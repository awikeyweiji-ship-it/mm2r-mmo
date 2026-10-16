package com.jeyu.clashgptstarter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface R7VaultDao {
    @Insert(onConflict=OnConflictStrategy.ABORT) void addProject(R7ProjectEntity p);
    @Insert(onConflict=OnConflictStrategy.IGNORE) long addSource(R7SourceEntity source);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putTask(R7TaskEntity task);
    @Insert(onConflict=OnConflictStrategy.IGNORE) void addEvent(R7EventEntity event);
    @Insert(onConflict=OnConflictStrategy.IGNORE) void addOutbox(R7SyncOutboxEntity op);
    @Update void updateProject(R7ProjectEntity p);
    @Update void updateSource(R7SourceEntity s);
    @Update void updateTask(R7TaskEntity t);
    @Query("SELECT * FROM r7_projects ORDER BY updatedAt DESC") List<R7ProjectEntity> projects();
    @Query("SELECT * FROM r7_projects WHERE id=:id LIMIT 1") R7ProjectEntity project(String id);
    @Query("SELECT * FROM r7_sources WHERE projectId=:projectId ORDER BY importedAt DESC") List<R7SourceEntity> sources(String projectId);
    @Query("SELECT * FROM r7_sources WHERE projectId=:projectId AND sha256=:sha LIMIT 1") R7SourceEntity findSource(String projectId,String sha);
    @Query("SELECT * FROM r7_tasks WHERE projectId=:projectId ORDER BY updatedAt DESC") List<R7TaskEntity> tasks(String projectId);
    @Query("SELECT * FROM r7_events WHERE taskId=:taskId ORDER BY sequence") List<R7EventEntity> events(String taskId);
    @Query("SELECT * FROM r7_sync_outbox WHERE acknowledgedAt=0 ORDER BY createdAt") List<R7SyncOutboxEntity> pendingOutbox();
    @Query("SELECT COUNT(*) FROM r7_sources WHERE projectId=:projectId") int sourceCount(String projectId);

    @Insert(onConflict=OnConflictStrategy.REPLACE) void putR8Execution(R8ExecutionEntity e);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putR8Memory(R8MemoryEntity e);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putR8Evidence(R8EvidenceEntity e);
    @Query("SELECT * FROM r8_executions WHERE taskId=:taskId LIMIT 1") R8ExecutionEntity r8Execution(String taskId);
    @Query("SELECT * FROM r8_memories WHERE taskId=:taskId AND trust='VERIFIED' ORDER BY updatedAt DESC") List<R8MemoryEntity> trustedMemories(String taskId);
    @Query("SELECT * FROM r8_evidence WHERE taskId=:taskId ORDER BY updatedAt DESC") List<R8EvidenceEntity> r8Evidence(String taskId);
}
