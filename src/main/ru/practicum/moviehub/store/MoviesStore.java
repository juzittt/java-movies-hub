package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie add(Movie movie) {
        movie.setId(nextId++);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public Movie findById(int id) {
        return movies.get(id);
    }

    public boolean delete(int id) {
        if (movies.containsKey(id)) {
            movies.remove(id);
            return true;
        }
        return false;
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }
}