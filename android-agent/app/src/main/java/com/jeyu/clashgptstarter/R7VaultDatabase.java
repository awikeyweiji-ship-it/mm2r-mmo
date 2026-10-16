package com.jeyu.clashgptstarter;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** Durable local metadata. Large bytes stored encrypted separately by SHA256. */
@Database(entities={R7ProjectEntity.class,R7SourceEntity.class,R7TaskEntity.class,
       R7EventEntity.class,R7SyncOutboxEntity.class},version=1,exportSchema=false)
public abstract class R7VaultDatabase extends RoomDatabase {
    private static volatile R7VaultDatabase INSTANCE;
    public abstract R7VaultDao dao();
    public static R7VaultDatabase get(Context ctx){
        if(INSTANCE==null){synchronized(R7VaultDatabase.class){if(INSTANCE==null){
            INSTANCE=Room.databaseBuilder(ctx.getApplicationContext(),R7VaultDatabase.class,"r7_vault.sqlite")
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build();
        }}}
        return INSTANCE;
    }
}
