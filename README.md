# Document Q&A Assistant (RAG)

A backend service that ingests PDF documents and answers natural-language
questions about them, grounded in retrieved content with citations back to
source document and page. Built for the take-home assignment brief
(Document Q&A Assistant — Engineering Assignment, v1.1).

## 1. How to run it

### Prerequisites
- Docker Desktop running
- Java 21+
- Maven (wrapper committed — `./mvnw` also works)
- [Ollama](https://ollama.com) installed locally, with two models pulled:
  ```bash
  ollama pull llama3.2
  ollama pull nomic-embed-text
  ```

> **Known gap:** `docker compose up` currently starts Postgres only, not the
> app itself — see Known Limitations. Until that's fixed, run the two steps
> below.

### Steps

1. **Start Postgres:**
   ```bash
   docker compose up -d
   ```
2. **Copy `.env.example` to `.env`** and confirm the Ollama values match
   your local setup (defaults should work if you used the model names
   above):
   ```
   OLLAMA_BASE_URL=http://localhost:11434
   OLLAMA_CHAT_MODEL=llama3.2
   OLLAMA_EMBEDDING_MODEL=nomic-embed-text
   DB_URL=jdbc:postgresql://localhost:5433/documentqa
   DB_USERNAME=postgres
   DB_PASSWORD=postgres
   RAG_TOP_K=5
   RAG_SIMILARITY_THRESHOLD=0.5
   ```
3. **Load env vars and run** (PowerShell):
   ```powershell
   Get-Content .env | ForEach-Object {
     if ($_ -match '^\s*([^#=][^=]*)=(.*)$') {
       [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
     }
   }
   mvn clean spring-boot:run
   ```
   (macOS/Linux: `export $(cat .env | xargs) && mvn spring-boot:run`)
4. **Upload a document:**
   ```bash
   curl -X POST "http://localhost:8081/api/documents?tenantId=tenant-a" \
     -F "file=@yourfile.pdf"
   ```
5. **Poll status until READY:**
   ```bash
   curl "http://localhost:8081/api/documents/{id}?tenantId=tenant-a"
   ```
6. **Ask a question:**
   ```bash
   curl -X POST http://localhost:8081/api/qa \
     -H "Content-Type: application/json" \
     -d '{"tenantId":"tenant-a","question":"..."}'
   ```

## 2. Architecture

**Ingestion path:**
`POST /api/documents` -> `DocumentService` validates (PDF only) and inserts a
`documents` row with status `PROCESSING`, returns `202` immediately ->
`DocumentIngestionService.ingestAsync` runs on a dedicated executor
(`@Async("ingestionExecutor")`), so upload requests never block on
extraction or embedding -> PDFBox extracts text **page by page** (page
number is captured before chunking, so it survives into every chunk's
metadata) -> `TokenTextSplitter` chunks each page -> `VectorStore.add()`
embeds the chunks (via Ollama's `nomic-embed-text`) and writes them to
Postgres/pgvector in one call -> status flips to `READY` or `FAILED`.

**Query path:**
`POST /api/qa` -> `RagService` builds a `FilterExpression` scoping the
vector search to the requesting tenant (`tenantId == '<tenant>'`) ->
`VectorStore.similaritySearch()` runs the embedding + cosine-similarity
search **as a single SQL query in Postgres**, with the tenant filter
applied in that same query (not filtered afterward in Java) -> if no chunk
clears `rag.similarity-threshold`, the request returns the fixed refusal
string immediately, without calling the LLM -> otherwise, retrieved chunks
are assembled into a `CONTEXT:` block and sent to the model with a system
prompt instructing it to answer only from that context -> response includes
the answer plus a `citations` array (document ID, filename, page, chunk
index, similarity score) for every retrieved chunk.

## 3. Chunking strategy

`TokenTextSplitter` (Spring AI's token-aware splitter), applied **per page**
rather than to the whole document at once — this is deliberate: chunking
after page extraction means every chunk inherits a single, correct page
number, instead of chunks straddling page boundaries and losing that
traceability.

Parameters:
- `chunkSize = 500` tokens
- `minChunkSizeChars = 100`
- `minChunkLengthToEmbed = 5`
- `maxNumChunks = 10000`
- `keepSeparator = true`

500 tokens keeps each chunk small enough to stay topically focused (a
single fee table or a single FAQ answer typically fits in one chunk) while
staying well under the context window, leaving room for `top-k=5` chunks
plus the system prompt without truncation.

## 4. Embedding model, dimensions, and cost

- **Model:** `nomic-embed-text` via Ollama (local)
- **Dimensions:** 768
- **Cost per 1000 pages:** $0 — runs locally, no API billing. (Originally
  built against Gemini's `gemini-embedding-001`, also 768-dim; switched to
  Ollama during development after repeated API key validation issues with
  the Gemini Developer API — see "one thing that surprised me" below.)

## 5. Similarity threshold

`rag.similarity-threshold = 0.5` (configurable via `RAG_SIMILARITY_THRESHOLD`).

Arrived at by testing against a small question set on the sample admission-
guidelines PDF:
- A directly-answered question ("What is the eligibility criteria for
  admission to BE course of IET-DAVV?") retrieved its best matching chunk
  at **0.665** cosine similarity.
- An out-of-scope question ("What is the capital of France?") retrieved no
  chunks above [FILL IN: the score you observed once you re-test at 0.5].

0.5 was chosen as a threshold with margin below the real match (0.665)
while still being restrictive enough to reject clearly unrelated queries.
**This needs a slightly larger test set than one question per case to be
fully confident** — see Known Limitations.

## 6. Known limitations and what I'd do with two more weeks

Being upfront about gaps rather than leaving them for you to find:

- **Migrations:** currently uses `schema.sql` (Postgres init script) plus
  Spring AI's `initialize-schema: true` for the vector table, not Flyway or
  Liquibase. This does not meet the stated "no ddl-auto=update" constraint
  in spirit. **Highest-priority fix** — would replace with versioned Flyway
  migrations first.
- **`document_chunks` table is unused.** The schema defines a typed table
  (`tenant_id`, `document_id`, `page_number` as real columns, with a
  cascade-delete FK) that was intended to back tenant isolation with
  referential integrity. In practice, Spring AI's `PgVectorStore`
  auto-configuration writes to its own default `vector_store` table
  instead, so tenant scoping is enforced only via a metadata JSONB filter,
  not the FK-backed schema. Two weeks: I'd point `PgVectorStore` at the
  custom table explicitly (or write a custom insert path) so isolation is
  backed by the schema, not just query-time filtering.
- **`docker compose up` does not start the app**, only Postgres — violates
  the "no manual steps" run requirement. Would add the app as a compose
  service with a build context and a `depends_on` health check.
- **No document management endpoints:** `GET /api/documents` (list/
  pagination) and `DELETE /api/documents/{id}` are not implemented.
- **No category filtering:** `AskRequest` doesn't expose a `category`
  field, and retrieval doesn't filter on it, despite the schema allowing
  for it.
- **No conversation memory:** `conversationId` isn't accepted or persisted;
  every question is stateless. Follow-up questions like "what about for
  class 9?" won't work.
- **Streaming is not real token streaming.** `/api/qa/stream` currently
  calls the model synchronously, then chops the final answer into
  space-separated tokens and drips them out with a fixed delay — it is not
  wired to the model's actual token stream, and there's no cancellation on
  client disconnect.
- **Tenant identifier is inconsistent across endpoints** — `DocumentController`
  reads it from an `X-Tenant-Id` header; `QuestionController` reads it from
  the request body. Two weeks: standardize on the header everywhere per
  FR-8.
- **No resilience layer:** no retry/backoff or circuit breaker around model
  calls; a dead Ollama instance currently surfaces as a raw 500, not a
  clean 503.
- **Tests are currently placeholders**, not real assertions against the
  system (e.g. `assertTrue(0.41 < 0.70)`) — they compile and pass but don't
  exercise real code paths. No Testcontainers integration tests yet. Two
  weeks: real Testcontainers tests against a live pgvector container,
  covering tenant isolation and the refusal path specifically, per the
  brief's testing requirements.
- **No observability beyond default Actuator health:** no correlation IDs
  threaded through the async ingestion path, no token/cost metrics.
