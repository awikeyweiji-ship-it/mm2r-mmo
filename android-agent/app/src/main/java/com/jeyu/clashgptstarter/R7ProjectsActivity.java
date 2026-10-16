package com.jeyu.clashgptstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** R7-A offline-first project vault. Does not control or impersonate external AI apps. */
public final class R7ProjectsActivity extends Activity {
    private static final int PICK_FILE=7101,EXPORT_BACKUP=7102,IMPORT_BACKUP=7103;
    private static final int BG=0xff07101C,INK=0xffE7F8FF,MUTED=0xff86A3B5,MINT=0xff67E6D7;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private R7VaultRepository repository;
    private String selectedId,exportPassword;
    private TextView selectedTitle,details,notice;
    private LinearLayout projectsList,sourcesList,tasksList;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        repository=new R7VaultRepository(this);
        selectedId=getSharedPreferences("r7_screen",MODE_PRIVATE).getString("selected_project",null);
        renderSkeleton();refresh();
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));t.setLineSpacing(dp(3),1f);return t;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams space(int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);lp.bottomMargin=dp(bottom);return lp;}
    private Button action(String title,boolean primary,Runnable run){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(primary?0xff05202B:INK);b.setBackground(shape(primary?0xff6CDEDA:0xff132B43,primary?0xff84FFF0:0xff355B72,13));
        b.setMinHeight(dp(52));b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->run.run());return b;}
    private LinearLayout section(String eyebrow,String title){LinearLayout card=col();card.setPadding(dp(16),dp(17),dp(16),dp(16));
        card.setBackground(shape(0xff101F31,0xff234154,18));
        TextView k=text(eyebrow,10,0xff6BC6CD,true);k.setLetterSpacing(.13f);card.addView(k);
        card.addView(text(title,19,INK,true),space(7,12));return card;}
    private void renderSkeleton(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        root.addView(new TechBackdropView(this),new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=col();content.setPadding(dp(18),dp(20),dp(18),dp(32));scroll.addView(content);
        if(Build.VERSION.SDK_INT>=30){scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            content.setPadding(dp(18)+bars.left,dp(20)+bars.top,dp(18)+bars.right,dp(32)+bars.bottom);return insets;
        });}
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        TextView back=text("‹  返回 AI 启动首页",14,MINT,true);back.setGravity(Gravity.CENTER_VERTICAL);back.setMinHeight(dp(46));back.setOnClickListener(v->finish());content.addView(back);
        TextView eyebrow=text("RESEARCH OS  /  LOCAL ARCHIVE",11,0xff6DE5E8,true);eyebrow.setLetterSpacing(.14f);content.addView(eyebrow,space(11,4));
        content.addView(text("我的 AI 项目",30,INK,true),space(6,7));
        content.addView(text("设备本地持久存储 · Room / SQLite · AES-GCM 加密 · 原文哈希校验",12,MUTED,false),space(0,18));
        notice=text("本机资料默认不上传。远程运行需你明确同意。",12,0xffA0D1D2,false);
        notice.setPadding(dp(14),dp(12),dp(14),dp(12));notice.setBackground(shape(0xff102E37,0xff28646A,10));content.addView(notice,space(0,14));

        LinearLayout projectCard=section("01  /  PROJECTS","设备本地项目仓");
        projectCard.addView(action("＋ 创建离线项目",true,this::createProjectDialog),space(1,8));
        projectsList=col();projectCard.addView(projectsList);
        content.addView(projectCard,space(0,14));
        LinearLayout selectedCard=section("02  /  WORKSPACE","当前项目");
        selectedTitle=text("未选择项目",18,INK,true);selectedCard.addView(selectedTitle);
        details=text("项目与资料持续保存在本机。",11,MUTED,false);selectedCard.addView(details,space(6,13));
        selectedCard.addView(action("＋ 导入原始文件（加密保存）",true,this::chooseDocument),space(0,7));
        selectedCard.addView(action("＋ 创建本地任务",false,this::createLocalTask),space(0,7));
        selectedCard.addView(action("重新核验全部原始文件",false,this::verifyAll),space(0,13));
        sourcesList=col();selectedCard.addView(text("已保存的证据",12,0xffB4D2E3,true),space(0,6));selectedCard.addView(sourcesList);
        tasksList=col();selectedCard.addView(text("本地任务（不依赖聊天窗口）",12,0xffB4D2E3,true),space(16,6));selectedCard.addView(tasksList);
        content.addView(selectedCard,space(0,14));

        LinearLayout backupCard=section("03  /  PORTABLE BACKUP","独立加密备份与恢复");
        backupCard.addView(text("可用 12 位以上恢复密码备份项目及原始文件。密码不储存在手机，也不上传服务器。",12,MUTED,false),space(0,9));
        backupCard.addView(action("导出加密备份文件",false,this::startBackup),space(0,6));
        backupCard.addView(action("从加密备份恢复",false,this::startRestore),space(0,1));
        content.addView(backupCard,space(0,14));

        LinearLayout remoteCard=section("04  /  AUTHORIZED SYNC","连接自己的 Agent 服务");
        remoteCard.addView(text("首次设置 HTTPS 地址与访问令牌。上传资料必须单独确认；本地记录不会自动外发。",12,MUTED,false),space(0,8));
        remoteCard.addView(action("配置服务地址与令牌",false,this::configureRemote),space(0,7));
        remoteCard.addView(action("同步项目与待运行任务",false,this::syncMetadata),space(0,7));
        remoteCard.addView(action("更新云端任务状态",false,this::refreshRemoteTasks),space(0,7));
        remoteCard.addView(action("授权上传一份原始资料",false,this::chooseUpload),space(0,7));
        remoteCard.addView(action("在浏览器打开 PC 工作台",false,this::openWeb),space(0,1));
        content.addView(remoteCard,space(0,14));
        content.addView(text("本地轻量模型：本阶段尚未安装。后续按 8GB / 16GB 机型选择可验证 GGUF，默认不开启。",11,0xff60859D,false),space(5,10));
    }
    private void status(String s,boolean error){runOnUiThread(()->{notice.setText(s);notice.setTextColor(error?0xffffadb3:0xff91DFD1);notice.setBackground(shape(error?0xff38202B:0xff102E37,error?0xff8F465A:0xff28646A,10));});}
    private void async(Action action){executor.execute(()->{try{action.run();refresh();}catch(Exception e){status("操作未完成："+e.getMessage(),true);}});}
    private interface Action{void run()throws Exception;}
    private void refresh(){executor.execute(()->{
        final List<R7ProjectEntity> projects=repository.projects();
        R7ProjectEntity p=selectedId==null?null:repository.project(selectedId);
        if(p==null&&!projects.isEmpty()){p=projects.get(0);selectedId=p.id;}
        final R7ProjectEntity selected=p;
        final List<R7SourceEntity> sources=selected==null?new ArrayList<>():repository.sources(selected.id);
        final List<R7TaskEntity> tasks=selected==null?new ArrayList<>():repository.tasks(selected.id);
        runOnUiThread(()->{
            projectsList.removeAllViews();
            if(projects.isEmpty())projectsList.addView(text("尚无项目，点击上方按钮创建。",12,MUTED,false));
            for(R7ProjectEntity proj:projects){
                TextView item=text((selected!=null&&selected.id.equals(proj.id)?"◉  ":"○  ")+repository.projectTitle(proj),14,selected!=null&&selected.id.equals(proj.id)?MINT:INK,false);
                item.setMinHeight(dp(45));item.setGravity(Gravity.CENTER_VERTICAL);
                item.setOnClickListener(v->{selectedId=proj.id;getSharedPreferences("r7_screen",MODE_PRIVATE).edit().putString("selected_project",selectedId).apply();refresh();});
                projectsList.addView(item);
            }
            selectedTitle.setText(selected==null?"未选择项目":repository.projectTitle(selected));
            details.setText(selected==null?"请先新建项目":("项目 ID: "+selected.id.substring(0,8)+" · "+sources.size()+" 份原始文件 · "+tasks.size()+" 个任务"));
            sourcesList.removeAllViews();
            if(sources.isEmpty())sourcesList.addView(text("暂无资料",12,MUTED,false));
            for(R7SourceEntity src:sources){
                TextView item=text("✓ "+repository.sourceName(src)+"  /  "+(src.sizeBytes/1024)+" KB\nSHA  "+src.sha256.substring(0,18)+"…"+
                    (src.sharedWithAgent?"  · 云端已上传":" · 仅在本机"),11,0xffABD7DB,false);
                item.setPadding(dp(8),dp(9),dp(8),dp(9));item.setBackground(shape(0xff0B1A2A,0xff203C52,8));sourcesList.addView(item,space(0,5));
            }
            tasksList.removeAllViews();
            if(tasks.isEmpty())tasksList.addView(text("暂无本地任务",12,MUTED,false));
            for(R7TaskEntity t:tasks){TextView item=text("· "+repository.taskGoal(t)+"\n"+t.status+(t.remoteTaskId!=null?" · 云端任务 "+t.remoteTaskId.substring(0,8):" · 未同步"),11,0xffBDD8E6,false);
                item.setPadding(dp(8),dp(8),dp(8),dp(8));tasksList.addView(item,space(0,5));}
        });
    });}
    private void createProjectDialog(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("例如：长期项目 A");
        new AlertDialog.Builder(this).setTitle("创建离线项目").setView(input)
        .setNegativeButton("取消",null).setPositiveButton("保存到本机",(d,w)->async(()->{
            R7ProjectEntity p=repository.create(input.getText().toString());selectedId=p.id;
            status("项目已持久保存到本机，重启 App 后仍可访问。",false);
        })).show();}
    private void chooseDocument(){if(selectedId==null){status("请先创建项目",true);return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
        startActivityForResult(intent,PICK_FILE);
    }
    private void createLocalTask(){if(selectedId==null){status("请先创建项目",true);return;}
        EditText box=new EditText(this);box.setMinLines(3);box.setHint("写下任务目标，原文离线加密保存");
        new AlertDialog.Builder(this).setTitle("创建持久任务").setView(box).setNegativeButton("取消",null)
         .setPositiveButton("保存",(d,w)->async(()->{
             repository.createLocalTask(selectedId,box.getText().toString());status("任务已本机保存；同步前不向模型发送。",false);
         })).show();
    }
    private void verifyAll(){if(selectedId==null){status("请先选择项目",true);return;}
        async(()->{repository.verifyAll(selectedId);status("SHA-256 / AES-GCM 重新校验通过（仅代表本机文件完整）。",false);});
    }
    private void askPassword(String title,PasswordAction action){EditText box=new EditText(this);
        box.setHint("不少于 12 位独立恢复密码");box.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("取消",null)
          .setPositiveButton("确认",(d,w)->{
              String secret=box.getText().toString();if(secret.length()<12){status("恢复密码至少12位",true);return;}action.accept(secret);
          }).show();}
    private interface PasswordAction{void accept(String password);}
    private void startBackup(){if(selectedId==null){status("先选择项目",true);return;}
        askPassword("导出独立加密备份",password->{exportPassword=password;
            Intent it=new Intent(Intent.ACTION_CREATE_DOCUMENT);it.addCategory(Intent.CATEGORY_OPENABLE);it.setType("application/octet-stream");
            it.putExtra(Intent.EXTRA_TITLE,"AI-Gateway-"+selectedId.substring(0,8)+".r7vault");startActivityForResult(it,EXPORT_BACKUP);
        });}
    private void startRestore(){Intent it=new Intent(Intent.ACTION_OPEN_DOCUMENT);it.addCategory(Intent.CATEGORY_OPENABLE);it.setType("*/*");startActivityForResult(it,IMPORT_BACKUP);}
    private void configureRemote(){
        LinearLayout fields=col();fields.setPadding(dp(18),0,dp(18),0);
        EditText address=new EditText(this);address.setSingleLine();address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("https://your-agent.example");address.setText(R7SyncClient.url(this));fields.addView(address);
        EditText secret=new EditText(this);secret.setSingleLine();secret.setHint("Bearer 访问令牌（至少8位）");
        secret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);fields.addView(secret);
        EditText remoteModel=new EditText(this);remoteModel.setSingleLine();
        remoteModel.setHint("模型精确 ID（默认只索引，不调用模型）");
        remoteModel.setText(R7SyncClient.modelId(this));fields.addView(remoteModel);
        CheckBox remoteConsent=new CheckBox(this);
        remoteConsent.setText("我明确同意运行任务时，将已授权上传的资料发送到配置的模型服务商");
        remoteConsent.setTextColor(MUTED);remoteConsent.setTextSize(12);
        remoteConsent.setChecked(R7SyncClient.remoteDataConsent(this));fields.addView(remoteConsent);
        new AlertDialog.Builder(this).setTitle("仅连接可信 HTTPS 服务").setView(fields).setNegativeButton("取消",null)
        .setPositiveButton("保存连接",(d,w)->{
            final String target=address.getText().toString(),key=secret.getText().toString();
            final String chosenModel=remoteModel.getText().toString();
            final boolean agreed=remoteConsent.isChecked();
            async(()->{
                R7SyncClient.configure(this,target,key,chosenModel,agreed);
                status("服务端和 STRICT 模型策略已保存；未逐份授权的文件不会上传。",false);
            });
        }).show();
    }
    private void syncMetadata(){if(selectedId==null){status("请先选项目",true);return;}
        new AlertDialog.Builder(this).setTitle("授权同步项目与任务？")
        .setMessage("将项目名称及待运行任务目标发给你配置的服务端。不会自动上传附件、登录密码或官方 AI 的聊天记录。是否继续？")
        .setNegativeButton("取消",null).setPositiveButton("我授权上传这些元数据",(d,w)->async(()->{
            R7ProjectEntity p=repository.project(selectedId);
            R7SyncClient remote=new R7SyncClient(this,repository);int revision=remote.syncProject(p);
            List<R7SourceEntity> sources=repository.sources(selectedId);
            for(R7TaskEntity t:repository.tasks(selectedId)){
                if(t.remoteTaskId!=null)continue;
                for(R7SourceEntity s:sources)if(!s.sharedWithAgent)
                    throw new IllegalStateException("存在未授权上传的来源文件，请逐份授权上传后再发送任务");
                remote.sendTask(t,sources);
            }
            status("已同步项目版本 "+revision+" 与可用任务；原始文件从不默认外发。",false);
        })).show();
    }
    private void refreshRemoteTasks(){if(selectedId==null){status("请先选择项目",true);return;}
        final String projectId=selectedId;
        async(()->{
            R7SyncClient sync=new R7SyncClient(this,repository);
            int updated=sync.pullRemoteTaskStates(projectId);
            status("已从 Agent 服务确认并更新 "+updated+" 个任务状态及检查点。",false);
        });
    }
    private void chooseUpload(){if(selectedId==null){status("请先选项目",true);return;}
        async(()->{
            List<R7SourceEntity> sources=repository.sources(selectedId);
            if(sources.isEmpty()){status("请先导入文件",true);return;}
            String[] entries=new String[sources.size()];for(int i=0;i<sources.size();i++)entries[i]=repository.sourceName(sources.get(i));
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("选择一份要上传的原始文件")
              .setItems(entries,(dialog,which)->{
                R7SourceEntity src=sources.get(which);
                new AlertDialog.Builder(this).setTitle("仅授权上传这一份资料？")
                  .setMessage("会将原始文档内容（解密后）发送到你填写的 HTTPS 服务器：\n"+R7SyncClient.url(this)+"\n\n文件："+entries[which]+"\n取消则不上传。")
                  .setNegativeButton("取消",null).setPositiveButton("确认上传",(d,w)->async(()->{
                    R7ProjectEntity p=repository.project(selectedId);
                    R7SyncClient remote=new R7SyncClient(this,repository);
                    if(p.remoteRevision==0)remote.syncProject(p);
                    remote.uploadEvidence(src);status("已上传并校验："+repository.sourceName(src),false);
                  })).show();
              }).setNegativeButton("取消",null).show());
        });
    }
    private void openWeb(){String url=R7SyncClient.url(this);if(!url.startsWith("https://")){status("请先配置 HTTPS 服务地址",true);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){status("没有可打开的浏览器",true);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT_BACKUP)exportPassword=null;return;}
        final Uri uri=data.getData();
        if(request==PICK_FILE){final String id=selectedId;async(()->{
            R7SourceEntity s=repository.importUri(id,uri);status("文件已 AES-GCM 加密并本机存档："+repository.sourceName(s),false);
        });}
        else if(request==EXPORT_BACKUP){String password=exportPassword;exportPassword=null;final String id=selectedId;
            async(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IllegalStateException("无法写入备份文件");
                new R7BackupArchive(this,repository).exportEncrypted(id,password,out);
                status("加密备份已导出。请将恢复密码与备份文件分别保管。",false);
            }});
        }else if(request==IMPORT_BACKUP){askPassword("输入备份恢复密码",password->async(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalStateException("无法打开备份文件");
                R7ProjectEntity p=new R7BackupArchive(this,repository).restoreEncrypted(password,in);selectedId=p.id;
                status("备份已完成解密、GCM 验证与全部来源哈希校验。",false);
            }
        }));}
    }
    @Override protected void onDestroy(){executor.shutdown();super.onDestroy();}
}
