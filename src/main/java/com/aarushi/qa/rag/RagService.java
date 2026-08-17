package com.aarushi.qa.rag;

import com.aarushi.qa.dto.AskResponse;
import com.aarushi.qa.dto.Citation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagService {

    private static final String REFUSAL =
            "I don't have enough information in the provided documents.";

    private final VectorStore store;
    private final ChatClient chat;
    private final double threshold;
    private final int topK;

    public RagService(
            VectorStore store,
            ChatClient.Builder builder,
            Environment env) {

        this.store = store;
        this.chat = builder.build();
        this.threshold = Double.parseDouble(
                env.getProperty("rag.similarity-threshold", "0.70"));
        this.topK = Integer.parseInt(
                env.getProperty("rag.top-k", "5"));
    }

    public AskResponse ask(String tenant, String question) {

        List<Document> docs = retrieve(tenant, question);

        if (docs.isEmpty()) {
            return new AskResponse(REFUSAL, true, List.of());
        }

        String context = buildContext(docs);

        String systemPrompt = """
                You are a grounded document Q&A assistant.

                Answer ONLY from the provided CONTEXT.
                If the context does not contain enough information to answer
                the question, respond exactly:
                "I don't have enough information in the provided documents."

                Never use outside knowledge.
                Cite claims using [Source N].
                """;

        String userPrompt = """
                CONTEXT:

                %s

                QUESTION:
                %s
                """.formatted(context, question);

        String answer = chat.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        boolean refused = answer == null
                || answer.isBlank()
                || answer.trim().equals(REFUSAL);

        if (answer == null || answer.isBlank()) {
            answer = REFUSAL;
        }

        return new AskResponse(answer, refused, citations(docs));
    }

    public List<Document> retrieve(String tenant, String question) {

        FilterExpressionBuilder builder =
                new FilterExpressionBuilder();

        var tenantFilter = builder
                .eq("tenantId", tenant)
                .build();

        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(tenantFilter)
                .build();

        return store.similaritySearch(request);
    }

    private String buildContext(List<Document> docs) {

        StringBuilder context = new StringBuilder();

        for (int i = 0; i < docs.size(); i++) {

            Document document = docs.get(i);

            context.append("[Source ")
                    .append(i + 1)
                    .append("]\n");

            context.append("Filename: ")
                    .append(document.getMetadata()
                            .getOrDefault("filename", "unknown"))
                    .append("\n");

            context.append("Page: ")
                    .append(document.getMetadata()
                            .getOrDefault("pageNumber", "unknown"))
                    .append("\n");

            context.append("Content:\n")
                    .append(document.getText())
                    .append("\n\n");
        }

        return context.toString();
    }

    private List<Citation> citations(List<Document> docs) {

        List<Citation> result = new ArrayList<>();

        for (Document document : docs) {

            Object score = document.getScore();

            result.add(new Citation(
                    String.valueOf(document.getMetadata()
                            .get("documentId")),
                    String.valueOf(document.getMetadata()
                            .get("filename")),
                    number(document.getMetadata()
                            .get("pageNumber")),
                    number(document.getMetadata()
                            .get("chunkIndex")),
                    score == null ? 0.0 : ((Number) score).doubleValue()
            ));
        }

        return result;
    }

    private Integer number(Object value) {
        if (value == null) {
            return null;
        }

        return Integer.valueOf(String.valueOf(value));
    }
}
