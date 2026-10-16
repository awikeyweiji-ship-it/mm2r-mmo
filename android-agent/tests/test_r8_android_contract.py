from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/com/jeyu/clashgptstarter"
MAIN=(JAVA/"MainActivity.java").read_text(encoding="utf-8")
ACT=(JAVA/"R8ResearchActivity.java").read_text(encoding="utf-8")
REPO=(JAVA/"R8ResearchRepository.java").read_text(encoding="utf-8")
CLIENT=(JAVA/"R7SyncClient.java").read_text(encoding="utf-8")
DB=(JAVA/"R7VaultDatabase.java").read_text(encoding="utf-8")
BLOB=(JAVA/"R8BlobVault.java").read_text(encoding="utf-8")
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def test_main_launcher_opens_r8():
    assert "R8ResearchActivity.class" in MAIN

def test_r8_is_registered_and_not_exported():
    assert '.R8ResearchActivity" android:exported="false"' in MANIFEST

def test_room_v2_has_r8_tables_and_migration():
    assert "version=2" in DB
    assert "r8_executions" in DB and "r8_memories" in DB and "r8_evidence" in DB
    assert "MIGRATION_1_2" in DB

def test_local_console_separates_failure_domains():
    for label in ["上下文完整性","资料完整性","规则验证","推理质量","模型基准","证据独立性"]:
        assert label in ACT
    assert "UNKNOWN" in ACT and "READY" in ACT

def test_client_is_strict_and_never_sends_automatic_model_fallback():
    assert '"mode","STRICT"' in CLIENT
    assert '"allow_model_fallback",false' in CLIENT
    assert '"allow_provider_failover_same_model",true' in CLIENT

def test_remote_identity_is_checked_before_local_apply():
    assert "REMOTE_PROJECT_ID_MISMATCH" in REPO
    assert "REMOTE_TASK_ID_MISMATCH" in REPO

def test_verified_memory_only():
    assert 'trust='VERIFIED'' in Path(ROOT/"app/src/main/java/com/jeyu/clashgptstarter/R7VaultDao.java").read_text(encoding="utf-8")
    assert 'if(!"VERIFIED".equals(trust))continue;' in REPO

def test_blob_vault_prevents_path_escape():
    assert "getCanonicalFile" in BLOB
    assert "startsWith(base.toPath())" in BLOB

def test_no_room_reads_in_android_main_render_path():
    assert "repo.execution(t.id)" not in ACT
    assert "repo.base().sources(p.id)" not in ACT
