package microservicio;

public class ServerDuda {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "5103"));
        new AsuntoServicio(port, Estructura.Asunto.DUDA, 10).start();
    }
}
