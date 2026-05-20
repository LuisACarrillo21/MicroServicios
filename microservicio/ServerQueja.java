package microservicio;

public class ServerQueja {
    public static void main(String[] args) throws Exception {
        new AsuntoServicio(5102, Estructura.Asunto.QUEJA, 10).start();
    }
}