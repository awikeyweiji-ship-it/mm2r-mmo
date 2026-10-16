package com.jeyu.clashgptstarter;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable password-encrypted project recovery file, independent from Android Keystore. */
public final class R7BackupArchive {
    private static final byte[] MAGIC=new byte[]{'R','7','B','A','C','K','U','P','1'};
    private static final int SALT=16,NONCE=12,MAX_OBJECTS=128;
    private static final long MAX_UNPACK=512L*1024*1024;
    private final Context ctx;
    private final R7VaultRepository repo;
    public R7BackupArchive(Context ctx,R7VaultRepository repo){this.ctx=ctx.getApplicationContext();this.repo=repo;}
    private static SecretKeySpec derive(String pass,byte[] salt)throws Exception{
        if(pass==null||pass.length()<12)throw new IOException("恢复密码至少12位");
        PBEKeySpec params=new PBEKeySpec(pass.toCharArray(),salt,250_000,256);
        try {byte[] bytes=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(params).getEncoded();
            return new SecretKeySpec(bytes,"AES");
        }finally{params.clearPassword();}
    }
    private static void transfer(InputStream in,OutputStream out)throws IOException{
        byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
    }
    public void exportEncrypted(String projectId,String passphrase,OutputStream destination)throws Exception{
        R7ProjectEntity p=repo.project(projectId);if(p==null)throw new IOException("项目不存在");
        List<R7SourceEntity> sources=repo.sources(projectId);
        byte[] salt=new byte[SALT],nonce=new byte[NONCE];
        SecureRandom secure=new SecureRandom();secure.nextBytes(salt);secure.nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,derive(passphrase,salt),new GCMParameterSpec(128,nonce));
        destination.write(MAGIC);destination.write(salt);destination.write(nonce);
        // CipherOutputStream handles the authenticated tag; only the caller owns SAF destination.
        try(ZipOutputStream zip=new ZipOutputStream(new CipherOutputStream(destination,cipher))){
            JSONObject manifest=new JSONObject();manifest.put("format","R7-A-PORTABLE-1");manifest.put("project_id",projectId);
            manifest.put("title",repo.projectTitle(p));
            JSONArray items=new JSONArray();
            for(R7SourceEntity source:sources){
                JSONObject item=new JSONObject();item.put("filename",repo.sourceName(source));item.put("sha256",source.sha256);
                item.put("size_bytes",source.sizeBytes);item.put("mime",source.mime);items.put(item);
            }
            manifest.put("sources",items);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            for(R7SourceEntity s:sources){
                repo.files().verify(projectId,s.sha256,s.sizeBytes);
                zip.putNextEntry(new ZipEntry("objects/"+s.sha256));
                try(InputStream decrypted=repo.files().openDecrypted(projectId,s.sha256)){transfer(decrypted,zip);}
                zip.closeEntry();
            }
        }
    }
    public R7ProjectEntity restoreEncrypted(String password,InputStream encrypted)throws Exception{
        byte[] header=new byte[MAGIC.length+SALT+NONCE];int idx=0;
        while(idx<header.length){int n=encrypted.read(header,idx,header.length-idx);if(n==-1)throw new IOException("恢复包不完整");idx+=n;}
        for(int i=0;i<MAGIC.length;i++)if(header[i]!=MAGIC[i])throw new IOException("错误的恢复包格式");
        byte[] salt=new byte[SALT],nonce=new byte[NONCE];
        System.arraycopy(header,MAGIC.length,salt,0,SALT);
        System.arraycopy(header,MAGIC.length+SALT,nonce,0,NONCE);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,nonce));
        // Decrypt into private staging first: no project mutation before GCM authentication passes.
        File stage=new File(ctx.getCacheDir(),"r7-restore-"+UUID.randomUUID()+".zip");
        try{
            long total=0;
            try(CipherInputStream in=new CipherInputStream(encrypted,cipher);FileOutputStream out=new FileOutputStream(stage)){
                byte[] b=new byte[32768];int n;
                while((n=in.read(b))!=-1){total+=n;if(total>MAX_UNPACK)throw new IOException("恢复包过大");out.write(b,0,n);}
                out.getFD().sync();
            }
            try(ZipFile zip=new ZipFile(stage)){
                ZipEntry manifestEntry=zip.getEntry("manifest.json");
                if(manifestEntry==null||manifestEntry.getSize()>1_000_000)throw new IOException("恢复包缺少合法清单");
                byte[] bytes;
                try(InputStream in=zip.getInputStream(manifestEntry)){
                    java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();transferLimited(in,b,1_000_000);bytes=b.toByteArray();
                }
                JSONObject manifest=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                if(!"R7-A-PORTABLE-1".equals(manifest.getString("format")))throw new IOException("格式版本不匹配");
                JSONArray items=manifest.getJSONArray("sources");if(items.length()>MAX_OBJECTS)throw new IOException("来源数量超过限制");
                HashSet<String> seen=new HashSet<>();
                // Verify every source before mutating local Room rows.
                for(int i=0;i<items.length();i++){
                    JSONObject j=items.getJSONObject(i);String sha=j.getString("sha256");long sz=j.getLong("size_bytes");
                    if(!sha.matches("[a-f0-9]{64}")||!seen.add(sha)||sz<0||sz>25L*1024*1024)
                        throw new IOException("来源格式不合法或重复");
                    ZipEntry object=zip.getEntry("objects/"+sha);if(object==null)throw new IOException("来源缺失："+sha);
                    MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
                    try(InputStream in=zip.getInputStream(object)){
                        byte[] b=new byte[32768];int n;
                        while((n=in.read(b))!=-1){count+=n;if(count>25L*1024*1024)throw new IOException("单文件超限");hash.update(b,0,n);}
                    }
                    if(count!=sz||!toHex(hash.digest()).equals(sha))throw new IOException("来源哈希不匹配");
                }
                String projectId=manifest.getString("project_id"), title=manifest.getString("title");
                R7ProjectEntity project=repo.restoreProject(projectId,title);
                for(int i=0;i<items.length();i++){
                    JSONObject j=items.getJSONObject(i);String sha=j.getString("sha256");
                    try(InputStream in=zip.getInputStream(zip.getEntry("objects/"+sha))){
                        R7FileVault.Imported imported=repo.files().importBytes(project.id,in);
                        if(!sha.equals(imported.sha256))throw new IOException("来源内容校验失败");
                    }
                    repo.restoreSource(project.id,j.getString("filename"),j.getString("mime"),sha,j.getLong("size_bytes"));
                }
                return project;
            }
        }finally{if(stage.exists())stage.delete();}
    }
    private static void transferLimited(InputStream in,OutputStream out,long max)throws IOException{
        byte[] b=new byte[8192];long total=0;int n;
        while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("清单超长");out.write(b,0,n);}
    }
    private static String toHex(byte[] bytes){StringBuilder sb=new StringBuilder();for(byte b:bytes)sb.append(String.format(java.util.Locale.US,"%02x",b&255));return sb.toString();}
}
