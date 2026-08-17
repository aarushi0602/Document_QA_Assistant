package com.aarushi.qa.service;

import com.aarushi.qa.model.DocumentEntity;
import com.aarushi.qa.model.IngestionStatus;
import com.aarushi.qa.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentRepository repo;

    public DocumentIngestionService(
            VectorStore vectorStore,
            DocumentRepository repo) {

        this.vectorStore = vectorStore;
        this.repo = repo;
    }

    @Async("ingestionExecutor")
    public void ingestAsync(DocumentEntity source, byte[] bytes) {

        try {

            // 1. Extract PDF text and create chunks
            List<Document> chunks = extractAndChunk(source, bytes);

            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "No readable text was found in the document"
                );
            }

            System.out.println(
                    "Ingestion: extracted " + chunks.size() + " chunks"
            );

            // 2. Generate embeddings + store in pgvector
            System.out.println("Ingestion: storing chunks in vector store");

            vectorStore.add(chunks);

            System.out.println(
                    "Ingestion: successfully stored " + chunks.size() + " chunks"
            );

            // 3. Mark document READY
            repo.updateStatus(
                    source.id(),
                    IngestionStatus.READY
            );

            System.out.println(
                    "Ingestion: document " + source.id() + " READY"
            );

        } catch (Exception e) {

            // VERY IMPORTANT:
            // Don't hide the real exception while debugging.
            System.err.println(
                    "================================================"
            );
            System.err.println(
                    "DOCUMENT INGESTION FAILED"
            );
            System.err.println(
                    "Document ID: " + source.id()
            );
            System.err.println(
                    "Filename: " + source.filename()
            );
            System.err.println(
                    "Error: " + e.getMessage()
            );
            System.err.println(
                    "================================================"
            );

            e.printStackTrace();

            repo.updateStatus(
                    source.id(),
                    IngestionStatus.FAILED
            );
        }
    }

    private List<Document> extractAndChunk(
            DocumentEntity source,
            byte[] bytes) throws Exception {

        List<Document> pages = new ArrayList<>();

        // ---------------------------------------------------------
        // STEP 1: Extract text page by page
        // ---------------------------------------------------------

        try (PDDocument pdf = Loader.loadPDF(bytes)) {

            PDFTextStripper stripper = new PDFTextStripper();

            for (int pageNumber = 1;
                 pageNumber <= pdf.getNumberOfPages();
                 pageNumber++) {

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);

                String text = stripper
                        .getText(pdf)
                        .trim();

                if (!text.isBlank()) {

                    Map<String, Object> metadata = Map.of(
                            "tenantId", source.tenantId(),
                            "documentId", source.id().toString(),
                            "filename", source.filename(),
                            "pageNumber", pageNumber
                    );

                    pages.add(
                            new Document(text, metadata)
                    );
                }
            }
        }

        if (pages.isEmpty()) {
            throw new IllegalStateException(
                    "PDF contains no extractable text"
            );
        }

        // ---------------------------------------------------------
        // STEP 2: Chunk
        // Spring AI 2.x builder API
        // ---------------------------------------------------------

        TokenTextSplitter splitter =
                TokenTextSplitter.builder()
                        .withChunkSize(500)
                        .withMinChunkSizeChars(100)
                        .withMinChunkLengthToEmbed(5)
                        .withMaxNumChunks(10000)
                        .withKeepSeparator(true)
                        .build();

        List<Document> output = new ArrayList<>();

        int chunkIndex = 0;

        for (Document page : pages) {

            List<Document> pageChunks =
                    splitter.split(List.of(page));

            for (Document chunk : pageChunks) {

                chunk.getMetadata().put(
                        "tenantId",
                        source.tenantId()
                );

                chunk.getMetadata().put(
                        "documentId",
                        source.id().toString()
                );

                chunk.getMetadata().put(
                        "filename",
                        source.filename()
                );

                chunk.getMetadata().put(
                        "pageNumber",
                        page.getMetadata().get("pageNumber")
                );

                chunk.getMetadata().put(
                        "chunkIndex",
                        chunkIndex++
                );

                output.add(chunk);
            }
        }

        return output;
    }
}