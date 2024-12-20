import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Client {

    static final String SERVER_ADDRESS = "localhost";
    static final int PORT = 5105;
    static final int CHUNK_SIZE = 4096;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter the file names to upload (comma-separated):");
        while (true) {
            String[] file_names = scanner.nextLine().split(",");

            for (String file_name : file_names) {
                file_name = file_name.trim();

                Thread upload_thread = new Thread(new FileUploader(file_name));
                upload_thread.start();
            }
        }
    }
}

class FileUploader implements Runnable {
    private String file_name;

    public FileUploader(String fileName) {
        this.file_name = fileName;
    }

    @Override
    public void run() {
        if (!isValidUploadFileFormat(file_name)) {
            System.out.println("Invalid file format for " + file_name);
            return;
        }

        File file = new File(file_name);
        try (Socket socket = new Socket(Client.SERVER_ADDRESS, Client.PORT);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                FileInputStream fis = new FileInputStream(file)) {

            // Sending upload request to server
            dos.writeBytes("UPLOAD " + file_name + "\n");
            dos.flush();

            // Uploading the file in chunks
            byte[] buffer = new byte[Client.CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                dos.flush();
            }

            System.out.println("File " + file_name + " uploaded");

        } catch (IOException e) {
            System.err.println("Error uploading file " + file_name);
            // e.printStackTrace();
        }
    }

    private boolean isValidUploadFileFormat(String file_name) {
        Path file_path = Paths.get(file_name);

        try {
            String mime_type = Files.probeContentType(file_path);
            if (mime_type != null) {
                return mime_type.startsWith("text") || mime_type.startsWith("image") || mime_type.startsWith("video");
            }
        } catch (IOException e) {
            // e.printStackTrace();
        }

        return false;
    }
}
