package com.email.email_writer.Serrvice;

import com.email.email_writer.Configure.EmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class EmailGeneratorService {

    public EmailGeneratorService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;
    public String generateEmailReply(EmailRequest emailRequest){
        //Build the prompt
        String prompt = buildprompt(emailRequest);


        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Prompt is empty");
        }


        System.out.println("PROMPT: " + prompt);
        //Craft a request
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
        System.out.println("REQUEST BODY: " + requestBody);

        //Do req and response
        String response = webClient.post()
                .uri(geminiApiUrl)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(errorBody -> new RuntimeException("Gemini Error: " + errorBody))
                )
                .bodyToMono(String.class)
                .block();

        System.out.println("Gemini raw response: " + response);


        System.out.println("RAW RESPONSE: " + response);System.out.println("RAW RESPONSE: " + response);
        //extract response and,
        //response return:

        return extractResponseContent(response);

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
            return "Error Processing Request: " + e.getMessage();
        }
    }

    private String buildprompt(EmailRequest emailRequest) {
        StringBuilder prompt=new StringBuilder();
        prompt.append("Generate a professional email reply for the following email content.  Please don't generate  a subject line ");
        if (emailRequest.getTone()!=null &&!emailRequest.getTone().isEmpty()){
            prompt.append("Use a ").append(emailRequest.getTone()).append("tone. ");
        }
        prompt.append("\nOriginal EMail: \n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }


}
