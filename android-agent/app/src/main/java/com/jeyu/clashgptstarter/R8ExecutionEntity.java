package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Durable execution snapshot kept separate from the user-facing task row. */
@Entity(tableName="r8_executions")
public final class R8ExecutionEntity {
    @PrimaryKey @NonNull public String taskId="";
    @NonNull public String executionState="CREATED";
    @NonNull public String contextState="SAFE";
    @NonNull public String dataState="LOCAL_ONLY";
    @NonNull public String ruleState="UNKNOWN";
    @NonNull public String reasoningState="NOT_EVALUATED";
    @NonNull public String qualityState="NOT_EVALUATED";
    @NonNull public String modelId="";
    @NonNull public String modelPolicy="STRICT";
    @NonNull public String ruleVersion="";
    @NonNull public String encryptedRules="";
    public boolean remoteDataConsent;
    public int contextEpoch;
    public int completedUnits;
    public int totalUnits;
    public int lastEventSeq;
    public String remoteCheckpointId;
    public String remoteRunId;
    public String snapshotBlobPath;
    public String draftBlobPath;
    public String verifiedReportBlobPath;
    @NonNull public String errorCodes="";
    public long updatedAt;
}
