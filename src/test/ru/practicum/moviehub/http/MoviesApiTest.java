package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    @BeforeAll
    static void beforeAll() {

    }

    @BeforeEach
    void beforeEach() {

    }

    @AfterAll
    static void afterAll() {

    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        MoviesServer server = new MoviesServer();
        server.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        server.stop();

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку" );

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");

        server.stop();
    }
}