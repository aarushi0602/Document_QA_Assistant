package com.aarushi.qa.repository;
import com.aarushi.qa.model.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository;
import java.sql.ResultSet; import java.time.OffsetDateTime; import java.util.Optional; import java.util.UUID;
@Repository public class DocumentRepository {
 private final JdbcTemplate jdbc; public DocumentRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void insert(DocumentEntity d){jdbc.update("INSERT INTO documents(id,tenant_id,filename,content_type,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
 d.id(),d.tenantId(),d.filename(),d.contentType(),d.status().name(),d.createdAt(),d.updatedAt());}
 public Optional<DocumentEntity> find(UUID id,String tenant){return jdbc.query("SELECT id,tenant_id,filename,content_type,status,created_at,updated_at FROM documents WHERE id=? AND tenant_id=?",
 rs->rs.next()?Optional.of(map(rs)):Optional.empty(),id,tenant);}
 public void updateStatus(UUID id,IngestionStatus s){jdbc.update("UPDATE documents SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",s.name(),id);}
 private DocumentEntity map(ResultSet r)throws java.sql.SQLException{return new DocumentEntity(r.getObject("id",UUID.class),r.getString("tenant_id"),r.getString("filename"),r.getString("content_type"),IngestionStatus.valueOf(r.getString("status")),r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class));}
}