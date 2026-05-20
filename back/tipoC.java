package Practica_7.back;

public enum tipoC {
     VIP(1), NORMAL(2), NO_CLIENTE(3);

    public final int code;
    tipoC(int code) { this.code = code; }

    public static tipoC parse(String s) {
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
