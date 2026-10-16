package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Title ciphertext is stored, not plaintext. Project id can match cross-device cloud id. */
@Entity(tableName="r7_projects")
public final class R7ProjectEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String encryptedTitle = "";
    public long createdAt;
    public long updatedAt;
    public int remoteRevision;
    public boolean localDirty = true;
}
