package Practica_7.back;

public enum asunto {
    DUDA(1), QUEJA(2), PROBLEMA(3);

    public final int code;
    asunto(int code) { this.code = code; }

    public static asunto parse(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase();
        if (t.startsWith("DUDA")) return DUDA;
        if (t.startsWith("QUEJA")) return QUEJA;
        if (t.startsWith("PROBLEMA")) return PROBLEMA;
        return null;
    }
}