package com.jeyu.clashgptstarter;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local durable control-plane repository. Remote data is accepted only after identity checks. */
public final class R8ResearchRepository {
    private final Context ctx;
    private final R7VaultRepository base;
    private final R7VaultDao dao;
    private final R8BlobVault blobs;
    public R8ResearchRepository(Context c){
        ctx=c.getApplicationContext();base=new R7VaultRepository(ctx);dao=R7VaultDatabase.get(ctx).dao();blobs=new R8BlobVault(ctx);
    }
    public R7VaultRepository base(){return base;}
    public R8ExecutionEntity ensureExecution(R7TaskEntity task,String modelId)throws Exception{
        R8ExecutionEntity e=dao.r8Execution(task.id);
        if(e!=null)return e;
        e=new R8ExecutionEntity();e.taskId=task.id;e.modelId=modelId==null?"":modelId;
        e.updatedAt=System.currentTimeMillis();dao.putR8Execution(e);return e;
    }
    public R8ExecutionEntity createTask(String projectId,String goal,String rules,String modelId)throws Exception{
        R7TaskEntity t=base.createLocalTask(projectId,goal);
        R8ExecutionEntity e=ensureExecution(t,modelId);
        e.encryptedRules=blobs.write(t.id,"rules",rules==null?"":rules);
        e.ruleVersion=rules==null||rules.trim().isEmpty()?"":("local-"+Integer.toHexString(rules.hashCode()));
        e.modelId=modelId==null?"":modelId;e.modelPolicy="STRICT";e.executionState="CREATED";
        e.remoteDataConsent=false;e.updatedAt=System.currentTimeMillis();dao.putR8Execution(e);return e;
    }
    public R8ExecutionEntity execution(String taskId){return dao.r8Execution(taskId);}
    public List<R8MemoryEntity> trustedMemory(String taskId){return dao.trustedMemories(taskId);}
    public List<R8EvidenceEntity> evidence(String taskId){return dao.r8Evidence(taskId);}
    public List<R7EventEntity> events(String taskId){return dao.events(taskId);}
    public String rules(R8ExecutionEntity e)throws Exception{return e==null||e.encryptedRules==null||e.encryptedRules.isEmpty()?"":blobs.read(e.encryptedRules);}
    public String snapshot(R8ExecutionEntity e)throws Exception{return e==null?"":blobs.read(e.snapshotBlobPath);}
    public String draft(R8ExecutionEntity e)throws Exception{return e==null?"":blobs.read(e.draftBlobPath);}
    public String verifiedReport(R8ExecutionEntity e)throws Exception{return e==null?"":blobs.read(e.verifiedReportBlobPath);}

    public void applyRemoteSnapshot(R7TaskEntity task,JSONObject json)throws Exception{
        if(!task.id.equals(task.id))throw new IllegalStateException("task mismatch");
        R8ExecutionEntity e=ensureExecution(task,json.optString("model_id",task.id));
        String remoteId=json.optString("id","");
        if(task.remoteTaskId!=null&&!task.remoteTaskId.isEmpty()&&!task.remoteTaskId.equals(remoteId))
            throw new SecurityException("REMOTE_TASK_ID_MISMATCH");
        e.executionState=json.optString("status","UNKNOWN");
        e.contextState=json.optString("context_state","UNKNOWN");
        e.dataState=json.optString("data_state","UNKNOWN");
        e.ruleState=json.optString("rule_state","UNKNOWN");
        e.reasoningState=json.optString("reasoning_state","NOT_EVALUATED");
        e.qualityState=json.optString("quality_state","NOT_EVALUATED");
        e.modelId=json.optString("requested_model",e.modelId);
        e.contextEpoch=json.optInt("context_epoch",e.contextEpoch);
        e.completedUnits=json.optInt("completed_units",e.completedUnits);
        e.totalUnits=json.optInt("total_units",e.totalUnits);
        e.lastEventSeq=json.optInt("last_event_seq",e.lastEventSeq);
        e.remoteCheckpointId=json.optString("checkpoint_id",e.remoteCheckpointId);
        e.remoteRunId=json.optString("run_id",e.remoteRunId);
        e.errorCodes=json.optString("error_codes",e.errorCodes);
        String snap=json.toString();
        if(snap.length()>180000)throw new SecurityException("REMOTE_SNAPSHOT_TOO_LARGE");
        e.snapshotBlobPath=blobs.write(task.id,"snapshot",snap);
        if(json.has("draft"))e.draftBlobPath=blobs.write(task.id,"draft",json.optString("draft",""));
        if(json.has("verified_report"))e.verifiedReportBlobPath=blobs.write(task.id,"verified-report",json.optString("verified_report",""));
        e.updatedAt=System.currentTimeMillis();dao.putR8Execution(e);
        applyMemories(task.id,json.optJSONArray("trusted_memory"));
        applyEvidence(task.id,json.optJSONArray("evidence"));
    }
    private void applyMemories(String taskId,JSONArray a)throws Exception{
        if(a==null)return;
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;
            String trust=x.optString("trust","UNVERIFIED");if(!"VERIFIED".equals(trust))continue;
            String value=x.optString("value","");
            if(value.length()>12000)continue;
            R8MemoryEntity m=new R8MemoryEntity();m.id=x.optString("id",UUID.randomUUID().toString());m.taskId=taskId;
            m.kind=x.optString("kind","FACT");m.trust=trust;m.sourceRef=x.optString("source_ref","");
            m.contentHash=x.optString("content_hash",Integer.toHexString(value.hashCode()));m.encryptedValue=base.files().encryptText(value);
            m.version=x.optInt("version",0);m.updatedAt=System.currentTimeMillis();dao.putR8Memory(m);
        }
    }
    private void applyEvidence(String taskId,JSONArray a)throws Exception{
        if(a==null)return;
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;
            R8EvidenceEntity ev=new R8EvidenceEntity();ev.id=x.optString("id",UUID.randomUUID().toString());ev.taskId=taskId;
            ev.sourceSha256=x.optString("source_sha256","");ev.locator=x.optString("locator","");
            ev.quoteHash=x.optString("quote_hash","");ev.encryptedQuote=base.files().encryptText(x.optString("quote",""));
            ev.verificationState=x.optString("verification_state","UNKNOWN");
            ev.evidenceGroupId=x.optString("evidence_group_id","");ev.independenceState=x.optString("independence_state","UNKNOWN");
            ev.updatedAt=System.currentTimeMillis();dao.putR8Evidence(ev);
        }
    }
    public void applyEvents(String taskId,JSONArray a)throws Exception{
        if(a==null)return;
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;
            R7EventEntity ev=new R7EventEntity();ev.id=x.optString("event_id",UUID.randomUUID().toString());
            ev.taskId=taskId;ev.sequence=x.optInt("seq",0);ev.type=x.optString("type","UNKNOWN");
            ev.encryptedPayload=base.files().encryptText(x.toString());ev.timestamp=System.currentTimeMillis();dao.addEvent(ev);
        }
    }
}
