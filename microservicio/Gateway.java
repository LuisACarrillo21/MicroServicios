package microservicio;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;

public class Gateway {

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "5100"));

    private static final String PROBLEMA_BASE = System.getenv().getOrDefault("PROBLEMA_URL", "http://localhost:5101");
    private static final String QUEJA_BASE    = System.getenv().getOrDefault("QUEJA_URL", "http://localhost:5102");
    private static final String DUDA_BASE     System.getenv().getOrDefault("DUDA_URL", "http://localhost:5103");

    private static final HttpClient http = HttpClient.newBuilder().build();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/solicitudes", Gateway::handleSolicitudes);
        server.createContext("/status", Gateway::handleStatus);
        server.createContext("/colas", Gateway::handleColas);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("[GATEWAY] http://localhost:" + PORT
                + " | POST /solicitudes | GET /status | GET /colas/problema|queja|duda");
    }

    // POST /solicitudes -> rutea por asunto al microservicio correcto
    private static void handleSolicitudes(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error","Método no permitido"));
            return;
        }

        String body = readBody(ex);
        Estructura.Solicitud s = Estructura.Json.fromJsonSolicitud(body);

        if (s == null) {
            respondJson(ex, 400, Estructura.Json.jsonStatus("error","JSON inválido o campos faltantes"));
            return;
        }

        String base = baseForAsunto(s.asunto);
        if (base == null) {
            respondJson(ex, 400, Estructura.Json.jsonStatus("error","Asunto inválido"));
            return;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/solicitudes"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            respondJson(ex, resp.statusCode(), resp.body());

        } catch (Exception e) {
            respondJson(ex, 502, Estructura.Json.jsonStatus("error","Gateway no pudo contactar servicio: " + e.getMessage()));
        }
    }

    // GET /status -> agrega status de los 3 microservicios
    private static void handleStatus(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error","Método no permitido"));
            return;
        }

        String p = fetchStatus(PROBLEMA_BASE);
        String q = fetchStatus(QUEJA_BASE);
        String d = fetchStatus(DUDA_BASE);

        String json = "{"
                + "\"time\":\"" + Instant.now() + "\","
                + "\"gateway\":true,"
                + "\"services\":{"
                    + "\"problema\":" + p + ","
                    + "\"queja\":" + q + ","
                    + "\"duda\":" + d
                + "}"
            + "}";

        respondJson(ex, 200, json);
    }

    // GET /colas/{asunto} -> forward al microservicio
    private static void handleColas(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 204, ""); return; }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error","Método no permitido"));
            return;
        }

        String path = ex.getRequestURI().getPath(); // /colas/problema
        String[] parts = path.split("/");
        if (parts.length < 3) {
            respondJson(ex, 400, Estructura.Json.jsonStatus("error","Usa /colas/problema|queja|duda"));
            return;
        }

        Estructura.Asunto a = Estructura.Asunto.parse(parts[2]);
        String base = baseForAsunto(a);
        if (base == null) {
            respondJson(ex, 400, Estructura.Json.jsonStatus("error","Asunto inválido"));
            return;
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/colas"))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            respondJson(ex, resp.statusCode(), resp.body());

        } catch (Exception e) {
            respondJson(ex, 502, Estructura.Json.jsonStatus("error","Gateway no pudo contactar servicio: " + e.getMessage()));
        }
    }

    private static String fetchStatus(String base) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(base + "/status")).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return resp.body();
            return Estructura.Json.jsonStatus("error","statusCode=" + resp.statusCode());
        } catch (Exception e) {
            return Estructura.Json.jsonStatus("error","down: " + e.getMessage());
        }
    }

    private static String baseForAsunto(Estructura.Asunto a) {
        if (a == null) return null;
        switch (a) {
            case PROBLEMA: return PROBLEMA_BASE;
            case QUEJA:    return QUEJA_BASE;
            case DUDA:     return DUDA_BASE;
            default:       return null;
        }
    }

    // helpers
    private static String readBody(HttpExchange ex) throws java.io.IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respondJson(HttpExchange ex, int code, String json) throws java.io.IOException {
        byte[] data = Estructura.Json.utf8(json);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void respond(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] data = Estructura.Json.utf8(body);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }
}
