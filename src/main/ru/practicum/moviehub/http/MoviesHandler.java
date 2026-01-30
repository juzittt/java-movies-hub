package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.*;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private static final String ERROR_INVALID_JSON = "Некорректный JSON.";
    private static final String ERROR_EMPTY_TITLE = "Название не должно быть пустым.";
    private static final String ERROR_LONG_TITLE = "Название не должно быть длиннее 100 символов.";
    private static final String ERROR_INVALID_YEAR = "Год должен быть между 1888 и %d";
    private static final String ERROR_CONTENT_TYPE = "Ожидался application/json";
    private static final String ERROR_UNSUPPORTED_MEDIA_TYPE = "Неподдерживаемый Content-Type";

    private static final int HTTP_UNSUPPORTED_TYPE = 415;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;


    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        switch (method) {
            case "GET" -> handleGet(ex, path, query);
            case "POST" -> handlePost(ex, path);
            case "DELETE" -> handleDelete(ex, path);
            default -> sendMethodNotAllowed(ex);
        }
    }

    private void handleGet(HttpExchange ex, String path, String query) throws IOException {
        if ("/movies".equals(path)) {
            handleGetAll(ex, query);
        } else if (path.startsWith("/movies/")) {
            String idPart = path.substring(8);
            if (isNumeric(idPart)) {
                int id = Integer.parseInt(idPart);
                handleGetById(ex, id);
            } else {
                sendBadRequest(ex, "Некорректный ID");
            }
        } else {
            sendNotFound(ex);
        }
    }

    private void handlePost(HttpExchange ex, String path) throws IOException {
        if (!"/movies".equals(path)) {
            sendMethodNotAllowed(ex);
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendUnsupportedMediaType(ex);
            return;
        }

        Movie movie = parseRequestBody(ex, Movie.class);
        if (movie == null) {
            sendUnprocessableEntity(ex, List.of(ERROR_INVALID_JSON));
            return;
        }

        List<String> errors = validateMovie(movie);
        if (!errors.isEmpty()) {
            sendUnprocessableEntity(ex, errors);
            return;
        }

        Movie saved = store.add(movie);
        sendJson(ex, HTTP_CREATED, gson.toJson(saved));
    }

    private void handleDelete(HttpExchange ex, String path) throws IOException {
        if (path.matches("/movies/\\d+")) {
            int id = Integer.parseInt(path.substring(8));
            if (store.delete(id)) {
                sendNoContent(ex);
            } else {
                sendNotFound(ex);
            }
        } else {
            sendMethodNotAllowed(ex);
        }
    }

    private void handleGetAll(HttpExchange ex, String query) throws IOException {
        if (query != null && query.startsWith("year=")) {
            handleGetByYear(ex, query);
        } else {
            sendJson(ex, HTTP_OK, gson.toJson(store.getAll()));
        }
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {
        String yearParam = getQueryParam(query, "year");
        if (yearParam == null || !isNumeric(yearParam)) {
            sendBadRequest(ex, "Некорректный параметр запроса — 'year'");
            return;
        }

        int year = Integer.parseInt(yearParam);
        int maxYear = getCurrentYear() + 1;
        if (year < 1888 || year > maxYear) {
            sendBadRequest(ex, String.format(ERROR_INVALID_YEAR, maxYear));
            return;
        }

        List<Movie> filtered = store.getAll().stream()
                .filter(m -> m.getReleaseYear() == year)
                .toList();

        sendJson(ex, HTTP_OK,  gson.toJson(filtered));
    }

    private void handleGetById(HttpExchange ex, int id) throws IOException {
        Movie movie = store.findById(id);
        if (movie == null) {
            sendNotFound(ex);
        } else {
            sendJson(ex, HTTP_OK, gson.toJson(movie));
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        String title = movie.getTitle();
        if (title == null || title.trim().isEmpty()) {
            errors.add(ERROR_EMPTY_TITLE);
        } else if (title.trim().length() > 100) {
            errors.add(ERROR_LONG_TITLE);
        }

        int year = movie.getReleaseYear();
        int maxYear = getCurrentYear() + 1;
        if (year < 1888 || year > maxYear) {
            errors.add(String.format(ERROR_INVALID_YEAR, maxYear));
        }

        return errors;
    }

    private void sendNotFound(HttpExchange ex) throws IOException {
        sendJson(ex, HTTP_NOT_FOUND, gson.toJson(new ErrorResponse("Фильм не найден")));
    }

    private void sendBadRequest(HttpExchange ex, String message) throws IOException {
        sendJson(ex, HTTP_BAD_REQUEST, gson.toJson(new ErrorResponse(message)));
    }

    private void sendUnprocessableEntity(HttpExchange ex, List<String> details) throws IOException {
        sendJson(ex, HTTP_UNPROCESSABLE_ENTITY, gson.toJson(new ErrorResponse("Ошибка валидации", details)));
    }

    private void sendUnsupportedMediaType(HttpExchange ex) throws IOException {
        sendJson(ex, HTTP_UNSUPPORTED_TYPE, gson.toJson(new ErrorResponse(
                ERROR_UNSUPPORTED_MEDIA_TYPE,
                List.of(ERROR_CONTENT_TYPE)
        )));
    }

    private void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1);
        ex.close();
    }
}