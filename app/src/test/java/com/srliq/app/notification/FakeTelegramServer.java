package com.srliq.app.notification;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Faux serveur de l'API Bot Telegram (HTTP local) : enregistre les requêtes, répond selon le mode. */
final class FakeTelegramServer {

    enum Mode { OK, ERROR_500, DROP }

    record Request(String path, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Mode mode = Mode.OK;

    FakeTelegramServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestURI().getPath(), body));
            switch (mode) {
                case DROP -> exchange.close(); // connexion fermée sans réponse
                case ERROR_500 -> respond(exchange, 500, "{\"ok\":false,\"description\":\"Internal Server Error\"}");
                default -> respond(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
            }
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void mode(Mode m) {
        mode = m;
    }

    List<Request> requests() {
        return requests;
    }

    void reset() {
        requests.clear();
        mode = Mode.OK;
    }
}
