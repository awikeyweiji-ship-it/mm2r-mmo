package com.jeyu.clashgptstarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** No plaintext persistence of the user-provided Mihomo local controller token. */
public final class ControllerSecrets {
    private static final String STORE="smart_route_cfg";
    private static final String ALIAS="ai_gateway_local_secret_v1";
    private ControllerSecrets(){}

    public static String endpoint(Context ctx) {
        return ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString("endpoint","127.0.0.1:9090");
    }
    public static String group(Context ctx) {
        return ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString("selector","");
    }
    public static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).getBoolean("auto",false);
    }
    public static void setEnabled(Context ctx,boolean enabled) {
        ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putBoolean("auto",enabled).apply();
    }
    public static void save(Context ctx,String portText,String group,String secret) throws Exception {
        // Controller stays on loopback only; no remote host or public listener is accepted.
        int port=Integer.parseInt(portText.trim());
        if(port<1 || port>65535)throw new IllegalArgumentException("端口必须为 1～65535");
        if(secret==null || secret.isEmpty()) throw new IllegalArgumentException("请填写 Clash 本地控制密钥");
        if(secret.length()>256) throw new IllegalArgumentException("控制密钥过长");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] ciphertext=cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        String saved=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
                Base64.encodeToString(ciphertext,Base64.NO_WRAP);
        SharedPreferences.Editor editor=ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit();
        editor.putString("endpoint","127.0.0.1:"+port);
        editor.putString("selector",group==null?"":group.trim());
        editor.putString("ciphertext",saved);
        editor.apply();
    }
    public static String loadSecret(Context ctx) {
        String data=ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE).getString("ciphertext","");
        if(data==null || data.isEmpty())return "";
        try {
            String[] parts=data.split(":",2);
            if(parts.length!=2)return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,
                    Base64.decode(parts[0],Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        }catch(Exception e) {
            // Keystore key can be invalidated by OS changes. Never fall back to plaintext storage.
            return "";
        }
    }
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS,null);
    }
}
