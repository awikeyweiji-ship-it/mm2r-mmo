package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName="r7_events",indices={@Index("taskId"),@Index(value={"taskId","sequence"},unique=true)})
public final class R7EventEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String taskId = "";
    public int sequence;
    @NonNull public String type = "";
    @NonNull public String encryptedPayload = "";
    public long timestamp;
}
