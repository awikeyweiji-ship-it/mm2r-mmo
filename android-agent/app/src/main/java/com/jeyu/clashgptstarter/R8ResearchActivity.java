package com.jeyu.clashgptstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R8 native Android research console.
 *
 * This screen is deliberately separate from the official ChatGPT/Gemini/Claude launchers.
 * It owns local persistence and controls a user-authorized durable Agent service.
 */
public final class R8ResearchActivity extends Activity {
    private static final int BG=0xff07101C,INK=0xffE7F8FF,MUTED=0xff86A3B5,MINT=0xff67E6D7;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean refreshing=new AtomicBoolean(false);
    private R8ResearchRepository repo;
    private String projectId;
    private String selectedTaskId;
    private LinearLayout projectList,taskList;
    private TextView notice,projectTitle,projectMeta,taskTitle,taskMeta;
    private TextView executionState,contextState,dataState,ruleState,reasoningState,qualityState,evidenceState;
    private Runnable poller;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        repo=new R8ResearchRepository(this);
        projectId=getSharedPreferences("r8_screen",MODE_PRIVATE).getString("project_id",null);
        selectedTaskId=getSharedPreferences("r8_screen",MODE_PRIVATE).getString("task_id",null);
        buildUi();
        refresh();
    }

    @Override protected void onResume(){
        super.onResume();
        poller=()->{refreshRemote();main.postDelayed(poller,8000);};
        main.postDelayed(poller,1500);
    }

    @Override protected void onPause(){
        if(poller!=null)main.removeCallbacks(poller);
        super.onPause();
    }

    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        t.setLineSpacing(dp(3),1f);return t;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams space(int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;
    }
    private Button action(String title,boolean primary,Runnable run){
        Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(primary?0xff05202B:INK);
        b.setBackground(shape(primary?0xff6CDEDA:0xff132B43,primary?0xff84FFF0:0xff355B72,13));
        b.setMinHeight(dp(52));b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->run.run());return b;
    }
    private LinearLayout section(String eyebrow,String title){
        LinearLayout card=col();card.setPadding(dp(16),dp(16),dp(16),dp(15));card.setBackground(shape(0xff101F31,0xff234154,18));
        TextView k=text(eyebrow,10,0xff6BC6CD,true);k.setLetterSpacing(.13f);card.addView(k);
        card.addView(text(title,19,INK,true),space(7,10));return card;
    }

    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        root.addView(new TechBackdropView(this),new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=col();content.setPadding(dp(18),dp(18),dp(18),dp(30));scroll.addView(content);
        if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            content.setPadding(dp(18)+bars.left,dp(18)+bars.top,dp(18)+bars.right,dp(30)+bars.bottom);return insets;
        });
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));setContentView(root);

        TextView back=text("‹  返回 AI 启动首页",14,MINT,true);back.setMinHeight(dp(46));back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v->finish());content.addView(back);
        TextView eyebrow=text("R8  /  DURABLE RESEARCH CORE",11,0xff6DE5E8,true);eyebrow.setLetterSpacing(.14f);
        content.addView(eyebrow,space(10,4));
        content.addView(text("长期任务中枢",30,INK,true),space(5,4));
        content.addView(text("手机保存原始资料与研究状态 · 复杂 Agent 在持久服务中继续执行",12,MUTED,false),space(0,13));

        notice=text("本地优先。未授权不会上传原文；模型降级、上下文异常和规则失败不会混成一个状态。",12,0xffA0D1D2,false);
        notice.setPadding(dp(14),dp(12),dp(14),dp(12));notice.setBackground(shape(0xff102E37,0xff28646A,10));
        content.addView(notice,space(0,13));

        LinearLayout projectCard=section("01  /  PROJECT","选择研究项目");
        projectList=col();projectCard.addView(projectList);
        projectCard.addView(action("＋ 创建项目",false,()->startActivity(new Intent(this,R7ProjectsActivity.class))),space(8,0));
        content.addView(projectCard,space(0,12));

        LinearLayout current=section("02  /  TASK","当前长期任务");
        projectTitle=text("未选择项目",19,INK,true);current.addView(projectTitle);
        projectMeta=text("请先在项目仓导入资料。",11,MUTED,false);current.addView(projectMeta,space(5,11));
        current.addView(action("＋ 新建长期 Agent 任务",true,this::createTaskDialog),space(0,7));
        taskList=col();current.addView(taskList);
        content.addView(current,space(0,12));

        LinearLayout monitor=section("03  /  CONTROL PLANE","状态隔离");
        monitor.addView(text("下面五类状态互相独立；绿色不代表“模型永远正确”。",11,MUTED,false),space(0,10));
        executionState=statusRow(monitor,"执行 / 恢复");contextState=statusRow(monitor,"上下文完整性");
        dataState=statusRow(monitor,"资料完整性");ruleState=statusRow(monitor,"规则验证");
        reasoningState=statusRow(monitor,"推理质量");qualityState=statusRow(monitor,"模型基准");
        evidenceState=statusRow(monitor,"证据独立性");
        content.addView(monitor,space(0,12));

        LinearLayout detail=section("04  /  EVIDENCE","本机持久证据");
        taskTitle=text("暂无任务",17,INK,true);detail.addView(taskTitle);
        taskMeta=text("任务状态会从本地数据库恢复；网络恢复后再向服务端核对。",11,MUTED,false);
        detail.addView(taskMeta,space(5,10));
        detail.addView(action("查看研究草稿",false,()->showBlob(false)),space(0,6));
        detail.addView(action("查看已验证报告",false,()->showBlob(true)),space(0,6));
        detail.addView(action("查看事件链与证据",false,this::showEvidence),space(0,6));
        detail.addView(action("继续 / 恢复当前任务",true,this::resumeTask),space(0,6));
        detail.addView(action("强制重新向服务端核对",false,this::refreshRemote),space(0,0));
        content.addView(detail,space(0,12));

        LinearLayout tools=section("05  /  TOOLS","跨端与本地");
        tools.addView(action("打开 PC 工作台",false,()->openWeb()),space(0,6));
        tools.addView(action("打开完整项目仓 / 备份",false,()->startActivity(new Intent(this,R7ProjectsActivity.class))),space(0,6));
        tools.addView(text("本地轻量模型：保持可选，不参与最终验证。后续可接入 llama.cpp GGUF 引擎；当前版本不会偷偷把小模型当作主模型。",11,MUTED,false),space(5,0));
        content.addView(tools,space(0,8));
    }

    private TextView statusRow(LinearLayout parent,String title){
        LinearLayout r=row();r.setPadding(dp(9),dp(8),dp(9),dp(8));r.setBackground(shape(0xff0B1A2A,0xff203C52,9));
        TextView name=text(title,12,INK,false);r.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
        TextView value=text("UNKNOWN",12,0xffB8C7D1,true);r.addView(value);
        parent.addView(r,space(0,5));return value;
    }

    private void refresh(){
        executor.execute(()->{
            try{
                List<R7ProjectEntity> projects=repo.base().projects();
                R7ProjectEntity p=projectId==null?null:repo.base().project(projectId);
                if(p==null&&!projects.isEmpty()){p=projects.get(0);projectId=p.id;persistSelection();}
                final R7ProjectEntity selected=p;
                final List<R7TaskEntity> tasks=selected==null?new ArrayList<>():repo.base().tasks(selected.id);
                if(selectedTaskId!=null){
                    boolean exists=false;for(R7TaskEntity t:tasks)if(selectedTaskId.equals(t.id)){exists=true;break;}
                    if(!exists)selectedTaskId=null;
                }
                if(selectedTaskId==null&&!tasks.isEmpty())selectedTaskId=tasks.get(0).id;
                final R7TaskEntity task=findTask(tasks,selectedTaskId);
                final java.util.HashMap<String,R8ExecutionEntity> executions=new java.util.HashMap<>();
                final java.util.HashMap<String,String> goals=new java.util.HashMap<>();
                for(R7TaskEntity t:tasks){executions.put(t.id,repo.execution(t.id));goals.put(t.id,repo.base().taskGoal(t));}
                final int sourceCount=selected==null?0:repo.base().sources(selected.id).size();
                runOnUiThread(()->render(selected,sourceCount,tasks,task,executions,goals));
            }catch(Exception e){notice("本机读取失败："+safe(e.getMessage()),true);}
        });
    }

    private void render(R7ProjectEntity p,int sourceCount,List<R7TaskEntity> tasks,R7TaskEntity task,
                        java.util.Map<String,R8ExecutionEntity> executions,java.util.Map<String,String> goals){
        projectList.removeAllViews();
        List<R7ProjectEntity> ps=repo.base().projects();
        if(ps.isEmpty())projectList.addView(text("还没有项目。先进入项目仓创建并导入资料。",12,MUTED,false));
        for(R7ProjectEntity item:ps){
            TextView v=text((p!=null&&p.id.equals(item.id)?"◉  ":"○  ")+repo.base().projectTitle(item),13,p!=null&&p.id.equals(item.id)?MINT:INK,false);
            v.setMinHeight(dp(42));v.setGravity(Gravity.CENTER_VERTICAL);v.setOnClickListener(x->{projectId=item.id;selectedTaskId=null;persistSelection();refresh();});
            projectList.addView(v);
        }
        projectTitle.setText(p==null?"未选择项目":repo.base().projectTitle(p));
        projectMeta.setText(p==null?"请先创建项目":("项目 "+p.id.substring(0,8)+" · "+sourceCount+" 份资料 · "+tasks.size()+" 个任务"));
        taskList.removeAllViews();
        if(tasks.isEmpty())taskList.addView(text("暂无任务。创建后默认先保存在手机，不会自动外发。",12,MUTED,false));
        for(R7TaskEntity t:tasks){
            R8ExecutionEntity e=executions.get(t.id);
            String label=(t.id.equals(selectedTaskId)?"◉ ":"○ ")+repo.base().taskGoal(t)+"\n"+(e==null?t.status:e.executionState)+
                    (t.remoteTaskId==null?" · 未启动远程 Agent":" · 云端 "+t.remoteTaskId.substring(0,8));
            TextView v=text(label,11,t.id.equals(selectedTaskId)?MINT:0xffBDD8E6,false);v.setPadding(dp(8),dp(8),dp(8),dp(8));
            v.setBackground(shape(0xff0B1A2A,0xff203C52,8));v.setOnClickListener(x->{selectedTaskId=t.id;persistSelection();refresh();});
            taskList.addView(v,space(0,5));
        }
        taskTitle.setText(task==null?"暂无任务":goals.get(task.id));
        if(ex==null){
            taskMeta.setText("未创建 R8 执行状态");
            setState(executionState,"UNKNOWN");setState(contextState,"UNKNOWN");setState(dataState,"UNKNOWN");
            setState(ruleState,"UNKNOWN");setState(reasoningState,"NOT_EVALUATED");setState(qualityState,"NOT_EVALUATED");setState(evidenceState,"UNKNOWN");
        }else{
            taskMeta.setText("窗口 "+ex.contextEpoch+" · "+ex.completedUnits+"/"+ex.totalUnits+" 单元 · "+(ex.errorCodes.isEmpty()?"无阻断错误":ex.errorCodes));
            setState(executionState,ex.executionState);setState(contextState,ex.contextState);setState(dataState,ex.dataState);
            setState(ruleState,ex.ruleState);setState(reasoningState,ex.reasoningState);setState(qualityState,ex.qualityState);
            setState(evidenceState,ex.executionState.equals("READY")?"UNKNOWN":ex.executionState);
        }
    }

    private R7TaskEntity findTask(List<R7TaskEntity> tasks,String id){if(id==null)return null;for(R7TaskEntity t:tasks)if(id.equals(t.id))return t;return null;}
    private void setState(TextView v,String value){
        v.setText(value==null||value.isEmpty()?"UNKNOWN":value);
        v.setTextColor("PASS".equals(value)||"READY".equals(value)||"RUNNING".equals(value)||"CHECKPOINTED".equals(value)?MINT:("FAIL".equals(value)||"FAILED".equals(value)||"BLOCKED".equals(value)?0xffff9CA8:0xffB8C7D1));
    }
    private void notice(String s,boolean error){
        runOnUiThread(()->{notice.setText(s);notice.setTextColor(error?0xffffadb3:0xff91DFD1);notice.setBackground(shape(error?0xff38202B:0xff102E37,error?0xff8F465A:0xff28646A,10));});
    }
    private String safe(String s){return s==null||s.isEmpty()?"未知错误":s;}

    private void persistSelection(){
        getSharedPreferences("r8_screen",MODE_PRIVATE).edit().putString("project_id",projectId).putString("task_id",selectedTaskId).apply();
    }

    private void createTaskDialog(){
        if(projectId==null){notice("请先创建项目并导入资料。",true);return;}
        LinearLayout box=col();box.setPadding(dp(12),0,dp(12),0);
        EditText goal=new EditText(this);goal.setHint("任务目标，例如：完整阅读资料并形成结构化研究结论");goal.setMinLines(3);box.addView(goal);
        EditText rules=new EditText(this);rules.setHint("可选：硬规则 / JSON / 约束。没有规则不会显示 PASS。");rules.setMinLines(3);box.addView(rules,space(0,8));
        EditText model=new EditText(this);model.setSingleLine(true);model.setHint("严格模型 ID");model.setText(R7SyncClient.modelId(this));box.addView(model);
        new AlertDialog.Builder(this).setTitle("新建长期 Agent 任务").setView(box)
            .setMessage("先把目标、规则和模型策略固定到本机。真正运行前还会再次确认资料是否上传。")
            .setNegativeButton("取消",null).setPositiveButton("保存并运行",(d,w)->executor.execute(()->{
                try{
                    R7TaskEntity task=repo.createTask(projectId,goal.getText().toString(),rules.getText().toString(),model.getText().toString().trim());
                    selectedTaskId=task.id;persistSelection();
                    notice("任务已永久保存到手机。下一步会在确认资料外发后启动远程 Agent。",false);
                    startRemote(task);
                }catch(Exception e){notice("创建任务失败："+safe(e.getMessage()),true);}
            })).show();
    }

    private void startRemote(R7TaskEntity task){
        executor.execute(()->{
            try{
                if(!R7SyncClient.url(this).startsWith("https://"))throw new IllegalStateException("还没有配置 HTTPS Agent 服务");
                List<R7SourceEntity> sources=repo.base().sources(projectId);
                if(sources.isEmpty())throw new IllegalStateException("项目没有原始资料");
                final List<R7SourceEntity> authorizedSources=new ArrayList<>(sources);
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("授权当前项目资料给 Agent？")
                    .setMessage("将上传 "+authorizedSources.size()+" 份原始资料到你配置的 HTTPS 服务。上传后服务才能执行长任务；不会读取官方 AI 登录状态。")
                    .setNegativeButton("暂不上传",null).setPositiveButton("确认上传并运行",(d,w)->executor.execute(()->{
                        try{
                            R7ProjectEntity p=repo.base().project(projectId);R7SyncClient sync=new R7SyncClient(this,repo.base());
                            sync.syncProject(p);
                            for(R7SourceEntity s:authorizedSources)if(!s.sharedWithAgent)sync.uploadEvidence(s);
                            R8ExecutionEntity e=repo.execution(task.id);String rules=repo.rules(e);
                            sync.createStrictResearchTask(task,authorizedSources,rules,e.ruleVersion);
                            notice("远程 Agent 已接管。你现在退出 App，服务端仍会继续执行；回来后状态会从云端核对。",false);refresh();
                        }catch(Exception ex){notice("远程启动失败："+safe(ex.getMessage()),true);}
                    })).show());
            }catch(Exception e){notice("不能启动远程任务："+safe(e.getMessage()),true);}
        });
    }

    private void refreshRemote(){if(projectId==null||selectedTaskId==null)return;if(!refreshing.compareAndSet(false,true))return;
        executor.execute(()->{
            try{
                R7TaskEntity task=repo.base().project(projectId)==null?null:findTask(repo.base().tasks(projectId),selectedTaskId);
                if(task==null||task.remoteTaskId==null||task.remoteTaskId.isEmpty())return;
                R7SyncClient sync=new R7SyncClient(this,repo.base());
                int before=repo.execution(task.id)==null?0:repo.execution(task.id).lastEventSeq;
                JSONObject snapshot=sync.fetchResearchSnapshot(task.remoteTaskId);
                repo.applyRemoteSnapshot(task,snapshot);
                org.json.JSONArray events=sync.fetchResearchEvents(task.remoteTaskId,before);
                repo.applyEvents(task.id,events);
                notice("已核对云端真实状态：窗口 "+snapshot.optInt("context_epoch",0)+" · "+snapshot.optString("status","UNKNOWN"),false);
                refresh();
            }catch(Exception e){notice("云端暂时不可用，本机数据仍保留："+safe(e.getMessage()),true);}
            finally{refreshing.set(false);}
        });
    }

    private void resumeTask(){
        executor.execute(()->{
            try{
                R7TaskEntity task=findTask(repo.base().tasks(projectId),selectedTaskId);if(task==null)throw new IllegalStateException("没有选择任务");
                R8ExecutionEntity e=repo.execution(task.id);if(e==null||task.remoteTaskId==null||task.remoteTaskId.isEmpty())throw new IllegalStateException("当前任务尚未建立云端执行");
                if(e.remoteCheckpointId==null||e.remoteCheckpointId.isEmpty())throw new IllegalStateException("服务端没有可恢复检查点");
                R7SyncClient sync=new R7SyncClient(this,repo.base());JSONObject result=sync.resumeResearchTask(task.remoteTaskId,e.remoteCheckpointId,e.lastEventSeq);
                notice("恢复请求已被服务端接受："+result.optString("status","UNKNOWN"),false);refreshRemote();
            }catch(Exception ex){notice("恢复未执行："+safe(ex.getMessage()),true);}
        });
    }

    private void showBlob(boolean verified){
        executor.execute(()->{
            try{
                R7TaskEntity task=findTask(repo.base().tasks(projectId),selectedTaskId);if(task==null)throw new IllegalStateException("没有选择任务");
                R8ExecutionEntity e=repo.execution(task.id);if(e==null)throw new IllegalStateException("还没有执行快照");
                String body=verified?repo.verifiedReport(e):repo.draft(e);
                if(body==null||body.isEmpty())body=verified?"当前没有已验证报告。":"当前没有研究草稿。";
                final String title=verified?"已验证报告（只显示服务端验证出口）":"研究草稿（模型正文，不等同事实）";
                runOnUiThread(()->showLongText(title,body));
            }catch(Exception ex){notice("读取结果失败："+safe(ex.getMessage()),true);}
        });
    }

    private void showEvidence(){
        executor.execute(()->{
            try{
                if(selectedTaskId==null)throw new IllegalStateException("没有选择任务");
                List<R8EvidenceEntity> evidence=repo.evidence(selectedTaskId);
                List<R7EventEntity> events=repo.events(selectedTaskId);
                StringBuilder b=new StringBuilder("证据 "+evidence.size()+" 条 · 事件 "+events.size()+" 条\n\n");
                for(R8EvidenceEntity e:evidence)b.append("来源 ").append(e.sourceSha256,0,Math.min(16,e.sourceSha256.length())).append("… / ")
                    .append(e.locator).append("\n校验=").append(e.verificationState).append(" / 独立性=").append(e.independenceState).append("\n\n");
                for(R7EventEntity e:events)b.append("#").append(e.sequence).append("  ").append(e.type).append("\n");
                runOnUiThread(()->showLongText("事件链与证据",b.toString()));
            }catch(Exception ex){notice("读取证据失败："+safe(ex.getMessage()),true);}
        });
    }

    private void showLongText(String title,String body){
        TextView t=text(body,13,INK,false);t.setTextIsSelectable(true);t.setPadding(dp(8),dp(5),dp(8),dp(5));
        ScrollView s=new ScrollView(this);s.setPadding(dp(12),dp(4),dp(12),dp(4));s.addView(t);
        new AlertDialog.Builder(this).setTitle(title).setView(s).setPositiveButton("关闭",null).show();
    }

    private void openWeb(){
        String url=R7SyncClient.url(this);if(!url.startsWith("https://")){notice("请先配置 HTTPS Agent 服务。",true);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){notice("没有可打开的浏览器。",true);}
    }

    @Override protected void onDestroy(){executor.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
