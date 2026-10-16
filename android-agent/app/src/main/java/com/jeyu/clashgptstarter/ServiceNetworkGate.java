package com.jeyu.clashgptstarter;

import android.content.Context;
import android.net.Network;
import java.io.IOException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Tests public service reachability only over the Android VPN network, never on Wi-Fi fallback. */
public final class ServiceNetworkGate {
    private ServiceNetworkGate(){}

    public static final class Result {
        public final boolean reachable;
        public final int status;
        public final String detail;
        public Result(boolean reachable,int status,String detail) {
            this.reachable=reachable;this.status=status;this.detail=detail;
        }
    }

    public static Result probe(Context ctx, ServiceTarget target) {
        Network vpn=NetworkGate.findVpnNetwork(ctx);
        if (vpn==null) return new Result(false,0,"未发现系统 VPN，不能检测");
        HttpsURLConnection conn=null;
        try {
            conn=(HttpsURLConnection)vpn.openConnection(new URL(target.probeUrl));
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("User-Agent","AIGateway/6.0 ConnectivityCheck");
            conn.setUseCaches(false);
            int code=conn.getResponseCode();
            String challenge=conn.getHeaderField("cf-mitigated");
            if ("challenge".equalsIgnoreCase(challenge)) {
                return new Result(false,code,"访问触发安全验证，需在官方应用中检查");
            }
            if (ServiceTarget.networkReachable(code)) {
                return new Result(true,code,code>=300?"服务网站可达，可能还需登录":"服务网站可达，账户权限待登录确认");
            }
            if (code==401) return new Result(false,code,"网站要求登录认证；不代表节点已获授权");
            if (code==403 || code==451) return new Result(false,code,"网站拒绝访问；可能涉及节点信誉、访问政策或区域");
            if (code==429) return new Result(false,code,"请求受到限流；此时不切换为成功状态");
            return new Result(false,code,"网站返回 HTTP "+code+"，未验证为可用");
        } catch (IOException | SecurityException e) {
            return new Result(false,0,"连接失败或 TLS 无法建立");
        } finally {
            if(conn!=null)conn.disconnect();
        }
    }
}
