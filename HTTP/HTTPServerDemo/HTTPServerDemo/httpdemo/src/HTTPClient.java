import java.io.*;
import java.net.Socket;

public class HTTPClient {

    public static void main(String[] args) {
        String host = "localhost"; // Server hostname
        int port = 6789; // Server port

        try {
            // Create a socket to connect to the server
            Socket socket = new Socket(host, port);
            System.out.println("Connected to the server.");

            // Send an HTTP GET request
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("GET / HTTP/1.1");
            out.println("Host: " + host);
            out.println("Connection: Close");
            out.println(); // Empty line indicates end of request headers

            // Read the response from the server
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println(responseLine); // Print each line of the response
            }

            // Close the connection
            in.close();
            out.close();
            socket.close();
            System.out.println("Connection closed.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
