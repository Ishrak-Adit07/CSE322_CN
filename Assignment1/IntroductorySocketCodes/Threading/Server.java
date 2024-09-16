package Threading;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

public class Server {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        ServerSocket welcomeSocket = new ServerSocket(6666);

        while (true) {
            System.out.println("Waiting for connection...");
            Socket socket = welcomeSocket.accept();
            System.out.println("Connection established");

            // open thread
            Thread worker = new Worker(socket);
            worker.start();

        }

    }
}

class Worker extends Thread {
    Socket socket;

    public Worker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // buffers
        try {
            ObjectOutputStream out = new ObjectOutputStream(this.socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(this.socket.getInputStream());

            while (true) {
                Thread.sleep(1000);
                Date date = new Date();
                out.writeObject(date.toString());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
