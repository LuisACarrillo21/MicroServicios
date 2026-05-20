package Practica_7.back;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

public class servidorAtencion {

    private static final int PORT = 5100;
    private static final int AGENTES_POR_ASUNTO = 10;

    private static final BlockingQueue<solicitud> colaProblemas = new LinkedBlockingQueue<>();
    private static final BlockingQueue<solicitud> colaQuejas    = new LinkedBlockingQueue<>();
    private static final BlockingQueue<solicitud> colaDudas     = new LinkedBlockingQueue<>();

    private static final Set<Long> idsVistos = ConcurrentHashMap.newKeySet();

    private static final LongAdder recibidas = new LongAdder();
    private static final LongAdder encoladas = new LongAdder();
    private static final LongAdder duplicadas = new LongAdder();
    private static final LongAdder invalidas = new LongAdder();

    private static final LongAdder atendidasProblemas = new LongAdder();
    private static final LongAdder atendidasQuejas = new LongAdder();
    private static final LongAdder atendidasDudas = new LongAdder();

    private static ExecutorService poolProblemas;
    private static ExecutorService poolQuejas;
    private static ExecutorService poolDudas;

    public static void main(String[] args) throws Exception {
        iniciarAgentes();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/solicitudes", servidorAtencion::handleSolicitudes);
        server.createContext("/status", servidorAtencion::handleStatus);
        server.createContext("/colas", servidorAtencion::handleColas);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Servidor Web iniciado en http://localhost:" + PORT);
        System.out.println("POST /solicitudes | GET /status | GET /colas/problema|queja|duda");
    }

    private static void iniciarAgentes() {
        poolProblemas = Executors.newFixedThreadPool(AGENTES_POR_ASUNTO);
        poolQuejas    = Executors.newFixedThreadPool(AGENTES_POR_ASUNTO);
        poolDudas     = Executors.newFixedThreadPool(AGENTES_POR_ASUNTO);

        for (int i = 1; i <= AGENTES_POR_ASUNTO; i++) {
            final int id = i;
            poolProblemas.submit(() -> loopAgente(asunto.PROBLEMA, id));
            poolQuejas.submit(() -> loopAgente(asunto.QUEJA, id));
            poolDudas.submit(() -> loopAgente(asunto.DUDA, id));
        }
    }

    private static void loopAgente(asunto asunto, int agenteId) {
        BlockingQueue<solicitud> cola = colaDe(asunto);
        while (true) {
            try {
                solicitud s = cola.take();
                Thread.sleep(tiempoServicioMs(asunto));

                if (asunto == asunto.PROBLEMA) atendidasProblemas.increment();
                else if (asunto == asunto.QUEJA) atendidasQuejas.increment();
                else atendidasDudas.increment();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static long tiempoServicioMs(asunto a) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (a) {
            case PROBLEMA: return r.nextLong(6000, 12001);
            case QUEJA:    return r.nextLong(4000, 9001);
            default:       return r.nextLong(3000, 7001);
        }
    }

    private static BlockingQueue<solicitud> colaDe(asunto a) {
        switch (a) {
            case PROBLEMA: return colaProblemas;
            case QUEJA:    return colaQuejas;
            default:       return colaDudas;
        }
    }

    // ===== ENDPOINTS =====

    private static void handleSolicitudes(HttpExchange ex) throws IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        String body = readBody(ex);
        solicitud s = Json.fromJson(body);

        recibidas.increment();

        if (s == null) {
            invalidas.increment();
            respondJson(ex, 400, Json.jsonStatus("error", "JSON inválido o campos faltantes"));
            return;
        }

        if (!idsVistos.add(s.id)) {
            duplicadas.increment();
            respondJson(ex, 409, Json.jsonStatus("duplicate", "Solicitud duplicada: id=" + s.id));
            return;
        }

        colaDe(s.asunto).offer(s);
        encoladas.increment();

        respondJson(ex, 200, "{"
                + "\"status\":\"queued\","
                + "\"id\":" + s.id + ","
                + "\"tipoCliente\":\"" + s.Cliente + "\","
                + "\"asunto\":\"" + s.asunto + "\""
                + "}");
    }

    private static void handleStatus(HttpExchange ex) throws IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        String json = "{"
                + "\"time\":\"" + Instant.now() + "\","
                + "\"recibidas\":" + recibidas.sum() + ","
                + "\"encoladas\":" + encoladas.sum() + ","
                + "\"duplicadas\":" + duplicadas.sum() + ","
                + "\"invalidas\":" + invalidas.sum() + ","
                + "\"colas\":{"
                    + "\"problemas\":" + colaProblemas.size() + ","
                    + "\"quejas\":" + colaQuejas.size() + ","
                    + "\"dudas\":" + colaDudas.size()
                + "},"
                + "\"atendidas\":{"
                    + "\"problemas\":" + atendidasProblemas.sum() + ","
                    + "\"quejas\":" + atendidasQuejas.sum() + ","
                    + "\"dudas\":" + atendidasDudas.sum()
                + "}"
            + "}";

        respondJson(ex, 200, json);
    }

    private static void handleColas(HttpExchange ex) throws IOException {
        addCors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 204, "");
            return;
        }

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondJson(ex, 405, Json.jsonStatus("error", "Método no permitido"));
            return;
        }

        String path = ex.getRequestURI().getPath(); // /colas/problema
        String[] parts = path.split("/");
        if (parts.length < 3) {
            respondJson(ex, 400, Json.jsonStatus("error", "Usa /colas/problema|queja|duda"));
            return;
        }

        asunto a = asunto.parse(parts[2]);
        if (a == null) {
            respondJson(ex, 400, Json.jsonStatus("error", "Asunto inválido"));
            return;
        }

        BlockingQueue<solicitud> cola = colaDe(a);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"asunto\":\"").append(a).append("\",\"size\":").append(cola.size()).append(",\"items\":[");
        int i = 0;
        for (solicitud s : cola) {
            if (i++ >= 50) break;
            sb.append(Json.toJson(s)).append(",");
        }
        if (i > 0) sb.setLength(sb.length() - 1);
        sb.append("]}");

        respondJson(ex, 200, sb.toString());
    }

    // ===== Helpers =====

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respondJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }
}
