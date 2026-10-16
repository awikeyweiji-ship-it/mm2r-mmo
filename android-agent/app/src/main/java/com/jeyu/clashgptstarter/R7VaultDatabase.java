package com.jeyu.clashgptstarter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Durable local metadata. R8 adds execution/memory/evidence without destroying R7 data. */
@Database(entities={R7ProjectEntity.class,R7SourceEntity.class,R7TaskEntity.class,
       R7EventEntity.class,R7SyncOutboxEntity.class,R8ExecutionEntity.class,
       R8MemoryEntity.class,R8EvidenceEntity.class},version=2,exportSchema=false)
public abstract class R7VaultDatabase extends RoomDatabase {
    private static volatile R7VaultDatabase INSTANCE;
    public abstract R7VaultDao dao();
    private static final Migration MIGRATION_1_2=new Migration(1,2){
        @Override public void migrate(@NonNull SupportSQLiteDatabase db){
            db.execSQL("CREATE TABLE IF NOT EXISTS r8_executions ("+
                "taskId TEXT NOT NULL PRIMARY KEY, executionState TEXT NOT NULL, contextState TEXT NOT NULL, "+
                "dataState TEXT NOT NULL, ruleState TEXT NOT NULL, reasoningState TEXT NOT NULL, qualityState TEXT NOT NULL, "+
                "modelId TEXT NOT NULL, modelPolicy TEXT NOT NULL, ruleVersion TEXT NOT NULL, encryptedRules TEXT NOT NULL, "+
                "remoteDataConsent INTEGER NOT NULL, contextEpoch INTEGER NOT NULL, completedUnits INTEGER NOT NULL, "+
                "totalUnits INTEGER NOT NULL, lastEventSeq INTEGER NOT NULL, remoteCheckpointId TEXT, remoteRunId TEXT, "+
                "snapshotBlobPath TEXT, draftBlobPath TEXT, verifiedReportBlobPath TEXT, errorCodes TEXT NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS r8_memories ("+
                "id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, kind TEXT NOT NULL, trust TEXT NOT NULL, "+
                "sourceRef TEXT NOT NULL, contentHash TEXT NOT NULL, encryptedValue TEXT NOT NULL, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, "+
                "FOREIGN KEY(taskId) REFERENCES r7_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_r8_memories_taskId_kind_contentHash ON r8_memories(taskId,kind,contentHash)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_r8_memories_taskId ON r8_memories(taskId)");
            db.execSQL("CREATE TABLE IF NOT EXISTS r8_evidence ("+
                "id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, sourceSha256 TEXT NOT NULL, locator TEXT NOT NULL, "+
                "quoteHash TEXT NOT NULL, encryptedQuote TEXT NOT NULL, verificationState TEXT NOT NULL, evidenceGroupId TEXT NOT NULL, "+
                "independenceState TEXT NOT NULL, updatedAt INTEGER NOT NULL, "+
                "FOREIGN KEY(taskId) REFERENCES r7_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_r8_evidence_taskId_sourceSha256_locator_quoteHash ON r8_evidence(taskId,sourceSha256,locator,quoteHash)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_r8_evidence_taskId ON r8_evidence(taskId)");
        }
    };
    public static R7VaultDatabase get(Context ctx){
        if(INSTANCE==null){synchronized(R7VaultDatabase.class){if(INSTANCE==null){
            INSTANCE=Room.databaseBuilder(ctx.getApplicationContext(),R7VaultDatabase.class,"r7_vault.sqlite")
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2).build();
        }}}
        return INSTANCE;
    }
}
