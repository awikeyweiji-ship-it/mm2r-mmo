import hashlib
import json
import os
import time
import uuid
from pathlib import Path

from . import store
from .context import build_units, compile_context, request_hash
from .model_gateway import ModelError, call_model, parse_json
from .validator import validate_output, final_gate

SYSTEM_PROMPT = (
    "You are the analysis engine inside a durable research system. "
    "Never invent sources. Never return step numbers, source IDs, hashes, validation states, "
    "or execution metadata except inside source_refs supplied from the current context. "
    "Return JSON with claims, memory-ready facts, and source_refs. "
    "A claim must cite exact text from the supplied current source. "
    "If evidence is insufficient, return no claim rather than guessing."
)

def _errors(task):
    try:return json.loads(task["error_codes"] or "[]")
    except Exception:return []

def _set_errors(tid,codes):
    store.set_state(tid,error_codes=sorted(set(codes)))

def _result_path(task_id,kind):
    p=store.RESULTS/task_id
    p.mkdir(parents=True,exist_ok=True)
    return p/(kind+".json")

def _write_result(task_id,kind,payload):
    p=_result_path(task_id,kind)
    tmp=p.with_suffix(".partial")
    tmp.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8")
    tmp.replace(p)
    return str(p)

def _verify_sources(source_rows):
    for s in source_rows:
        digest,size=store.file_sha(s["object_path"])
        if digest!=s["sha256"] or size!=int(s["size_bytes"]):
            raise RuntimeError("DATA_SOURCE_INTEGRITY_FAILURE:" + s["id"])

def _checkpoint(task,reason):
    cid=str(uuid.uuid4())
    store.mark_checkpoint(task["id"],int(task["context_epoch"]),int(task["current_unit"]),cid,reason)
    return cid

def _prompt_for(task,unit,memories,limit):
    prompt,estimate,_=compile_context(task,unit,memories,limit)
    return prompt,estimate

def _load_completed_call(call):
    if call["status"]!="COMPLETED" or not call.get("response_path"):
        return None
    p=Path(call["response_path"])
    if not p.exists():return None
    return p.read_text(encoding="utf-8")

def _execute_model(task,unit,epoch,prompt):
    rh=request_hash(prompt,task["requested_model"])
    call=store.model_call(task["id"],unit,epoch,task["requested_model"],rh)
    if call["status"]=="COMPLETED":
        cached=_load_completed_call(call)
        if cached is not None:return parse_json(cached),call["actual_model"],{"cached":True}
    if call["status"]=="IN_FLIGHT" and not call.get("_new"):
        raise ModelError("EXTERNAL_CALL_UNCERTAIN","已有未确认的模型调用；不会自动重复调用。",True)
    try:
        raw,actual,usage=call_model(task["requested_model"],[
            {"role":"system","content":SYSTEM_PROMPT},
            {"role":"user","content":prompt}
        ])
        try: payload=parse_json(raw)
        except Exception as exc:
            store.finish_model_call(call["id"],"ERROR",actual_model=actual,error_code="MODEL_OUTPUT_NOT_JSON")
            raise ModelError("MODEL_OUTPUT_NOT_JSON","模型没有返回严格 JSON") from exc
        path=_write_result(task["id"],"model-call-"+str(unit)+"-"+str(epoch),payload)
        store.finish_model_call(call["id"],"COMPLETED",actual_model=actual,response_path=path)
        return payload,actual,usage
    except ModelError as exc:
        if not exc.uncertain:
            store.finish_model_call(call["id"],"ERROR",error_code=exc.code)
        raise
    except Exception as exc:
        store.finish_model_call(call["id"],"ERROR",error_code="MODEL_CALL_EXCEPTION")
        raise ModelError("MODEL_CALL_EXCEPTION",str(exc)) from exc

def _process_unit(task,unit,worker_id):
    if not store.renew(task["id"],worker_id):raise RuntimeError("LEASE_LOST")
    context_limit=max(1000,int(os.getenv("MODEL_CONTEXT_TOKENS","32768")))
    safe_budget=max(1000,int(context_limit*0.65))
    memories=store.trusted_memories(task["id"])
    prompt,estimate=_prompt_for(task,unit,memories,safe_budget)
    epoch=int(task["context_epoch"])
    if estimate>safe_budget:
        epoch+=1
        store.mark_checkpoint(task["id"],epoch,int(unit.index),str(uuid.uuid4()),"ROLLED")
        memories=store.trusted_memories(task["id"])
        prompt,estimate=_prompt_for(task,unit,memories,safe_budget)
    if estimate>safe_budget:
        raise ModelError("CTX_OVER_BUDGET","当前最小必要上下文仍超过安全预算。",False)
    payload,actual,usage=_execute_model(task,unit.index,epoch,prompt)
    source_rows=store.sources_for_task(task["id"])
    errors,evidence,memories_out,rule_state,reasoning=validate_output(task,source_rows,payload)
    if errors:
        codes=_errors(task)+errors
        _set_errors(task["id"],codes)
        store.set_state(task["id"],status="NEEDS_REVIEW",rule_state=rule_state,
                        reasoning_state=reasoning,context_state="SAFE")
        store.append_event(task["id"],"UNIT_BLOCKED",{"unit":unit.index,"errors":errors,"requested_model":task["requested_model"]})
        return False
    for ev in evidence:store.save_evidence(task["id"],ev)
    for mem in memories_out:store.save_memory(task["id"],mem)
    draft_payload={"unit":unit.index,"source_id":unit.source_id,"locator":unit.locator,"claims":payload.get("claims",[])}
    _write_result(task["id"],"draft-latest",draft_payload)
    store.set_state(task["id"],current_unit=unit.index+1,total_units=task["total_units"],
                   context_epoch=epoch,context_state="ROLLED" if epoch!=int(task["context_epoch"]) else "SAFE",
                   rule_state=rule_state if task["rules"].strip() else "UNKNOWN",
                   reasoning_state=reasoning,status="CHECKPOINTED",checkpoint_id=str(uuid.uuid4()))
    store.append_event(task["id"],"UNIT_VERIFIED",{"unit":unit.index,"source_id":unit.source_id,
                                                   "context_epoch":epoch,"requested_model":task["requested_model"],
                                                   "usage":usage if isinstance(usage,dict) else {}})
    return True

def _finalize(task,worker_id):
    if not task["rules"].strip():
        store.set_state(task["id"],status="NEEDS_REVIEW",rule_state="UNKNOWN")
        store.append_event(task["id"],"PUBLISH_BLOCKED","rule set is empty; no validated report is publishable")
        return
    ok,reason=final_gate(task)
    if not ok:
        store.set_state(task["id"],status="NEEDS_REVIEW")
        _set_errors(task["id"],_errors(task)+[reason])
        store.append_event(task["id"],"FINAL_GATE_BLOCKED",{"reason":reason})
        return
    memories=store.trusted_memories(task["id"])
    evidence=store.evidence(task["id"])
    context_limit=max(1000,int(os.getenv("MODEL_CONTEXT_TOKENS","32768")))
    budget=max(1000,int(context_limit*0.65))
    evidence_text="\n".join("- "+e["source_sha256"]+" "+e["locator"]+" :: "+e["quote"] for e in evidence[:32])
    memory_text="\n".join("- "+m["kind"]+" :: "+m["value_json"]+" ("+m["source_ref"]+")" for m in memories[:32])
    prompt=("TASK GOAL:\n"+task["goal"]+"\n\nRULES:\n"+task["rules"]+
            "\n\nONLY VERIFIED MEMORY:\n"+memory_text+"\n\nVERIFIED EVIDENCE:\n"+evidence_text+
            "\n\nReturn JSON only. Every claim must cite exact evidence.")
    if len(prompt)//4>budget:
        raise ModelError("CTX_FINAL_OVER_BUDGET","最终综合上下文超过安全预算。",False)
    payload,actual,usage=_execute_model(task,-1,int(task["context_epoch"]),prompt)
    source_rows=store.sources_for_task(task["id"])
    errors,evidence2,memories2,rule_state,reasoning=validate_output(task,source_rows,payload)
    if errors or rule_state!="PASS":
        _set_errors(task["id"],_errors(task)+errors+["FINAL_OUTPUT_NOT_VERIFIED"])
        store.set_state(task["id"],status="NEEDS_REVIEW",rule_state="FAIL" if errors else rule_state,reasoning_state=reasoning)
        store.append_event(task["id"],"FINAL_OUTPUT_BLOCKED",{"errors":errors})
        return
    for ev in evidence2:store.save_evidence(task["id"],ev)
    for mem in memories2:store.save_memory(task["id"],mem)
    report={"status":"VERIFIED","model":actual,"claims":payload.get("claims",[]),"evidence":evidence2,
            "rule_state":"PASS","reasoning_state":reasoning,"quality_state":"NOT_EVALUATED"}
    rp=_write_result(task["id"],"verified-report",report)
    store.set_state(task["id"],status="READY",rule_state="PASS",reasoning_state=reasoning,
                    quality_state="NOT_EVALUATED",verified_report_path=rp,checkpoint_id=str(uuid.uuid4()))
    store.append_event(task["id"],"PUBLISHED_VERIFIED_REPORT",{"requested_model":task["requested_model"],"actual_model":actual})

class DurableWorker:
    def __init__(self,worker_id=None):
        self.worker_id=worker_id or ("worker-"+str(uuid.uuid4())[:8])
        self.poll=float(os.getenv("AGENT_WORKER_POLL_SECONDS","2"))
    def run_once(self):
        task=store.claim_task(self.worker_id)
        if not task:return False
        try:
            source_rows=store.sources_for_task(task["id"])
            _verify_sources(source_rows)
            units=build_units(source_rows)
            if int(task["total_units"])==0:
                store.set_state(task["id"],total_units=len(units),data_state="PASS")
                task=store.task(task["id"])
            else:
                if int(task["total_units"])!=len(units):
                    store.set_state(task["id"],status="NEEDS_REVIEW",data_state="FAIL",error_codes=["SOURCE_SET_CHANGED"])
                    return True
            if int(task["current_unit"])<len(units):
                ok=_process_unit(task,units[int(task["current_unit"])],self.worker_id)
                if not ok:return True
                task=store.task(task["id"])
                if int(task["current_unit"])>=len(units):_finalize(task,self.worker_id)
            else:_finalize(task,self.worker_id)
        except ModelError as exc:
            codes=_errors(task)+[exc.code]
            _set_errors(task["id"],codes)
            state="PAUSED_MODEL" if exc.code.startswith("MODEL") or exc.uncertain else ("PAUSED_CONTEXT" if exc.code.startswith("CTX") else "NEEDS_REVIEW")
            store.set_state(task["id"],status=state)
            store.append_event(task["id"],"TASK_PAUSED",{"error_code":exc.code,"uncertain_external_call":exc.uncertain})
        except RuntimeError as exc:
            code=str(exc)
            _set_errors(task["id"],_errors(task)+[code])
            store.set_state(task["id"],status="NEEDS_REVIEW")
            store.append_event(task["id"],"TASK_FAILED",{"error_code":code})
        except Exception as exc:
            _set_errors(task["id"],_errors(task)+["WORKER_EXCEPTION"])
            store.set_state(task["id"],status="FAILED")
            store.append_event(task["id"],"TASK_FAILED",{"error_code":"WORKER_EXCEPTION","message":str(exc)[:500]})
        finally:
            store.release(task["id"],self.worker_id)
        return True
    def run_forever(self):
        while True:
            worked=self.run_once()
            if not worked:time.sleep(self.poll)
