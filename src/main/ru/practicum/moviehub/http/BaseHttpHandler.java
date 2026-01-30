package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();

    protected int getCurrentYear() {
        return Year.now().getValue();
    }

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    protected <T> T parseRequestBody(HttpExchange ex, Class<T> tClass) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.trim().isEmpty()) return null;

        try (JsonReader jsonReader = new JsonReader(new StringReader(body))) {
            jsonReader.setLenient(false);
            JsonElement jsonElement = JsonParser.parseReader(jsonReader);

            if (!jsonElement.isJsonObject()) return null;

            JsonObject obj = jsonElement.getAsJsonObject();
            if (!obj.has("title") || !obj.has("releaseYear")) return null;

            return gson.fromJson(jsonElement, tClass);
        } catch (Exception e) {
            return null;
        }
    }

    protected String getQueryParam(String query, String param) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && param.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    protected boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        return str.chars().allMatch(Character::isDigit);
    }
}