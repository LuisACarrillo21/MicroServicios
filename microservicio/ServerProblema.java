package microservicio;

public class ServerProblema {
    public static void main(String[] args) throws Exception {
        new AsuntoServicio(5101, Estructura.Asunto.PROBLEMA, 10).start();
    }
}