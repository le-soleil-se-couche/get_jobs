package ai;

import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

public class EmbeddingUtil {
    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-ada-002";
    private static final String API_KEY = Dotenv.load().get("API_KEY");

    public static List<Double> getEmbedding(String input) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("input", input);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EMBEDDING_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JSONArray vectorArray = new JSONObject(response.body())
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONArray("embedding");

            return vectorArray.toList().stream()
                    .map(x -> ((Number) x).doubleValue())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of(); // 返回空向量
    }
}
