package com.jeyu.clashgptstarter;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM content addressed storage under private filesDir; no public external storage. */
public final class R7FileVault {
    private static final String KEY_ALIAS="ai_gateway_r7_evidence_aes_v1";
    private static final byte[] MAGIC=new byte[]{'R','7','V','1'};
    private static final long FILE_LIMIT=25L*1024*1024;
    private final Context ctx;
    public R7FileVault(Context context){ctx=context.getApplicationContext();}

    static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(KEY_ALIAS)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());gen.generateKey();
        }
        return (SecretKey)ks.getKey(KEY_ALIAS,null);
    }
    public String encryptText(String raw)throws Exception {
        if(raw==null)raw="";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(c.doFinal(raw.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    public String decryptText(String encrypted)throws Exception {
        String[] parts=encrypted.split(":",2);if(parts.length!=2)throw new IOException("Invalid encrypted text");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private File folder(String projectId){
        if(!projectId.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid project UUID");
        File f=new File(ctx.getFilesDir(),"r7-vault/projects/"+projectId+"/objects");
        if(!f.isDirectory() && !f.mkdirs())throw new IllegalStateException("Cannot create private vault folder");
        return f;
    }
    public File objectFile(String projectId,String sha){
        if(!sha.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid SHA256");
        return new File(folder(projectId),sha);
    }
    public static final class Imported {
        public final String sha256;public final long size;public final File object;
        Imported(String digest,long bytes,File encrypted){sha256=digest;size=bytes;object=encrypted;}
    }
    public Imported importUri(String projectId, Uri source)throws Exception {
        try(InputStream in=ctx.getContentResolver().openInputStream(source)){
            if(in==null)throw new IOException("Selected document cannot be read");
            return importBytes(projectId,in);
        }
    }
    public Imported importBytes(String projectId,InputStream input)throws Exception {
        File dir=folder(projectId);File partial=new File(dir,".incoming-"+UUID.randomUUID());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long len=0;
        try{
            FileOutputStream fos=new FileOutputStream(partial);
            try{
                fos.write(MAGIC);fos.write(cipher.getIV());
                // Close CipherOutputStream to append GCM tag, but do not prematurely close fd before fsync.
                FilterOutputStream hold=new FilterOutputStream(fos){@Override public void close()throws IOException{flush();}};
                CipherOutputStream enc=new CipherOutputStream(hold,cipher);
                byte[] buffer=new byte[32768];int n;
                while((n=input.read(buffer))!=-1){
                    len+=n;if(len>FILE_LIMIT)throw new IOException("Document larger than 25 MB local limit");
                    digest.update(buffer,0,n);enc.write(buffer,0,n);
                }
                enc.close();fos.getFD().sync();
            }finally{fos.close();}
            String hex=toHex(digest.digest());
            File target=objectFile(projectId,hex);
            if(!target.exists()){
                if(!partial.renameTo(target))throw new IOException("Atomic file move failed");
            }else{
                // A matching filename alone does NOT prove the existing GCM data is valid.
                // Fail closed on corruption instead of silently reusing an invalid object.
                verify(projectId,hex,len);
                partial.delete();
            }
            return new Imported(hex,len,target);
        }finally{if(partial.exists())partial.delete();}
    }
    public InputStream openDecrypted(String projectId,String sha)throws Exception {
        File encrypted=objectFile(projectId,sha);
        FileInputStream fis=new FileInputStream(encrypted);
        try{
            byte[] header=new byte[16];int read=0;
            while(read<header.length){int n=fis.read(header,read,header.length-read);if(n<0)throw new IOException("Truncated encrypted source");read+=n;}
            for(int i=0;i<MAGIC.length;i++)if(header[i]!=MAGIC[i])throw new IOException("Invalid vault magic");
            byte[] nonce=new byte[12];System.arraycopy(header,4,nonce,0,12);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
            return new CipherInputStream(new BufferedInputStream(fis),c);
        }catch(Exception ex){fis.close();throw ex;}
    }
    public void verify(String projectId,String sha,long expectedSize)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(InputStream in=openDecrypted(projectId,sha)){
            byte[] b=new byte[32768];int n;
            while((n=in.read(b))!=-1){digest.update(b,0,n);total+=n;}
        }
        if(total!=expectedSize || !toHex(digest.digest()).equals(sha))
            throw new SecurityException("SOURCE_INTEGRITY_FAILURE: " + sha);
    }
    private static String toHex(byte[] bytes){StringBuilder sb=new StringBuilder();for(byte b:bytes)sb.append(String.format(java.util.Locale.US,"%02x",b&255));return sb.toString();}
}
