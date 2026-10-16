package com.jeyu.clashgptstarter;

import android.content.Context;
import android.net.Network;
import android.os.SystemClock;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Explicit user-authorized selector scan. The app never fabricates a node or
 * silently edits subscriptions/configurations. A successful delay probe is
 * only a candidate; the chosen route must additionally pass a VPN-bound
 * service HTTPS check. No app login credentials are ever accessed.
 */
public final class SmartRouteEngine {
    public interface Progress {void onProgress(int done,int total,String info);}
    public static final class Outcome {
        public final boolean ready,autoSelected;
        public final String message,node,selector;
        public final int checked,total;
        private Outcome(boolean ready,boolean selected,String message,String node,
                        String selector,int checked,int total) {
            this.ready=ready;this.autoSelected=selected;this.message=message;
            this.node=node;this.selector=selector;this.checked=checked;this.total=total;
        }
        public static Outcome error(String message,int checked,int total) {
            return new Outcome(false,false,message,"","",checked,total);
        }
        public static Outcome success(String selector,String node,int checked,int total) {
            return new Outcome(true,true,"服务网站可达；请在应用内确认登录和地区资格",
                    node,selector,checked,total);
        }
    }
    private SmartRouteEngine(){}

    /** Run on the single background worker; this starts a small bounded scan pool. */
    public static Outcome optimize(Context ctx,ServiceTarget target,Progress callback) {
        if (target == ServiceTarget.CLAUDE)
            return Outcome.error("Claude 稳定模式不自动扫描或轮换节点，请保持当前合法连接",0,0);
        if (!ControllerSecrets.enabled(ctx))
            return Outcome.error("自动选点未开启；请在设置里完成本地控制配置",0,0);
        String token=ControllerSecrets.loadSecret(ctx);
        if(token.isEmpty())
            return Outcome.error("未找到安全保存的控制密钥，请在设置中重新保存",0,0);
        Network initial=NetworkGate.findVpnNetwork(ctx);
        if(initial==null)
            return Outcome.error("尚未检测到 VPN。先在 Clash 启动连接",0,0);
        MihomoController controller;
        try {
            controller=new MihomoController(ControllerSecrets.endpoint(ctx),token);
        }catch(Exception e){return Outcome.error(e.getMessage(),0,0);}
        int completed=0,total=0;
        String selector="",original="";
        boolean selectedSuccessfully=false;
        List<String> allowed=new ArrayList<>();
        try {
            JSONObject proxies=controller.getProxies();
            selector=controller.findSelector(proxies,ControllerSecrets.group(ctx),controller.mode());
            allowed=controller.members(proxies,selector);
            original=controller.currentlySelected(proxies,selector);
            total=allowed.size();
            if(callback!=null)callback.onProgress(0,total,"正在按 "+target.label+" 实际访问地址检测节点");
            ExecutorService pool=Executors.newFixedThreadPool(Math.min(8,Math.max(1,total)));
            ExecutorCompletionService<SmartNodePolicy.Candidate> ecs=new ExecutorCompletionService<>(pool);
            try {
                for(String node:allowed) {
                    ecs.submit(() -> new SmartNodePolicy.Candidate(node,
                            controller.delay(node,target),controller.delay(node,target)));
                }
                long deadline=SystemClock.elapsedRealtime()+90_000L;
                List<SmartNodePolicy.Candidate> tested=new ArrayList<>();
                while(completed<total && !Thread.currentThread().isInterrupted()){
                    long left=deadline-SystemClock.elapsedRealtime();
                    if(left<=0)break;
                    Future<SmartNodePolicy.Candidate> f=ecs.poll(left,TimeUnit.MILLISECONDS);
                    if(f==null)break;
                    completed++;
                    try{SmartNodePolicy.Candidate candidate=f.get();
                        if(candidate.firstMs>0 && candidate.secondMs>0)tested.add(candidate);
                    }catch(Exception ignored){}
                    if(callback!=null && (completed%3==0 || completed==total))
                        callback.onProgress(completed,total,"已检测 "+completed+" / "+total+" 个节点");
                }
                List<SmartNodePolicy.Candidate> ranked=SmartNodePolicy.rank(tested);
                if(ranked.isEmpty())return Outcome.error(
                        "节点测速未通过。请确认 Clash 本地控制接口和订阅配置",completed,total);
                int trials=Math.min(6,ranked.size());
                String lastError="";
                for(int i=0;i<trials;i++){
                    SmartNodePolicy.Candidate c=ranked.get(i);
                    if(callback!=null)callback.onProgress(completed,total,
                            "尝试节点："+c.name+"（"+(i+1)+"/"+trials+"）");
                    try {
                        controller.select(selector,c.name,allowed);
                        if(!controller.isSelected(selector,c.name)) {
                            lastError="策略组切换未生效";continue;
                        }
                        Thread.sleep(650);
                        if(!initial.equals(NetworkGate.findVpnNetwork(ctx))) {
                            lastError="VPN 在切换途中断开";break;
                        }
                        ServiceNetworkGate.Result site=ServiceNetworkGate.probe(ctx,target);
                        if(site.reachable){
                            selectedSuccessfully=true;
                            return Outcome.success(selector,c.name,completed,total);
                        }
                        lastError=site.detail;
                    }catch(Exception e) {lastError=e.getMessage();}
                }
                return Outcome.error("已试用前 "+trials+" 个候选，但 "+target.label+
                        " 未能验证访问；"+lastError,completed,total);
            } finally {
                pool.shutdownNow();
                // Failure recovery performed in the outer finally block.
            }
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            return Outcome.error("选点已中断",completed,total);
        }catch(Exception e){
            return Outcome.error("智能选点不可用："+e.getMessage(),completed,total);
        }finally{
            if(!selectedSuccessfully && !selector.isEmpty() && !original.isEmpty() &&
                    controller!=null && !allowed.isEmpty()){
                // Roll back only this selector's previous value. Do not modify profiles or other groups.
                try {
                    List<String> restoreAllowed=new ArrayList<>(allowed);
                    restoreAllowed.add(original);
                    controller.select(selector,original,restoreAllowed);
                }catch(Exception ignored){}
            }
        }
    }
}
