import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    static final int PORT = 5105;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server listening for connections on port: " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connection established with client.");

            // Individual thread for each client connection
            Thread workerThread = new Thread(new Worker(clientSocket));
            workerThread.start();
        }
    }
}
