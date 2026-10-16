package com.jeyu.clashgptstarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** User-approved private project sync. Never reads consumer AI login sessions. */
public final class R7SyncClient {
    private static final String PREFS="r7_sync_options";
    private final Context ctx;
    private final R7VaultRepository repo;
    public R7SyncClient(Context context,R7VaultRepository repository){ctx=context.getApplicationContext();repo=repository;}
    public static void configure(Context ctx,String url,String token)throws Exception{
        configure(ctx,url,token,"demo/no-llm",false);
    }
    public static void configure(Context ctx,String url,String token,String modelId,boolean remoteDataConsent)throws Exception{
        if(modelId==null||!modelId.matches("[A-Za-z0-9._:/-]{1,120}"))
            throw new IOException("模型 ID 不合法：请填写服务端已配置的准确模型 ID");
        if(url==null||!url.startsWith("https://"))throw new IOException("需要有效的 HTTPS 服务地址；HTTP 不允许同步私人资料");
        java.net.URI parsed=new java.net.URI(url);
        if(parsed.getHost()==null||parsed.getUserInfo()!=null||parsed.getQuery()!=null||parsed.getFragment()!=null)
            throw new IOException("服务地址不能包含用户名、查询或密钥");
        if(token==null||token.trim().length()<8)throw new IOException("访问令牌至少8位");
        String encrypted=new R7FileVault(ctx).encryptText(token.trim());
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
           .putString("origin",url.replaceAll("/+$", "")).putString("secret",encrypted)
           .putString("model_id",modelId.trim())
           .putBoolean("remote_data_consent",remoteDataConsent).apply();
    }
    public static String url(Context ctx){return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("origin", "");}
    public static String modelId(Context ctx){return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("model_id", "demo/no-llm");}
    public static boolean remoteDataConsent(Context ctx){return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("remote_data_consent",false);}
    private String token()throws Exception{
        String e=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("secret", "");
        if(e.isEmpty())throw new IOException("请先在同步设置中保存访问令牌");
        return new R7FileVault(ctx).decryptText(e);
    }
    private HttpsURLConnection connection(String route,String method)throws Exception{
        String origin=url(ctx);if(!origin.startsWith("https://"))throw new IOException("请先配置 HTTPS 服务地址");
        java.net.URL target=new java.net.URL(origin+route);
        HttpsURLConnection c=(HttpsURLConnection)target.openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(85000);
        c.setRequestProperty("Authorization","Bearer "+token());
        c.setRequestProperty("Accept","application/json");
        c.setUseCaches(false);c.setInstanceFollowRedirects(false); // no token sent to another domain
        if(method.equals("POST"))c.setDoOutput(true);
        return c;
    }
    private static JSONObject response(HttpsURLConnection c)throws Exception{
        int status=c.getResponseCode();
        try(InputStream in=(status>=400?c.getErrorStream():c.getInputStream())){
            StringBuilder sb=new StringBuilder();
            if(in!=null){try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String s;while((s=reader.readLine())!=null)sb.append(s);
            }}
            if(status==409)throw new IOException("同步冲突：服务器项目已改变。没有自动覆盖，请在 PC 端检查版本。" );
            if(status<200||status>=300)throw new IOException("Agent 服务返回 HTTP "+status+": "+sb.substring(0,Math.min(120,sb.length())));
            return new JSONObject(sb.toString());
        }finally{c.disconnect();}
    }
    private JSONObject json(String path,JSONObject payload)throws Exception{
        HttpsURLConnection c=connection(path,"POST");c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream out=c.getOutputStream()){out.write(bytes);}
        return response(c);
    }
    public int syncProject(R7ProjectEntity p)throws Exception{
        // No automatic upload; caller must first confirm which project fields leave the phone.
        JSONObject req=new JSONObject();req.put("project_id",p.id);req.put("title",repo.projectTitle(p));req.put("base_revision",p.remoteRevision);
        JSONObject result=json("/v1/sync/push",req);
        int revision=result.getInt("revision");repo.markProjectSynchronized(p,revision);return revision;
    }
    public String uploadEvidence(R7SourceEntity s)throws Exception{
        // doNotSendWithoutConsent: caller must display a confirmation dialog before calling.
        if(!s.projectId.matches("[0-9a-fA-F-]{36}")||!s.sha256.matches("[0-9a-f]{64}"))throw new IOException("来源 ID 不合法");
        repo.files().verify(s.projectId,s.sha256,s.sizeBytes);
        String route="/v1/projects/"+s.projectId+"/sources?filename="+
               URLEncoder.encode(repo.sourceName(s),"UTF-8")+"&mime="+URLEncoder.encode(s.mime,"UTF-8");
        HttpsURLConnection c=connection(route,"POST");
        c.setRequestProperty("Content-Type","application/octet-stream");
        c.setRequestProperty("X-Upload-Consent","yes");
        c.setRequestProperty("X-Content-SHA256",s.sha256);
        c.setFixedLengthStreamingMode(s.sizeBytes);
        try(InputStream in=repo.files().openDecrypted(s.projectId,s.sha256);OutputStream out=c.getOutputStream()){
            byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        JSONObject result=response(c);
        String id=result.getString("id");repo.markUploaded(s.projectId,s.sha256,id);return id;
    }
    /** Read back actual Agent execution status. Never treat local stale status as remote completion. */
    public JSONObject fetchRemoteTask(String taskId)throws Exception{
        if(taskId==null||!taskId.matches("[a-fA-F0-9-]{36}"))throw new IOException("任务 ID 不合法");
        return response(connection("/v1/tasks/"+taskId,"GET"));
    }
    public int pullRemoteTaskStates(String projectId)throws Exception{
        int updated=0;
        for(R7TaskEntity task:repo.tasks(projectId)){
            if(task.remoteTaskId==null||task.remoteTaskId.isEmpty())continue;
            JSONObject current=fetchRemoteTask(task.remoteTaskId);
            if(!task.remoteTaskId.equals(current.optString("id"))||!projectId.equals(current.optString("project_id")))
                throw new IOException("服务端任务归属不匹配，拒绝覆盖本地状态");
            String status=current.optString("status", "UNKNOWN");
            if(!status.matches("[A-Z_]{3,40}"))throw new IOException("服务端任务状态无效");
            repo.markTaskSent(task,task.remoteTaskId,status,current.optString("checkpoint_id",null));
            updated++;
        }
        return updated;
    }

    /** R8: create a strict durable research run with frozen rules and no model downgrade. */
    public String createStrictResearchTask(R7TaskEntity t, java.util.List<R7SourceEntity> sources, String rules, String ruleVersion)throws Exception{
        JSONArray ids=new JSONArray();
        for(R7SourceEntity s:sources){
            if(s.remoteSourceId==null||s.remoteSourceId.isEmpty())throw new IOException("存在尚未授权上传的原始资料");
            repo.files().verify(s.projectId,s.sha256,s.sizeBytes);ids.put(s.remoteSourceId);
        }
        JSONObject policy=new JSONObject();
        policy.put("mode","STRICT");
        policy.put("model",modelId(ctx));
        policy.put("allow_model_fallback",false);
        policy.put("allow_provider_failover_same_model",true);
        policy.put("remote_data_consent",remoteDataConsent(ctx));
        JSONObject payload=new JSONObject();
        payload.put("project_id",t.projectId);
        payload.put("goal",repo.taskGoal(t));
        payload.put("source_ids",ids);
        payload.put("rules",rules==null?"":rules);
        payload.put("rule_version",ruleVersion==null?"":ruleVersion);
        payload.put("model_policy",policy);
        payload.put("idempotency_key",t.id);
        JSONObject result=json("/v1/tasks",payload);
        repo.markTaskSent(t,result.getString("id"),result.getString("status"),result.optString("checkpoint_id",null));
        return result.getString("id");
    }

    public JSONObject fetchResearchSnapshot(String remoteTaskId)throws Exception{
        return fetchRemoteTask(remoteTaskId);
    }

    public JSONArray fetchResearchEvents(String remoteTaskId,int afterSeq)throws Exception{
        if(remoteTaskId==null||!remoteTaskId.matches("[a-fA-F0-9-]{36}"))throw new IOException("任务 ID 不合法");
        return getJson("/v1/tasks/"+remoteTaskId+"/events?after_seq="+Math.max(0,afterSeq)).optJSONArray("events");
    }

    public JSONObject resumeResearchTask(String remoteTaskId,String checkpointId,int lastEventSeq)throws Exception{
        if(checkpointId==null||checkpointId.trim().isEmpty())throw new IOException("缺少可恢复检查点");
        JSONObject body=new JSONObject();body.put("checkpoint_id",checkpointId);body.put("last_event_seq",Math.max(0,lastEventSeq));
        return json("/v1/tasks/"+remoteTaskId+"/resume",body);
    }

    public JSONObject validateResearchTask(String remoteTaskId)throws Exception{
        return json("/v1/tasks/"+remoteTaskId+"/validate",new JSONObject());
    }

    private JSONObject getJson(String path)throws Exception{
        HttpsURLConnection c=connection(path,"GET");return response(c);
    }

    public String sendTask(R7TaskEntity t,java.util.List<R7SourceEntity> sources)throws Exception{
        JSONArray ids=new JSONArray();for(R7SourceEntity s:sources){if(s.remoteSourceId!=null&&!s.remoteSourceId.isEmpty())ids.put(s.remoteSourceId);}
        JSONObject policy=new JSONObject();policy.put("mode","STRICT");
        policy.put("model",modelId(ctx));
        // No external provider receives the source unless the user explicitly opted in.
        policy.put("remote_data_consent",remoteDataConsent(ctx));
        JSONObject payload=new JSONObject();payload.put("project_id",t.projectId);payload.put("goal",repo.taskGoal(t));payload.put("source_ids",ids);
        payload.put("idempotency_key",t.id);payload.put("model_policy",policy);
        JSONObject result=json("/v1/tasks",payload);
        repo.markTaskSent(t,result.getString("id"),result.getString("status"),result.optString("checkpoint_id",null));
        return result.getString("id");
    }
}
