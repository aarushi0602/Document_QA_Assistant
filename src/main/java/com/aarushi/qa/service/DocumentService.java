package com.aarushi.qa.service;
import com.aarushi.qa.dto.DocumentResponse; import com.aarushi.qa.model.*; import com.aarushi.qa.repository.DocumentRepository;
import org.springframework.stereotype.Service; import org.springframework.web.multipart.MultipartFile; import java.io.IOException; import java.time.OffsetDateTime; import java.util.UUID;
@Service public class DocumentService {
 private final DocumentRepository repo; private final DocumentIngestionService ingestion;
 public DocumentService(DocumentRepository repo,DocumentIngestionService ingestion){this.repo=repo;this.ingestion=ingestion;}
 public DocumentResponse upload(String tenant,MultipartFile file)throws IOException{
  if(file.isEmpty())throw new IllegalArgumentException("File is empty");
  String name=file.getOriginalFilename(); if(name==null||!name.toLowerCase().endsWith(".pdf"))throw new IllegalArgumentException("Only PDF files are supported");
  UUID id=UUID.randomUUID(); var now=OffsetDateTime.now(); var d=new DocumentEntity(id,tenant,name,file.getContentType(),IngestionStatus.PROCESSING,now,now);
  repo.insert(d); ingestion.ingestAsync(d,file.getBytes()); return toResponse(d);
 }
 public DocumentResponse get(UUID id,String tenant){return repo.find(id,tenant).map(this::toResponse).orElseThrow(()->new IllegalArgumentException("Document not found"));}
 private DocumentResponse toResponse(DocumentEntity d){return new DocumentResponse(d.id(),d.tenantId(),d.filename(),d.status(),d.createdAt());}
}