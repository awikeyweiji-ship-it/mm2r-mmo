#!/usr/bin/env python3
"""Package the verified native R5 shell with three original install archives.

The nested application APK/APKM files are copied byte for byte. Only the
outer assistant package is re-signed using the private, R4-compatible key.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path('/mnt/data/clash_gpt_r5_work')
APP = ROOT / 'project/app/src/main/assets'
TOOLS = ROOT / 'tools/release'
KEYSTORE = ROOT / 'private/gateway-r4-release.p12'
PASSWORD = ROOT / 'private/gateway-r4-password.txt'
OUTDIR = Path('/mnt/data')
OUTPUT = OUTDIR / 'AI_Gateway_R5_ChatGPT_Gemini_Clash_智能选点.apk'
STAGING = ROOT / 'assembly'
STAGING.mkdir(parents=True, exist_ok=True)

ASSETS = {
    'assets/clash-meta.apk': ('clash-meta.apk', '93a719347dc2318b6b6356c588646a7ee82fd7ab0355e7a865fe6954de1234f0'),
    'assets/chatgpt-original.apkm': ('chatgpt-original.apkm','cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f'),
    'assets/gemini-original.apk': ('gemini-original.apk','df5d131c354853764de41cb6a5e579692387971e17d4b0b3beb2e08edd587c68'),
}


def sha256(path:Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1<<20),b''):
            h.update(b)
    return h.hexdigest()


def nested_sha(z:zipfile.ZipFile,key:str)->str:
    h=hashlib.sha256()
    with z.open(key) as fd:
        for b in iter(lambda:fd.read(1<<20),b''):
            h.update(b)
    return h.hexdigest()


def run(args:list[str], *, env=None)->str:
    cp=subprocess.run(args,capture_output=True,text=True,env=env)
    if cp.returncode:
        raise RuntimeError(f'{args[0]} returned {cp.returncode}:\n{(cp.stdout+cp.stderr)[-2000:]}')
    return cp.stdout+cp.stderr


def main():
    for dest,(name,digest) in ASSETS.items():
        path=APP/name
        if not path.is_file() or sha256(path)!=digest:
            raise AssertionError(f'Unverified asset: {dest}')
        print('ASSET_VERIFIED', name, path.stat().st_size, digest,flush=True)
    shell=TOOLS/'r5-shell-debug.apk'
    if not shell.is_file(): raise FileNotFoundError(shell)
    unsigned=STAGING/'r5-unsigned.apk'
    aligned=STAGING/'r5-aligned.apk'
    with zipfile.ZipFile(shell) as src:
        assert src.testzip() is None
        assert {'classes.dex','AndroidManifest.xml','assets/clash-meta.apk'} <= set(src.namelist())
        assert nested_sha(src,'assets/clash-meta.apk')==ASSETS['assets/clash-meta.apk'][1]
        with zipfile.ZipFile(unsigned,'w',allowZip64=True) as target:
            for info in src.infolist():
                name=info.filename
                if (name.startswith('META-INF/') and name.upper().endswith(('.SF','.RSA','.DSA'))):
                    continue
                if name in ('assets/chatgpt-original.apkm','assets/gemini-original.apk'):
                    # Shell is supposed to be built *without* these two vendor binaries.
                    raise AssertionError('Source shell unexpectedly includes nested vendor app')
                with src.open(info) as inp, target.open(info,'w') as dest:
                    shutil.copyfileobj(inp,dest,1<<20)
            target.write(APP/'chatgpt-original.apkm','assets/chatgpt-original.apkm',compress_type=zipfile.ZIP_STORED)
            target.write(APP/'gemini-original.apk','assets/gemini-original.apk',compress_type=zipfile.ZIP_STORED)
    env=os.environ.copy()
    env['LD_LIBRARY_PATH']=str(TOOLS)+(':'+env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
    align=run([str(TOOLS/'zipalign'),'-f','-v','4',str(unsigned),str(aligned)],env=env)
    print('ZIPALIGN',align.splitlines()[-1],flush=True)
    pwd=PASSWORD.read_text(encoding='utf-8').strip()
    env['GATEWAY_KEYPASS']=pwd
    run(['java','-jar',str(TOOLS/'apksigner.jar'),'sign',
         '--ks',str(KEYSTORE),'--ks-key-alias','gateway-r4',
         '--ks-pass','env:GATEWAY_KEYPASS','--key-pass','env:GATEWAY_KEYPASS',
         '--out',str(OUTPUT),str(aligned)],env=env)
    verify=run(['java','-jar',str(TOOLS/'apksigner.jar'),'verify','--verbose','--print-certs',str(OUTPUT)])
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verify
    assert 'b1e2610c8b6271ca6382c951cc30929968dbe8c674936671fc90d00e1a345c9d' in verify
    check=run([str(TOOLS/'zipalign'),'-c','-v','4',str(OUTPUT)],env=env)
    assert ('Verification successful' in check or 'Verification succesful' in check)
    print('SIGNED_AND_ALIGNED',flush=True)
    with zipfile.ZipFile(OUTPUT) as f:
        assert f.testzip() is None
        for key,(_,digest) in ASSETS.items():
            assert nested_sha(f,key)==digest, key
            print('NESTED_VERIFIED',key,digest,flush=True)
        assert f.getinfo('assets/chatgpt-original.apkm').compress_type==zipfile.ZIP_STORED
        assert f.getinfo('assets/gemini-original.apk').compress_type==zipfile.ZIP_STORED
        entry_names=f.namelist()
    report={
        'file':OUTPUT.name,
        'outer_size_bytes':OUTPUT.stat().st_size,
        'outer_sha256':sha256(OUTPUT),
        'assistant_signer_sha256':'b1e2610c8b6271ca6382c951cc30929968dbe8c674936671fc90d00e1a345c9d',
        'assistant_version':'5.0.0',
        'embedded_origins':{key:{'bytes':(APP/name).stat().st_size,'sha256':digest} for key,(name,digest) in ASSETS.items()},
        'all_nested_bytes_unchanged':True,
        'zip_entries':len(entry_names),
        'zipalign':'pass', 'apksigner':'pass',
        'device_runtime_test':'not-performed',
    }
    path=OUTDIR/'AI_Gateway_R5_交付校验.json'
    path.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print('FINAL',json.dumps(report,ensure_ascii=False),flush=True)

if __name__=='__main__': main()
