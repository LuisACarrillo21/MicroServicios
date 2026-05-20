package microservicio;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Estructura {

    public enum Asunto {
        DUDA(1), QUEJA(2), PROBLEMA(3);

        public final int code;
        Asunto(int code) { this.code = code; }

        public static Asunto parse(String s) {
            if (s == null) return null;
            String t = s.trim().toUpperCase();
            if (t.startsWith("DUD")) return DUDA;
            if (t.startsWith("QUEJ")) return QUEJA;
            if (t.startsWith("PROBLE")) return PROBLEMA;
            return null;
        }
    }

    public enum TipoC {
        VIP(1), NORMAL(2), NO_CLIENTE(3);

        public final int code;
        TipoC(int code) { this.code = code; }

        public static TipoC parse(String s) {
            if (s == null) return null;
            String t = s.trim().toUpperCase();
            switch (t) {
                case "VIP": return VIP;
                case "NORMAL": return NORMAL;
                case "NO_CLIENTE":
                case "NOCLIENTE": return NO_CLIENTE;
                default: return null;
            }
        }
    }

    public static class Solicitud {
        public final long id;
        public final TipoC tipoCliente;
        public final Asunto asunto;
        public final long timestampMs;

        public Solicitud(long id, TipoC tipoCliente, Asunto asunto, long timestampMs) {
            this.id = id;
            this.tipoCliente = tipoCliente;
            this.asunto = asunto;
            this.timestampMs = timestampMs;
        }

        @Override
        public String toString() {
            return "#" + id + " | " + tipoCliente + " | " + asunto;
        }
    }

    public static class Json {

        private static final Pattern LONG_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
        private static final Pattern STR_FIELD  = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");

        public static Solicitud fromJsonSolicitud(String json) {
            if (json == null) return null;

            Long id = getLong(json, "id");
            Long ts = getLong(json, "timestampMs");
            String tipo = getString(json, "tipoCliente");
            String asunto = getString(json, "asunto");

            if (id == null || tipo == null || asunto == null) return null;
            if (ts == null) ts = System.currentTimeMillis();

            TipoC tc = TipoC.parse(tipo);
            Asunto as = Asunto.parse(asunto);
            if (tc == null || as == null) return null;

            return new Solicitud(id, tc, as, ts);
        }

        public static String toJsonSolicitud(Solicitud s) {
            return "{"
                    + "\"id\":" + s.id + ","
                    + "\"tipoCliente\":\"" + s.tipoCliente + "\","
                    + "\"asunto\":\"" + s.asunto + "\","
                    + "\"timestampMs\":" + s.timestampMs
                    + "}";
        }

        public static String jsonStatus(String status, String message) {
            message = message == null ? "" : message.replace("\"","\\\"");
            return "{"
                    + "\"status\":\"" + status + "\","
                    + "\"message\":\"" + message + "\""
                    + "}";
        }

        private static Long getLong(String json, String key) {
            Pattern p = Pattern.compile(String.format(LONG_FIELD.pattern(), Pattern.quote(key)));
            Matcher m = p.matcher(json);
            if (m.find()) return Long.parseLong(m.group(1));
            return null;
        }

        private static String getString(String json, String key) {
            Pattern p = Pattern.compile(String.format(STR_FIELD.pattern(), Pattern.quote(key)));
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
            return null;
        }

        public static byte[] utf8(String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }
}