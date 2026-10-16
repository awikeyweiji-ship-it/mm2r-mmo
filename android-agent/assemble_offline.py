#!/usr/bin/env python3
"""Locally combine a GitHub-built native APK shell with the user's *byte-exact* APKM.

No Android application is rewritten. The enclosing launcher gets its own fresh signing key.
"""
from __future__ import annotations
import hashlib
import os
import pathlib
import secrets
import shutil
import subprocess
import sys
import zipfile

WORK = pathlib.Path('/mnt/data/clash_gpt_r4_workspace')
BIN = WORK / 'bin'
ASSET = WORK / 'app/src/main/assets/chatgpt-original.apkm'
CLASH = WORK / 'app/src/main/assets/clash-meta.apk'
SHELL = BIN / 'r4-shell-debug.apk'
UNSIGNED = WORK / 'delivery/r4-unsigned-complete.apk'
ALIGNED = WORK / 'delivery/r4-aligned-complete.apk'
FINAL = pathlib.Path('/mnt/data/GPT_Clash_ChatGPT_R4.0_双包离线安装助手.apk')
PRIVATE = WORK / 'private-signing'
KEY = PRIVATE / 'gateway-r4-release.p12'
PASSWORD = PRIVATE / 'gateway-r4-password.txt'
EXPECTED = {
    CLASH: '93a719347dc2318b6b6356c588646a7ee82fd7ab0355e7a865fe6954de1234f0',
    ASSET: 'cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f',
}


def sha(path: pathlib.Path) -> str:
    result = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''):
            result.update(chunk)
    return result.hexdigest()


def run(*cmd: str, env=None) -> str:
    res = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
    if res.returncode:
        raise RuntimeError(f'Command failed {res.returncode}: {cmd[0]}\n{res.stdout[-5000:]}')
    return res.stdout


def main():
    for filename, digest in EXPECTED.items():
        assert filename.is_file(), filename
        actual = sha(filename)
        assert actual == digest, (filename, actual)
        print(f'INPUT_OK: {filename.name} bytes={filename.stat().st_size} sha256={actual}', flush=True)
    assert SHELL.exists(), SHELL
    with zipfile.ZipFile(SHELL) as source, zipfile.ZipFile(ASSET) as apkm:
        assert source.testzip() is None
        assert apkm.testzip() is None
        entries = set(source.namelist())
        assert 'assets/clash-meta.apk' in entries
        assert 'assets/chatgpt-original.apkm' not in entries
        assert 'classes.dex' in entries
        with source.open('assets/clash-meta.apk') as nested:
            d = hashlib.sha256()
            for buf in iter(lambda: nested.read(1<<20), b''): d.update(buf)
            assert d.hexdigest() == EXPECTED[CLASH]
        names = set(apkm.namelist())
        assert {'base.apk','split_config.arm64_v8a.apk','split_config.en.apk','split_config.zh.apk'} <= names
        print(f'SHELL_OK: shell bytes={SHELL.stat().st_size} entries={len(entries)}', flush=True)
        print(f'CHATGPT_SPLITS_OK: total={len([n for n in names if n.endswith(".apk")])}',flush=True)
        with zipfile.ZipFile(UNSIGNED, 'w', allowZip64=True) as dest:
            for info in source.infolist():
                # Strip legacy signer files, if any, before applying a fresh signature to the modified ZIP.
                if info.filename.startswith('META-INF/') and (info.filename.upper().endswith(('.RSA','.SF','.DSA'))):
                    continue
                with source.open(info) as fsrc, dest.open(info, 'w') as fdst:
                    shutil.copyfileobj(fsrc, fdst, 1024 * 1024)
            dest.write(ASSET, 'assets/chatgpt-original.apkm', compress_type=zipfile.ZIP_STORED)
    print(f'APK_ARCHIVE_BUILT: bytes={UNSIGNED.stat().st_size}',flush=True)

    env = os.environ.copy()
    env['LD_LIBRARY_PATH'] = str(BIN) + (':' + env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
    align = run(str(BIN / 'zipalign'), '-f', '-v', '4', str(UNSIGNED), str(ALIGNED),env=env)
    print('ZIPALIGN: '+align.strip().splitlines()[-1], flush=True)

    PRIVATE.mkdir(parents=True, exist_ok=True)
    if not KEY.exists():
        password = secrets.token_urlsafe(36)
        PASSWORD.write_text(password + '\n', encoding='utf-8')
        os.chmod(PASSWORD,0o600)
        run('keytool','-genkeypair','-storetype','PKCS12','-keystore',str(KEY),
            '-alias','gateway-r4','-keyalg','RSA','-keysize','3072','-validity','3650',
            '-dname','CN=GPT Clash Assistant, OU=App Distribution, O=Individual, C=US',
            '-storepass',password,'-keypass',password,'-noprompt')
        os.chmod(KEY,0o600)
    else:
        password=PASSWORD.read_text(encoding='utf-8').strip()
    signed = run('java','-jar',str(BIN / 'apksigner.jar'),'sign',
        '--ks',str(KEY),'--ks-key-alias','gateway-r4',
        '--ks-pass','pass:'+password,'--key-pass','pass:'+password,
        '--out',str(FINAL),str(ALIGNED))
    print('SIGNED: '+signed.strip()[-400:], flush=True)
    verified=run('java','-jar',str(BIN / 'apksigner.jar'),'verify','--verbose','--print-certs',str(FINAL))
    assert 'Verifies' in verified
    assert 'Number of signers: 1' in verified
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verified
    print('SIGNATURE: '+ '\n'.join(x for x in verified.splitlines() if 'Verified using v' in x or 'certificate SHA-256 digest' in x or x=='Verifies'),flush=True)
    align2=run(str(BIN / 'zipalign'), '-c','-v','4',str(FINAL),env=env)
    assert 'Verification succesful' in align2 or 'Verification successful' in align2
    print('ALIGNMENT: Verification successful',flush=True)
    with zipfile.ZipFile(FINAL) as z:
        assert z.testzip() is None
        assert z.getinfo('assets/chatgpt-original.apkm').compress_type == zipfile.ZIP_STORED
        for name, digest in [('assets/chatgpt-original.apkm',EXPECTED[ASSET]),('assets/clash-meta.apk',EXPECTED[CLASH])]:
            d=hashlib.sha256()
            with z.open(name) as f:
                for buf in iter(lambda: f.read(1<<20),b''): d.update(buf)
            assert d.hexdigest()==digest, name
            print(f'ASSET_OK: {name} {d.hexdigest()}',flush=True)
        print(f'ZIP_OK: entries={len(z.namelist())}',flush=True)
    print(f'FINAL_OK: {FINAL}\nBYTES: {FINAL.stat().st_size}\nSHA256: {sha(FINAL)}',flush=True)

if __name__ == '__main__':
    main()
