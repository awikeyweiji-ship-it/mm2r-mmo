package com.jeyu.clashgptstarter;

import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;

import java.util.List;
import java.util.UUID;

/** Called only on a worker thread: Room deliberately prohibits main thread reads. */
public final class R7VaultRepository {
    private final Context context;
    private final R7VaultDao dao;
    private final R7FileVault files;
    public R7VaultRepository(Context c){context=c.getApplicationContext();dao=R7VaultDatabase.get(c).dao();files=new R7FileVault(c);}
    public R7FileVault files(){return files;}
    public R7ProjectEntity create(String title)throws Exception {
        if(title==null||title.trim().isEmpty()||title.length()>200)throw new IllegalArgumentException("Project title required");
        R7ProjectEntity p=new R7ProjectEntity();p.id=UUID.randomUUID().toString();
        p.encryptedTitle=files.encryptText(title.trim());p.createdAt=p.updatedAt=System.currentTimeMillis();
        p.remoteRevision=0;p.localDirty=true;dao.addProject(p);return p;
    }
    public R7ProjectEntity restoreProject(String requestedId,String title)throws Exception{
        if(dao.project(requestedId)!=null)return create(title+"（恢复副本）");
        if(!requestedId.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid UUID");
        R7ProjectEntity p=new R7ProjectEntity();p.id=requestedId;
        p.encryptedTitle=files.encryptText(title);p.createdAt=p.updatedAt=System.currentTimeMillis();
        p.localDirty=true;p.remoteRevision=0;dao.addProject(p);return p;
    }
    public void restoreSource(String projectId,String filename,String mime,String digest,long size)throws Exception{
        if(dao.findSource(projectId,digest)!=null)return;
        files.verify(projectId,digest,size);
        R7SourceEntity s=new R7SourceEntity();s.id=UUID.randomUUID().toString();s.projectId=projectId;
        s.sha256=digest;s.encryptedFilename=files.encryptText(filename);s.mime=mime;
        s.sizeBytes=size;s.importedAt=System.currentTimeMillis();dao.addSource(s);
    }
    public String projectTitle(R7ProjectEntity p){try{return files.decryptText(p.encryptedTitle);}catch(Exception e){return "[加密密钥不可用]";}}
    public String sourceName(R7SourceEntity s){try{return files.decryptText(s.encryptedFilename);}catch(Exception e){return "[无法解密文件名]";}}
    public List<R7ProjectEntity> projects(){return dao.projects();}
    public R7ProjectEntity project(String id){return dao.project(id);}
    public List<R7SourceEntity> sources(String id){return dao.sources(id);}
    public R7SourceEntity importUri(String projectId,Uri uri)throws Exception {
        R7ProjectEntity p=dao.project(projectId);
        if(p==null)throw new IllegalArgumentException("Project not found");
        String name="document";String type=context.getContentResolver().getType(uri);
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst() && !c.isNull(0))name=c.getString(0);
        }
        R7FileVault.Imported imported=files.importUri(projectId,uri);
        R7SourceEntity existing=dao.findSource(projectId,imported.sha256);
        if(existing!=null){files.verify(projectId,existing.sha256,existing.sizeBytes);return existing;}
        R7SourceEntity s=new R7SourceEntity();s.id=UUID.randomUUID().toString();s.projectId=projectId;
        s.sha256=imported.sha256;s.encryptedFilename=files.encryptText(name);
        s.mime=type==null?"application/octet-stream":type;s.sizeBytes=imported.size;
        s.importedAt=System.currentTimeMillis();s.sharedWithAgent=false;
        dao.addSource(s);p.updatedAt=System.currentTimeMillis();p.localDirty=true;dao.updateProject(p);
        return s;
    }
    public void verifyAll(String projectId)throws Exception {
        for(R7SourceEntity src:dao.sources(projectId))files.verify(projectId,src.sha256,src.sizeBytes);
    }
    public R7TaskEntity createLocalTask(String projectId,String goal)throws Exception{
        if(goal==null||goal.trim().isEmpty()||goal.length()>8192)throw new IllegalArgumentException("目标不能为空或过长");
        if(dao.project(projectId)==null)throw new IllegalArgumentException("项目不存在");
        R7TaskEntity t=new R7TaskEntity();t.id=UUID.randomUUID().toString();t.projectId=projectId;
        t.encryptedGoal=files.encryptText(goal.trim());t.updatedAt=System.currentTimeMillis();dao.putTask(t);return t;
    }
    public List<R7TaskEntity> tasks(String projectId){return dao.tasks(projectId);}
    public String taskGoal(R7TaskEntity t){try{return files.decryptText(t.encryptedGoal);}catch(Exception e){return "[目标解密失败]";}}
    public void markTaskSent(R7TaskEntity t,String serverId,String status,String checkpoint){
        t.remoteTaskId=serverId;t.status=status;t.remoteCheckpointId=checkpoint;t.updatedAt=System.currentTimeMillis();dao.updateTask(t);
    }
    public void markUploaded(String id,String digest,String remoteId){
        R7SourceEntity s=dao.findSource(id,digest);
        if(s!=null){s.sharedWithAgent=true;s.remoteSourceId=remoteId;dao.updateSource(s);}
    }
    public void markProjectSynchronized(R7ProjectEntity p,int remoteRevision){
        p.remoteRevision=remoteRevision;p.localDirty=false;dao.updateProject(p);
    }
}
