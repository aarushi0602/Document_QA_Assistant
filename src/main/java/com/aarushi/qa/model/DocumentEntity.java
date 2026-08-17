package com.aarushi.qa.model;
import java.time.OffsetDateTime; import java.util.UUID;
public record DocumentEntity(UUID id,String tenantId,String filename,String contentType,
 IngestionStatus status,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}