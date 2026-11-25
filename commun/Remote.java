/**
 * Remote.java
 *
 * Minimal HTTP/JSON client used by the storage server to talk to another
 * server (the naming server for /register, another storage server for copy).
 */
package commun;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class Remote {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private Remote() {
    }

    /**
     * Sends a POST with the body serialized as JSON.
     *
     * @throws IOException if the server cannot be reached or the call is interrupted.
     */
    public static HttpResponse<String> post(String ip, int port, String endpoint, Object body)
            throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":" + port + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + endpoint, e);
        }
    }
}
