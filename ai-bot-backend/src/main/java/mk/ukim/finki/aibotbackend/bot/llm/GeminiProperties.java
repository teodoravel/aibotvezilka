package mk.ukim.finki.aibotbackend.bot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the Gemini API, bound from the {@code gemini.*} properties.
 *
 * @param baseUrl the base URL of the Gemini REST API
 * @param apiKey  the API key from Google AI Studio
 * @param model   the model that makes the decisions, e.g. {@code gemini-3.5-flash-lite}
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
    String baseUrl,
    String apiKey,
    String model
) {
}
