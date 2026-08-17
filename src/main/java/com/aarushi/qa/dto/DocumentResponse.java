package com.aarushi.qa.dto;
import com.aarushi.qa.model.IngestionStatus; import java.time.OffsetDateTime; import java.util.UUID;
public record DocumentResponse(UUID id,String tenantId,String filename,IngestionStatus status,OffsetDateTime createdAt) {}