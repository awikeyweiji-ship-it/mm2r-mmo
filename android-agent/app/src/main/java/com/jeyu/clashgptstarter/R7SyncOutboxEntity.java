package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName="r7_sync_outbox",indices={@Index("projectId")})
public final class R7SyncOutboxEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String projectId = "";
    @NonNull public String encryptedPayload = "";
    @NonNull public String type = "";
    public long createdAt;
    public long acknowledgedAt;
    public int retryCount;
}
