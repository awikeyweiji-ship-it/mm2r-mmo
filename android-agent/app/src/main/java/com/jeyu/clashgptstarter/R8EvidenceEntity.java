package com.jeyu.clashgptstarter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Evidence is tracked separately from model prose; independence is never inferred from quote count. */
@Entity(tableName="r8_evidence",indices={
        @Index("taskId"),
        @Index(value={"taskId","sourceSha256","locator","quoteHash"},unique=true)
})
public final class R8EvidenceEntity {
    @PrimaryKey @NonNull public String id="";
    @NonNull public String taskId="";
    @NonNull public String sourceSha256="";
    @NonNull public String locator="";
    @NonNull public String quoteHash="";
    @NonNull public String encryptedQuote="";
    @NonNull public String verificationState="UNKNOWN";
    @NonNull public String evidenceGroupId="";
    @NonNull public String independenceState="UNKNOWN";
    public long updatedAt;
}
