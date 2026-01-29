package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class MoviesServer {
    private  final HttpServer server;

    public MoviesServer(){
        try {
           server = HttpServer.create(new InetSocketAddress(8080), 0);
           server.createContext("/movies", exchange -> {
               String response = "[]";
               byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
               exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
               exchange.sendResponseHeaders(200, bytes.length);
               try (OutputStream os = exchange.getResponseBody()) {
                   os.write(bytes);
               }
           });
           server.setExecutor(null);
        } catch (IOException e){
            throw new RuntimeException("Не удалось создать HTTP-сервер");
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }
}