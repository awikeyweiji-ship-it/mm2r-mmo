package com.jeyu.clashgptstarter;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted private storage for durable remote snapshots and reports. */
public final class R8BlobVault {
    private static final byte[] MAGIC=new byte[]{'R','8','B','1'};
    private final Context ctx;
    public R8BlobVault(Context c){ctx=c.getApplicationContext();}

    private File folder(String taskId){
        if(taskId==null||!taskId.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid task UUID");
        File f=new File(ctx.getFilesDir(),"r8-vault/tasks/"+taskId);
        if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("Cannot create R8 vault folder");
        return f;
    }
    public String write(String taskId,String kind,String text)throws Exception{
        if(kind==null||!kind.matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid blob kind");
        if(text==null)text="";
        File target=new File(folder(taskId),kind+"-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".r8b");
        File partial=new File(target.getParentFile(),".incoming-"+UUID.randomUUID());
        SecretKey key=R7FileVault.key();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);
        try(FileOutputStream fos=new FileOutputStream(partial)){
            fos.write(MAGIC);fos.write(c.getIV());
            try(CipherOutputStream out=new CipherOutputStream(fos,c)){
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            fos.getFD().sync();
        }
        if(!partial.renameTo(target)){partial.delete();throw new IOException("Atomic R8 blob move failed");}
        return ctx.getFilesDir().toPath().relativize(target.toPath()).toString();
    }
    public String read(String relative)throws Exception{
        if(relative==null||relative.isEmpty())return "";
        File f=new File(ctx.getFilesDir(),relative);
        File base=new File(ctx.getFilesDir(),"r8-vault").getCanonicalFile();
        File canonical=f.getCanonicalFile();
        if(!canonical.toPath().startsWith(base.toPath()))throw new SecurityException("R8 blob path escapes private vault");
        try(FileInputStream fis=new FileInputStream(canonical)){
            byte[] header=new byte[16];int off=0;
            while(off<header.length){int n=fis.read(header,off,header.length-off);if(n<0)throw new IOException("Truncated R8 blob");off+=n;}
            for(int i=0;i<MAGIC.length;i++)if(header[i]!=MAGIC[i])throw new IOException("Invalid R8 blob");
            byte[] iv=new byte[12];System.arraycopy(header,4,iv,0,12);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,R7FileVault.key(),new GCMParameterSpec(128,iv));
            try(InputStream in=new CipherInputStream(fis,c)){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[32768];int n;
                while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2*1024*1024)throw new IOException("R8 blob exceeds 2MB safety limit");}
                return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }
}
