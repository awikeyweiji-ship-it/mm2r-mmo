package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName="r7_sources",indices={@Index(value={"projectId","sha256"},unique=true),@Index("projectId")})
public final class R7SourceEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String projectId = "";
    @NonNull public String sha256 = "";
    @NonNull public String encryptedFilename = "";
    @NonNull public String mime = "application/octet-stream";
    public long sizeBytes;
    public long importedAt;
    public boolean sharedWithAgent;
    public String remoteSourceId;
}
