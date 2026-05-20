package microservicio;

public class ServerDuda {
    public static void main(String[] args) throws Exception {
        new AsuntoServicio(5103, Estructura.Asunto.DUDA, 10).start();
    }
}