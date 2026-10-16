package com.jeyu.clashgptstarter;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.IOException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Checks platform VPN transport first. HTTPS reachability is a separate, non-authentication signal. */
public final class NetworkGate {
    private NetworkGate() { }

    /** Returns a system VPN network, not necessarily CMFA's; caller must not equate both. */
    public static Network findVpnNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return network;
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return null;
    }

    public static boolean hasVpnTransport(Context context) {
        return findVpnNetwork(context) != null;
    }

    /** Call ONLY on a worker thread; force the probe onto the discovered VPN network. */
    public static boolean canReachChatGpt(Context context) {
        Network vpn = findVpnNetwork(context);
        if (vpn == null) return false;
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) vpn.openConnection(new URL("https://chatgpt.com/"));
            connection.setConnectTimeout(5500);
            connection.setReadTimeout(5500);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "GptLaunchAssistant/1.0");
            int status = connection.getResponseCode();
            // 401/403/429 are still proof of a reachable authenticated TLS/HTTP endpoint.
            return status >= 200 && status < 500;
        } catch (IOException | SecurityException ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
