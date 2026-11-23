package storage;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import jsonhelper.ExceptionReturn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpUtil {
    private static final Gson GSON = new Gson();
    private HttpUtil() {}
        public static boolean requirePost(HttpExchange exchange)throws IOException {
            if("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                return true;
            }
            sendError(exchange,405, "IllegalArgumentException", "Only POST is supported.");
            return false;
    }

    public static <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
    public static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
    public static void sendError(HttpExchange exchange, int status,String type, String info) throws IOException {
            sendJson(exchange,status,new ExceptionReturn(type,info));
    }
    public static void sendBadRequest(HttpExchange exchange) throws IOException {
        sendError(exchange, 400, "IllegalArgumentException", "Invalid request data.");
    }

}
