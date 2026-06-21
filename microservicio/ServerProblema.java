package microservicio;

public class ServerProblema {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "5101"));
        new AsuntoServicio(port, Estructura.Asunto.PROBLEMA, 10).start();
    }
}
