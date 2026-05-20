package Practica_7.back;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Json {

    private static final Pattern LONG_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern STR_FIELD  = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");

    public static String toJson(solicitud s) {
        return "{"
                + "\"id\":" + s.id + ","
                + "\"tipoCliente\":\"" + s.Cliente.name() + "\","
                + "\"asunto\":\"" + s.asunto.name() + "\","
                + "\"timestampMs\":" + s.timestampMs
                + "}";
    }

    public static solicitud fromJson(String json) {
        if (json == null) return null;

        Long id = getLong(json, "id");
        Long ts = getLong(json, "timestampMs");
        String tipo = getString(json, "tipoCliente");
        String asunt = getString(json, "asunto");

        if (id == null || tipo == null || asunt == null) return null;
        if (ts == null) ts = System.currentTimeMillis();

        tipoC tc = tipoC.parse(tipo);
        asunto as = asunto.parse(asunt);

        if (tc == null || as == null) return null;

        return new solicitud(id, tc, as, ts);
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

    public static String jsonStatus(String status, String message) {
        message = message == null ? "" : message.replace("\"", "\\\"");
        return "{"
                + "\"status\":\"" + status + "\","
                + "\"message\":\"" + message + "\""
                + "}";
    }
}