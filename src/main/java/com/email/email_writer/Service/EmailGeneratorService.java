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

    // 🔑 OpenRouter API Key
    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public String generateEmailReply(EmailRequest emailRequest) {

        if (apiKey == null || apiKey.isBlank()) {
            return "API key missing (check Render environment)";
        }

        String prompt = buildPrompt(emailRequest);

        if (prompt == null || prompt.isBlank()) {
            return "Prompt is empty";
        }

        try {

            String response = webClient.post()
                    .uri("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://your-app.onrender.com")
                    .header("X-Title", "Email Generator App")
                    .bodyValue(Map.of(
                            "model", "openrouter/auto",
                            "messages", java.util.List.of(
                                    Map.of("role", "user", "content", prompt)
                            )
                    ))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.out.println("OpenRouter ERROR: " + errorBody);
                                        return reactor.core.publisher.Mono.error(
                                                new RuntimeException("OpenRouter Error: " + errorBody)
                                        );
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();

            System.out.println("RAW RESPONSE: " + response);

            return extractResponseContent(response);

        } catch (Exception e) {
            e.printStackTrace();
            return "API failed: " + e.getMessage();
        }
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            return root.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing response: " + e.getMessage();
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