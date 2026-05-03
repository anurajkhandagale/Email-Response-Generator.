package com.email.email_writer.Service;

import com.email.email_writer.Configure.EmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    public EmailGeneratorService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public String generateEmailReply(EmailRequest emailRequest) {

        String prompt = buildPrompt(emailRequest);

        if (prompt == null || prompt.isBlank()) {
            return "Prompt is empty";
        }

        Map<String, Object> requestBody = Map.of(
                "contents", java.util.List.of(
                        Map.of(
                                "parts", java.util.List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", 500
                )
        );

        try {

            String response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()

                    // ✅ Correct error handling
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.out.println("Gemini API ERROR: " + errorBody);
                                        return reactor.core.publisher.Mono.error(
                                                new RuntimeException("Gemini Error: " + errorBody)
                                        );
                                    })
                    )

                    .bodyToMono(String.class)
                    .block();

            System.out.println("Gemini raw response: " + response);

            return extractResponseContent(response);

        } catch (Exception e) {
            e.printStackTrace();
            return "Gemini API failed: " + e.getMessage();
        }
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);

            JsonNode candidates = rootNode.path("candidates");

            if (candidates.isArray() && candidates.size() > 0) {
                return candidates.get(0)
                        .path("content")
                        .path("parts")
                        .get(0)
                        .path("text")
                        .asText();
            }

            return "No response from Gemini";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error Processing Response: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a professional email reply for the following email content. ");
        prompt.append("Do not include a subject line.\n\n");

        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone.\n\n");
        }

        prompt.append("Original Email:\n");
        prompt.append(emailRequest.getEmailContent());

        return prompt.toString();
    }
}