package Practica_7.back;

public class solicitud {
    public final long id;
    public final tipoC Cliente;
    public final asunto asunto;
    public final long timestampMs;

    public solicitud(long id, tipoC Cliente, asunto asunto, long timestampMs) {
        this.id = id;
        this.Cliente = Cliente;
        this.asunto = asunto;
        this.timestampMs = timestampMs;
    }

    @Override
    public String toString() {
        return "#" + id + " | " + Cliente + " | " + asunto;
    }
}
