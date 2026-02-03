package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static MoviesServer server;
    private static HttpClient client;
    private static final String BASE = "http://localhost:8080";
    private static final String CT_JSON = "application/json; charset=UTF-8";
    private static final Gson GSON = new Gson();
    private static final ListOfMoviesTypeToken FILM_TOKEN = new ListOfMoviesTypeToken();
    private static MoviesStore store;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> movieGetRequest(String path) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Accept", CT_JSON)
                .GET()
                .build();
        return sendRequest(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> moviePostRequest(String json) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return sendRequest(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<Void> deleteByIdMovieRequest(int id) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .DELETE()
                .build();
        return sendRequest(request, HttpResponse.BodyHandlers.discarding());
    }

    private <T> HttpResponse<T> sendRequest(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ошибка при выполнении HTTP-запроса", e);
        }
    }

    private ErrorResponse parseError(HttpResponse<String> response) {
        return GSON.fromJson(response.body(), ErrorResponse.class);
    }

    private String createMovieJson(String title, int releaseYear, String genre) {
        JsonObject json = new JsonObject();
        json.addProperty("title", title);
        json.addProperty("releaseYear", releaseYear);
        json.addProperty("genre", genre);
        return json.toString();
    }

    private String createMovieJson(String title, int releaseYear) {
        return createMovieJson(title, releaseYear, "Unknown");
    }

    @Test
    @DisplayName("GET /movies — возвращает пустой массив, если фильмов нет")
    void getMovies_whenEmpty_returnsEmptyArray() {
        HttpResponse<String> response = movieGetRequest("/movies");

        assertEquals(200, response.statusCode());
        assertEquals(CT_JSON, response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().trim().isEmpty() || "[]".equals(response.body().trim()));
    }

    @Test
    @DisplayName("GET /movies — возвращает список фильмов, если они есть")
    void getMovies_whenHasMovies_returnsListOfMovies() {
        Movie expected = new Movie();
        expected.setTitle("Interstellar");
        expected.setReleaseYear(2014);
        expected.setGenre("Sci-Fi");
        store.add(expected);

        HttpResponse<String> response = movieGetRequest("/movies");
        List<Movie> movies = GSON.fromJson(response.body(), FILM_TOKEN.getType());

        assertEquals(200, response.statusCode());
        assertEquals(CT_JSON, response.headers().firstValue("Content-Type").orElse(""));
        assertFalse(movies.isEmpty());
        assertEquals("Interstellar", movies.get(0).getTitle());
        assertEquals(2014, movies.get(0).getReleaseYear());
    }

    @Test
    @DisplayName("POST /movies — создаёт фильм и возвращает статус 201")
    void postMovie_whenValid_returnsCreated() {
        String json = createMovieJson("The Matrix", 1999, "Action");
        HttpResponse<String> response = moviePostRequest(json);

        assertEquals(201, response.statusCode());
        assertEquals(CT_JSON, response.headers().firstValue("Content-Type").orElse(""));

        Movie saved = GSON.fromJson(response.body(), Movie.class);
        assertEquals("The Matrix", saved.getTitle());
        assertEquals(1999, saved.getReleaseYear());
        assertEquals("Action", saved.getGenre());
    }

    @Test
    @DisplayName("POST /movies — при пустом названии возвращает 422 и ошибку валидации")
    void postMovie_whenEmptyTitle_returnsUnprocessableEntity() {
        String json = createMovieJson("", 1999);
        HttpResponse<String> response = moviePostRequest(json);
        ErrorResponse error = parseError(response);

        assertEquals(422, response.statusCode());
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("Название не должно быть пустым."));
    }

    @Test
    @DisplayName("POST /movies — при слишком длинном названии возвращает 422")
    void postMovie_whenTitleTooLong_returnsUnprocessableEntity() {
        String longTitle = "A".repeat(101);
        String json = createMovieJson(longTitle, 1999);
        HttpResponse<String> response = moviePostRequest(json);
        ErrorResponse error = parseError(response);

        assertEquals(422, response.statusCode());
        assertTrue(error.getDetails().contains("Название не должно быть длиннее 100 символов."));
    }

    @Test
    @DisplayName("POST /movies — при некорректном годе выпуска возвращает 422")
    void postMovie_whenInvalidYear_returnsUnprocessableEntity() {
        String json = createMovieJson("Old Film", 1000);
        HttpResponse<String> response = moviePostRequest(json);
        ErrorResponse error = parseError(response);

        int maxYear = Year.now().getValue() + 1;
        String expectedMsg = "год должен быть между 1888 и " + maxYear;
        assertEquals(422, response.statusCode());
        assertTrue(error.getDetails().stream().anyMatch(msg -> msg.contains("1888") && msg.contains(String.valueOf(maxYear))));
    }

    @Test
    @DisplayName("POST /movies — при неверном Content-Type возвращает 415")
    void postMovie_whenInvalidContentType_returnsUnsupportedMediaType() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = sendRequest(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ErrorResponse error = parseError(response);

        assertEquals(415, response.statusCode());
        assertEquals("Неподдерживаемый Content-Type", error.getError());
        assertTrue(error.getDetails().contains("Ожидался application/json"));
    }

    @Test
    @DisplayName("POST /movies — при некорректном JSON в теле возвращает 422")
    void postMovie_whenInvalidJson_returnsUnprocessableEntity() {
        String invalidJson = "{\"title\": \"A\", \"releaseYear\": 2000,}";

        HttpResponse<String> response = moviePostRequest(invalidJson);
        ErrorResponse error = parseError(response);

        assertEquals(422, response.statusCode());
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("Некорректный JSON."));
    }

    @Test
    @DisplayName("POST /movies — при передаче массива вместо объекта возвращает 422")
    void postMovie_whenJsonArray_returnsUnprocessableEntity() {
        String jsonArray = "[{\"title\": \"A\", \"releaseYear\": 2000, \"genre\": \"Action\"}]";
        HttpResponse<String> response = moviePostRequest(jsonArray);
        ErrorResponse error = parseError(response);

        assertEquals(422, response.statusCode());
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("Некорректный JSON."));
    }

    @Test
    @DisplayName("POST /movies — при ошибке валидации фильм не сохраняется")
    void postMovie_whenValidationFails_movieIsNotSaved() {
        moviePostRequest(createMovieJson("", 1000));

        HttpResponse<String> getResponse = movieGetRequest("/movies");
        List<Movie> movies = GSON.fromJson(getResponse.body(), FILM_TOKEN.getType());

        assertTrue(movies.isEmpty(), "Фильм не должен быть сохранён");
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает фильм, если он существует")
    void getMovieById_whenExists_returnsMovie() {
        Movie movie = new Movie();
        movie.setTitle("Avatar");
        movie.setReleaseYear(2009);
        Movie saved = store.add(movie);

        HttpResponse<String> response = movieGetRequest("/movies/" + saved.getId());
        Movie result = GSON.fromJson(response.body(), Movie.class);

        assertEquals(200, response.statusCode());
        assertEquals("Avatar", result.getTitle());
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает 404, если фильм не найден")
    void getMovieById_whenNotFound_returnsNotFound() {
        HttpResponse<String> response = movieGetRequest("/movies/999");
        ErrorResponse error = parseError(response);

        assertEquals(404, response.statusCode());
        assertEquals("Фильм не найден", error.getError());
    }

    @Test
    @DisplayName("GET /movies/{id} — возвращает 400, если ID не число")
    void getMovieById_whenIdNotNumber_returnsBadRequest() {
        HttpResponse<String> response = movieGetRequest("/movies/abc");
        ErrorResponse error = parseError(response);

        assertEquals(400, response.statusCode());
        assertEquals("Некорректный ID", error.getError());
    }

    @Test
    @DisplayName("DELETE /movies/{id} — удаляет фильм и возвращает 204")
    void deleteMovie_whenExists_returnsNoContent() {
        Movie saved = store.add(new Movie("ToDelete", 2000, "Action"));

        HttpResponse<Void> response = deleteByIdMovieRequest(saved.getId());

        assertEquals(204, response.statusCode());
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    @DisplayName("DELETE /movies/{id} — возвращает 404, если фильм не найден")
    void deleteMovie_whenNotFound_returnsNotFound() {
        HttpResponse<Void> response = deleteByIdMovieRequest(999);
        assertEquals(404, response.statusCode());
    }

    @Test
    @DisplayName("GET /movies?year=... — фильтрует фильмы по году")
    void getMoviesByYear_whenValid_returnsFiltered() {
        store.add(new Movie("2020 A", 2020, "X"));
        store.add(new Movie("2020 B", 2020, "X"));
        store.add(new Movie("2021", 2021, "X"));

        HttpResponse<String> response = movieGetRequest("/movies?year=2020");
        List<Movie> movies = GSON.fromJson(response.body(), FILM_TOKEN.getType());

        assertEquals(200, response.statusCode());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getReleaseYear() == 2020));
    }

    @Test
    @DisplayName("GET /movies?year=... — возвращает пустой массив, если нет совпадений")
    void getMoviesByYear_whenNoMatches_returnsEmptyArray() {
        store.add(new Movie("2021", 2021, "X"));

        HttpResponse<String> response = movieGetRequest("/movies?year=2020");
        List<Movie> movies = GSON.fromJson(response.body(), FILM_TOKEN.getType());

        assertEquals(200, response.statusCode());
        assertTrue(movies.isEmpty());
    }

    @Test
    @DisplayName("GET /movies?year=... — возвращает 400, если год не число")
    void getMoviesByYear_whenYearNotNumber_returnsBadRequest() {
        HttpResponse<String> response = movieGetRequest("/movies?year=abc");
        ErrorResponse error = parseError(response);

        assertEquals(400, response.statusCode());
        assertEquals("Некорректный параметр запроса — 'year'", error.getError());
    }
}