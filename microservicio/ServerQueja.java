package microservicio;

public class ServerQueja {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "5102"));
        new AsuntoServicio(port, Estructura.Asunto.QUEJA, 10).start();
    }
}
