from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/jeyu/clashgptstarter'

def test_android_can_pull_remote_task_checkpoint():
    sync=(JAVA/'R7SyncClient.java').read_text()
    activity=(JAVA/'R7ProjectsActivity.java').read_text()
    assert 'fetchRemoteTask' in sync
    assert 'refreshRemoteTasks' in activity
    assert '更新云端任务状态' in activity
    assert 'remoteCheckpointId' in (JAVA/'R7TaskEntity.java').read_text()
