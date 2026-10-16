package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Only VERIFIED memory is eligible for automatic carry-over into a later context window. */
@Entity(tableName="r8_memories",indices={@Index("taskId"),@Index(value={"taskId","kind","contentHash"},unique=true)})
public final class R8MemoryEntity {
    @PrimaryKey @NonNull public String id="";
    @NonNull public String taskId="";
    @NonNull public String kind="FACT";
    @NonNull public String trust="UNVERIFIED";
    @NonNull public String sourceRef="";
    @NonNull public String contentHash="";
    @NonNull public String encryptedValue="";
    public int version;
    public long updatedAt;
}
