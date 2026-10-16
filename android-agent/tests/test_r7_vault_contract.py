"""Static attack-surface contract in addition to Gradle compilation. Not a device test."""
from pathlib import Path

SRC=Path(__file__).parents[1]
JAVA=SRC/'app/src/main/java/com/jeyu/clashgptstarter'


def read(name):return (JAVA/name).read_text()


def test_room_wal_and_local_project_schema():
    gradle=(SRC/'app/build.gradle').read_text()
    assert 'androidx.room:room-runtime:2.8.5' in gradle
    assert 'annotationProcessor' in gradle
    src=read('R7VaultDatabase.java')
    assert '@Database' in src and 'WRITE_AHEAD_LOGGING' in src
    assert 'R7ProjectEntity.class' in src and 'R7SourceEntity.class' in src
    assert 'R7EventEntity.class' in src


def test_keystore_encryption_durable_and_hash_checked():
    src=read('R7FileVault.java')
    assert 'AndroidKeyStore' in src
    assert 'AES/GCM/NoPadding' in src
    assert 'MessageDigest.getInstance("SHA-256")' in src
    assert 'getFilesDir()' in src
    assert 'SOURCE_INTEGRITY_FAILURE' in src


def test_native_project_ui_preserves_r6_entry():
    activity=read('MainActivity.java')
    assert 'R7ProjectsActivity.class' in activity
    manifest=(SRC/'app/src/main/AndroidManifest.xml').read_text()
    assert '.R7ProjectsActivity' in manifest
    assert 'android:exported="false"' in manifest
    assert 'android:allowBackup="false"' in manifest
    screen=read('R7ProjectsActivity.java')
    assert '创建离线项目' in screen
    assert '导入原始文件' in screen
    assert '设备本地' in screen


def test_explicit_sync_consent_and_https_security():
    client=read('R7SyncClient.java')
    assert 'HttpsURLConnection' in client
    assert 'https://' in client
    assert 'X-Upload-Consent' in client
    assert 'uploadEvidence' in client
    assert 'Authorization' in client
    assert 'doNotSendWithoutConsent' in client


def test_prior_launch_flows_remain():
    m=read('MainActivity.java')
    assert 'ServiceTarget.CHATGPT' in m and 'ServiceTarget.GEMINI' in m and 'ServiceTarget.CLAUDE' in m
    assert 'checkAndContinue' in m
