package ru.practicum.moviehub.api;

import java.util.List;
import java.util.Objects;

public class ErrorResponse {
    private final String error;
    private final List<String> details;

    public ErrorResponse(String error) {
        this(error, List.of());
    }

    public ErrorResponse(String error, List<String> details) {
        this.error = Objects.requireNonNull(error, "Error не может быть null.");
        this.details = details != null ? List.copyOf(details) : List.of();
    }

    public String getError() {
        return error;
    }

    public List<String> getDetails() {
        return details;
    }
}