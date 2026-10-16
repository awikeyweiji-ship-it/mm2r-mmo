package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName="r7_tasks",indices={@Index("projectId")})
public final class R7TaskEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String projectId = "";
    @NonNull public String encryptedGoal = "";
    @NonNull public String status = "CREATED_LOCAL";
    public int step;
    public String remoteCheckpointId;
    public String remoteTaskId;
    public long updatedAt;
}
