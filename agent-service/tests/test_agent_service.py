import os,sys,tempfile,time,json
from pathlib import Path

ROOT=tempfile.mkdtemp(prefix="r8-agent-test-")
os.environ["AGENT_DATA_DIR"]=ROOT
os.environ["AGENT_ACCESS_TOKEN"]="test-token"
os.environ["AGENT_ALLOW_INSECURE_LOCAL"]="false"
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))

from app import store
from app.context import Unit,compile_context
from app.validator import validate_output
from fastapi.testclient import TestClient
from app.main import app

def source(pid,text,name="a.txt"):
    raw=Path(ROOT)/("raw-"+str(time.time_ns()));raw.write_text(text,encoding="utf-8")
    digest,size=store.file_sha(raw)
    return store.add_source(pid,name,"text/plain",digest,size,raw,raw)

def test_task_sources_are_frozen():
    p=store.create_project("P")
    s1=source(p["id"],"alpha")
    s2=source(p["id"],"beta")
    t=store.create_task(p["id"],"goal",[s1["id"]],"{}","1","model-x",{"mode":"STRICT"},"task-1")
    assert [x["id"] for x in store.sources_for_task(t["id"])]==[s1["id"]]

def test_duplicate_task_is_idempotent():
    p=store.create_project("P")
    s=source(p["id"],"alpha")
    a=store.create_task(p["id"],"goal",[s["id"]],"{}","1","model-x",{"mode":"STRICT"},"same")
    b=store.create_task(p["id"],"goal",[s["id"]],"{}","1","model-x",{"mode":"STRICT"},"same")
    assert a["id"]==b["id"]
    assert len(store.events(a["id"]))==1

def test_lease_is_reclaimable_after_expiry():
    p=store.create_project("P");s=source(p["id"],"alpha")
    store.create_task(p["id"],"goal",[s["id"]],"{}","1","model-x",{"mode":"STRICT"},"lease")
    first=store.claim_task("w1",lease_seconds=0.01);assert first
    time.sleep(0.03)
    second=store.claim_task("w2",lease_seconds=1);assert second and second["id"]==first["id"]

def test_context_only_carries_verified_memory():
    task={"goal":"alpha","rules":"{}"}
    unit=Unit(0,"s","h","p:0","alpha beta")
    memories=[
      {"kind":"FACT","trust":"VERIFIED","value_json":"alpha verified","source_ref":"s:1","updated_at":2},
      {"kind":"FACT","trust":"UNVERIFIED","value_json":"secret drift","source_ref":"s:2","updated_at":3}
    ]
    prompt,_,_=compile_context(task,unit,[m for m in memories if m["trust"]=="VERIFIED"],500)
    assert "alpha verified" in prompt and "secret drift" not in prompt

def test_validator_blocks_fake_quote():
    p=store.create_project("P");s=source(p["id"],"真实内容")
    task=store.create_task(p["id"],"goal",[s["id"]],"{}","1","model-x",{"mode":"STRICT"},"val")
    payload={"claims":[{"text":"bad","source_refs":[{"source_id":s["id"],"locator":"p:0","quote":"不存在"}]}]}
    errors,ev,mem,rule,reason=validate_output(task,[s],payload)
    assert "CLAIM_0_QUOTE_NOT_FOUND" in errors and not mem and rule=="UNKNOWN"

def test_rule_failure_is_not_pass():
    p=store.create_project("P");s=source(p["id"],"score=0.69")
    rules=json.dumps({"required_evidence":True,"numeric_constraints":[{"path":"claims.*.score","op":">=","value":0.8}]})
    task=store.create_task(p["id"],"goal",[s["id"]],rules,"1","model-x",{"mode":"STRICT"},"rule")
    payload={"claims":[{"score":0.69,"text":"score 0.69","source_refs":[{"source_id":s["id"],"locator":"p:0","quote":"score=0.69"}]}]}
    errors,_,_,state,_=validate_output(task,[s],payload)
    assert state=="FAIL" and "RULE_NUMERIC_CONSTRAINT" in errors

def test_api_rejects_checkpoint_mismatch():
    client=TestClient(app)
    p=store.create_project("P");s=source(p["id"],"alpha")
    t=store.create_task(p["id"],"goal",[s["id"]],"{}","1","model-x",{"mode":"STRICT"},"api")
    r=client.post("/v1/tasks/"+t["id"]+"/resume",headers={"Authorization":"Bearer test-token"},json={"checkpoint_id":"wrong","last_event_seq":0})
    assert r.status_code==409

def test_api_requires_auth():
    client=TestClient(app)
    r=client.get("/v1/tasks/not-found")
    assert r.status_code==401
