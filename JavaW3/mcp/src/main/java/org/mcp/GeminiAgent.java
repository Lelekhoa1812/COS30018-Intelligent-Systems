package org.mcp;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class GeminiAgent {
    private final Client client;
    private final String model;

    public GeminiAgent(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_API_KEY is missing (set it in your environment).");
        }
        // The Client automatically picks up GEMINI_API_KEY / GOOGLE_API_KEY
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = (model == null || model.isBlank())
                ? "gemini-2.5-flash"
                : model;
    }

    public static GeminiAgent fromEnv() {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        String chosenModel = System.getProperty("GEMINI_MODEL", "gemini-2.5-flash");
        return new GeminiAgent(apiKey, chosenModel);
    }

    public synchronized String complete(String prompt) {
        // options argument can be null if not used
        GenerateContentResponse resp = client.models.generateContent(model, prompt, null);

        // Simplest way: use response.text()
        String text = resp.text();
        return (text != null && !text.isBlank()) ? text : "(no response)";
    }
}
