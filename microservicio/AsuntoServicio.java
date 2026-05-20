package microservicio;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

public class AsuntoServicio {

    private final int port;
    private final Estructura.Asunto asuntoAceptado;
    private final int agentes;

    private final BlockingQueue<Estructura.Solicitud> cola = new LinkedBlockingQueue<>();
    private final Set<Long> idsVistos = ConcurrentHashMap.newKeySet();

    private final LongAdder recibidas = new LongAdder();
    private final LongAdder encoladas = new LongAdder();
    private final LongAdder duplicadas = new LongAdder();
    private final LongAdder invalidas = new LongAdder();
    private final LongAdder atendidas = new LongAdder();

    private ExecutorService poolAgentes;

    public AsuntoServicio(int port, Estructura.Asunto asuntoAceptado, int agentes) {
        this.port = port;
        this.asuntoAceptado = asuntoAceptado;
        this.agentes = agentes;
    }

    public void start() throws Exception {
        iniciarAgentes();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/solicitudes", this::handleSolicitudes);
        server.createContext("/status", this::handleStatus);
        server.createContext("/colas", this::handleColas);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("[" + asuntoAceptado + "-SERVICE] http://localhost:" + port
                + " | POST /solicitudes | GET /status | GET /colas");
    }

    private void iniciarAgentes() {
        poolAgentes = Executors.newFixedThreadPool(agentes);
        for (int i = 1; i <= agentes; i++) {
            final int id = i;
            poolAgentes.submit(() -> loopAgente(id));
        }
    }

    private void loopAgente(int agenteId) {
        while (true) {
            try {
                Estructura.Solicitud s = cola.take();
                Thread.sleep(tiempoServicioMs(asuntoAceptado));
                atendidas.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private long tiempoServicioMs(Estructura.Asunto a) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (a) {
            case PROBLEMA: return r.nextLong(6000, 12001);
            case QUEJA:    return r.nextLong(4000, 9001);
            default:       return r.nextLong(3000, 7001);
        }
    }

    // ========= Handlers =========

    private void handleSolicitudes(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        String body = readBody(ex);
        Estructura.Solicitud s = Estructura.Json.fromJsonSolicitud(body);
        recibidas.increment();

        if (s == null) {
            invalidas.increment();
            respondJson(ex, 400, Estructura.Json.jsonStatus("error", "JSON inválido o campos faltantes"));
            return;
        }

        // Este microservicio solo acepta SU asunto
        if (s.asunto != asuntoAceptado) {
            invalidas.increment();
            respondJson(ex, 400, Estructura.Json.jsonStatus("error",
                    "Asunto incorrecto. Este servicio acepta: " + asuntoAceptado));
            return;
        }

        if (!idsVistos.add(s.id)) {
            duplicadas.increment();
            respondJson(ex, 409, Estructura.Json.jsonStatus("duplicate", "Duplicada: id=" + s.id));
            return;
        }

        cola.offer(s);
        encoladas.increment();

        respondJson(ex, 200, "{"
                + "\"status\":\"queued\","
                + "\"service\":\"" + asuntoAceptado + "\","
                + "\"id\":" + s.id
                + "}");
    }

    private void handleStatus(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        String json = "{"
                + "\"time\":\"" + Instant.now() + "\","
                + "\"service\":\"" + asuntoAceptado + "\","
                + "\"recibidas\":" + recibidas.sum() + ","
                + "\"encoladas\":" + encoladas.sum() + ","
                + "\"duplicadas\":" + duplicadas.sum() + ","
                + "\"invalidas\":" + invalidas.sum() + ","
                + "\"cola\":" + cola.size() + ","
                + "\"atendidas\":" + atendidas.sum()
                + "}";

        respondJson(ex, 200, json);
    }

    private void handleColas(HttpExchange ex) throws java.io.IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Estructura.Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"service\":\"").append(asuntoAceptado).append("\",\"size\":").append(cola.size()).append(",\"items\":[");
        int i = 0;
        for (Estructura.Solicitud s : cola) {
            if (i++ >= 50) break;
            sb.append(Estructura.Json.toJsonSolicitud(s)).append(",");
        }
        if (i > 0) sb.setLength(sb.length() - 1);
        sb.append("]}");

        respondJson(ex, 200, sb.toString());
    }

    // ========= Helpers =========

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