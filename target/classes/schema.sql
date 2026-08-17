CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS documents (
 id UUID PRIMARY KEY, tenant_id VARCHAR(100) NOT NULL, filename VARCHAR(500) NOT NULL,
 content_type VARCHAR(150), status VARCHAR(30) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_documents_tenant ON documents(tenant_id);
CREATE TABLE IF NOT EXISTS document_chunks (
 id UUID PRIMARY KEY, document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 tenant_id VARCHAR(100) NOT NULL, chunk_index INTEGER NOT NULL, content TEXT NOT NULL,
 page_number INTEGER, metadata JSONB, embedding vector(768)
);
CREATE INDEX IF NOT EXISTS idx_chunks_tenant ON document_chunks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
 ON document_chunks USING hnsw (embedding vector_cosine_ops);
