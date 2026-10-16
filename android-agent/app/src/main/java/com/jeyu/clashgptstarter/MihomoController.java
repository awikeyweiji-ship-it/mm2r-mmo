package com.jeyu.clashgptstarter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mihomo REST client with a strict loopback-only control plane.
 * Requires explicit CMFA Override configuration for external-controller + secret.
 * Does not read subscriptions or forward the user's controller token over the Internet.
 */
public final class MihomoController {
    private final String root, token;
    private final int timeoutMs=4800;

    public MihomoController(String endpoint,String secret) {
        if(endpoint==null || !endpoint.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) {
            throw new IllegalArgumentException("仅允许连接本机 127.0.0.1 控制接口");
        }
        int port=Integer.parseInt(endpoint.substring(endpoint.indexOf(':')+1));
        if(port<=0 || port>65535)throw new IllegalArgumentException("控制接口端口无效");
        if(secret==null || secret.isEmpty())throw new IllegalArgumentException("请设置本地控制密钥");
        if(secret.length()>256)throw new IllegalArgumentException("控制密钥长度不合理");
        root="http://"+endpoint;
        token=secret;
    }

    private JSONObject call(String method,String path,String jsonBody) throws IOException {
        if(!path.startsWith("/") || path.startsWith("//") ||
                path.contains("://") || path.contains("\\") || path.contains("\r") ||
                path.contains("\n")) throw new IOException("拒绝不安全的控制接口路径");
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(root+path).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestProperty("Accept","application/json");
            if(jsonBody!=null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()) {
                    out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status=c.getResponseCode();
            if(status==401 || status==403)throw new IOException("Clash 本地控制密钥不正确或无权限");
            if(status==404)throw new IOException("Clash 本地控制接口不可用，请检查覆写配置");
            if(status<200 || status>=300)throw new IOException("本地控制器响应："+status);
            if(status==204)return new JSONObject();
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int total=0,n;
                while((n=in.read(buf))!=-1){
                    total+=n;if(total>1024*1024)throw new IOException("Clash 返回数据过大");
                    out.write(buf,0,n);
                }
                try {return new JSONObject(out.toString("UTF-8"));}
                catch(Exception ex) {throw new IOException("Clash 控制器返回数据无法解析",ex);}
            }
        } finally {if(c!=null)c.disconnect();}
    }

    public JSONObject getProxies() throws IOException {
        JSONObject body=call("GET","/proxies",null);
        JSONObject proxies=body.optJSONObject("proxies");
        if(proxies==null)throw new IOException("无法读取 Clash 节点列表");
        return proxies;
    }
    public String mode() throws IOException {
        return call("GET","/configs",null).optString("mode","rule");
    }

    public String findSelector(JSONObject proxies,String preference,String mode) throws IOException {
        if(preference!=null && !preference.trim().isEmpty()) {
            JSONObject item=proxies.optJSONObject(preference.trim());
            if(item!=null && "Selector".equalsIgnoreCase(item.optString("type","")))
                return preference.trim();
            throw new IOException("指定的策略组不存在，或不支持手动切换");
        }
        if("global".equalsIgnoreCase(mode)) {
            JSONObject global=proxies.optJSONObject("GLOBAL");
            if(global!=null && "Selector".equalsIgnoreCase(global.optString("type","")))
                return "GLOBAL";
        }
        List<String> selectors=new ArrayList<>();
        java.util.Iterator<String> it=proxies.keys();
        while(it.hasNext()) {
            String name=it.next();
            JSONObject item=proxies.optJSONObject(name);
            if(item!=null && "Selector".equalsIgnoreCase(item.optString("type","")) &&
                    !"GLOBAL".equals(name))selectors.add(name);
        }
        if(selectors.size()==1)return selectors.get(0);
        throw new IOException("当前有多个策略组，需在设置中指定 AI 网站实际使用的策略组");
    }

    public List<String> members(JSONObject proxies,String selector) throws IOException {
        JSONObject group=proxies.optJSONObject(selector);
        if(group==null || !"Selector".equalsIgnoreCase(group.optString("type","")))
            throw new IOException("不能在非 Selector 策略组切换节点");
        JSONArray all=group.optJSONArray("all");
        if(all==null)throw new IOException("策略组里没有可扫描的节点列表");
        Set<String> unique=new LinkedHashSet<>();
        for(int i=0;i<all.length();i++){
            String name=all.optString(i,"");
            if(!SmartNodePolicy.isSelectCandidate(name))continue;
            JSONObject item=proxies.optJSONObject(name);
            String kind=item==null?"":item.optString("type","");
            if("Selector".equalsIgnoreCase(kind)||"URLTest".equalsIgnoreCase(kind)
                    ||"LoadBalance".equalsIgnoreCase(kind)
                    ||"Fallback".equalsIgnoreCase(kind))continue;
            unique.add(name);
        }
        if(unique.isEmpty())throw new IOException("策略组中没有可选择的节点");
        if(unique.size()>512)throw new IOException("本组超过512个节点，请先在 Clash 中缩小策略组");
        return new ArrayList<>(unique);
    }

    public String currentlySelected(JSONObject proxies,String selector) {
        JSONObject group=proxies.optJSONObject(selector);
        return group==null?"":group.optString("now","");
    }

    public int delay(String node,ServiceTarget service) {
        try{
            String name=URLEncoder.encode(node,"UTF-8").replace("+","%20");
            String url=URLEncoder.encode(service.probeUrl,"UTF-8");
            JSONObject response=call("GET","/proxies/"+name+"/delay?url="+url+"&timeout=4000",null);
            int ms=response.optInt("delay",-1);
            return ms>0 && ms<=30000?ms:-1;
        }catch(Exception e){return -1;}
    }

    public void select(String group,String node,List<String> allowed) throws IOException {
        if(!allowed.contains(node))throw new SecurityException("拒绝选择不在当前策略组内的节点");
        String path="/proxies/"+URLEncoder.encode(group,"UTF-8").replace("+","%20");
        JSONObject body=new JSONObject();
        try {body.put("name",node);}
        catch(Exception e){throw new IOException("节点名称编码失败",e);}
        call("PUT",path,body.toString());
    }

    public boolean isSelected(String group,String node) throws IOException {
        String path="/proxies/"+URLEncoder.encode(group,"UTF-8").replace("+","%20");
        return node.equals(call("GET",path,null).optString("now",""));
    }
}
