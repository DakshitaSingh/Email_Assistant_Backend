package com.email.writer.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;

    // Safely handle missing env vars
    @Value("${GEMINI_API_KEY:#{null}}")
    private String geminiApiKey;

    @Value("${GEMINI_API_URL:#{null}}")
    private String geminiApiUrl;

    public EmailGeneratorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // Log environment variable values at startup
    @PostConstruct
    public void debugEnvVars() {
        System.out.println("🔍 @Value GEMINI_API_KEY: " + geminiApiKey);
        System.out.println("🔍 @Value GEMINI_API_URL: " + geminiApiUrl);

        System.out.println("🧪 System.getenv GEMINI_API_KEY: " + System.getenv("GEMINI_API_KEY"));
        System.out.println("🧪 System.getenv GEMINI_API_URL: " + System.getenv("GEMINI_API_URL"));

        // Fallback or crash early if required vars are missing
        if (geminiApiUrl == null || geminiApiKey == null) {
            System.err.println("❌ Required environment variables are missing!");
            throw new IllegalStateException("Environment variables GEMINI_API_URL and GEMINI_API_KEY must be set.");
        }
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        String prompt = buildPrompt(emailRequest);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );

        String response = webClient.post()
                .uri(geminiApiUrl)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey) // ✅ in case needed for actual Gemini API
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            return rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            return "Error processing request: " + e.getMessage();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a professional email reply for the following email content. Please don't generate a subject line. ");
        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
        }
        prompt.append("\nOriginal email:\n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}
