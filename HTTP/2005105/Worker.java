import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
 
public class Worker implements Runnable {
    private Socket socket;
    private static final int CHUNK_SIZE = 4096;

    private static final String UPLOAD_DIRECTORY = "uploaded";
    private static final String LOG_FILE_NAME = "log.txt";
    private BufferedWriter LOG_WRITER;

    public Worker(Socket socket) throws IOException {
        this.socket = socket;

        try {
            LOG_WRITER = new BufferedWriter(new FileWriter(LOG_FILE_NAME, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pr = new PrintWriter(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            String request = in.readLine();

            if (request == null)
                return;

            logRequest(request);

            // Task 1 -> Display or Download Files
            if (request.startsWith("GET")) {
                String[] request_tokens = request.split(" ");
                String file_path = request_tokens[1]; // GET file_name

                file_path = file_path.substring(1);
                if (file_path.equals("")) {
                    file_path = ".";
                }

                handleGetRequest(file_path, pr);
            }

            // Task 2 -> Upload Files
            else if (request.startsWith("UPLOAD")) {
                String[] request_tokens = request.split(" ");

                // Two tokens, UPLOAD file_name
                if (request_tokens.length != 2) {
                    System.out.println("Invalid upload request format.");
                    return;
                }

                String file_name = request_tokens[1];
                if (!isValidUploadFileFormat(file_name)) {
                    System.out.println("Invalid file format for " + file_name);
                    return;
                }

                hanldeUploadRequest(file_name, dis);
            }

            // No match
            else {
                System.out.println("Invalid request: " + request);
            }

        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Error in request handle run process " + e.getMessage());
        } finally {
            try {
                socket.close();
                if (LOG_WRITER != null) {
                    LOG_WRITER.close();
                }
            } catch (IOException e) {
                // e.printStackTrace();
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    // Log file write functions
    private void logRequest(String request) throws IOException {
        if (LOG_WRITER != null) {
            LOG_WRITER.write(request);
            LOG_WRITER.newLine();
            LOG_WRITER.flush();
        }
    }

    private void logResponse(int status_code, String status_message, String content_type, String content)
            throws IOException {
        if (LOG_WRITER != null) {
            LOG_WRITER.write("HTTP/1.0 " + status_code + " " + status_message + "\r\n");
            LOG_WRITER.write("Server: Java HTTP Server\r\n");
            LOG_WRITER.write("Date: " + new Date() + "\r\n");
            LOG_WRITER.write("Content-Type: " + content_type + "\r\n");
            LOG_WRITER.write("Content-Length: " + content.length() + "\r\n");
            LOG_WRITER.write("\r\n");
            LOG_WRITER.flush();
        }
    }

    private void logResponse(int status_code, String status_message, String content_type, File file)
            throws IOException {
        if (LOG_WRITER != null) {
            LOG_WRITER.write("HTTP/1.0 " + status_code + " " + status_message + "\r\n");
            LOG_WRITER.write("Server: Java HTTP Server\r\n");
            LOG_WRITER.write("Date: " + new Date() + "\r\n");
            LOG_WRITER.write("Content-Type: " + content_type + "\r\n");
            LOG_WRITER.write("Content-Length: " + file.length() + "\r\n");
            LOG_WRITER.write("\r\n");
            LOG_WRITER.flush();
        }
    }

    // Functions required for file/folder display/download

    // For proper rendering of file_name
    private String renderDisplayNameForFile(String file_name) {
        String display_name = file_name
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("=", "&#61;")
                .replace("/", "&#x2F;")
                .replace("#", "&#35;")
                .replace("%", "&#37;");

        return display_name;
    }

    private void handleGetRequest(String path, PrintWriter pr) throws IOException {
        File file = new File(path);

        // File exists, is a directory
        if (file.isDirectory()) {

            File[] files = file.listFiles();

            // For all files of that directory...
            if ((files != null) && (files.length != 0)) {

                // Start of html page
                StringBuilder content = new StringBuilder(
                        "<html><body><h1>List of files in Directory " + path + "</h1>");
                content.append("<ul>");

                // Display all files/folders in this directory
                for (File f : files) {
                    String file_name = f.getName();
                    String display_name = renderDisplayNameForFile(file_name);

                    // Directory name in bold-italic fonts
                    if (f.isDirectory()) {
                        content.append("<li><b><i><a href=\"").append(f.getName()).append("/\">")
                                .append(display_name).append("/</a></i></b></li>");
                    }
                    // File name in normal fonts
                    else {
                        content.append("<li><a href=\"").append(file_name).append("\">")
                                .append(display_name).append("</a></li>");
                    }
                }

                // Complete html page
                content.append("</ul>");
                content.append("</body></html>");

                // Success response
                sendResponse(200, "OK", "text/html", content.toString(), pr);
            }

            // Failure response
            else if ((files != null) && (files.length == 0)) {
                sendResponse(404, "Not Found", "text/html",
                        "<html><body><h1>No Files in this directory</h1></body></html>", pr);
            }

            else {
                sendResponse(404, "Not Found", "text/html",
                        "<html><body><h1>Directory not found</h1></body></html>", pr);
            }
        }

        // File exists, is not a directory
        else if (file.exists()) {

            String mime_type = Files.probeContentType(file.toPath());
            if (mime_type == null) {
                mime_type = "application/octet-stream";
            }

            // Display if file is text/image type
            if (mime_type.startsWith("text")) {
                String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
                sendResponse(200, "OK", mime_type, content, pr);
            }

            else if (mime_type.startsWith("image")) {
                sendImageResponse(file, pr, mime_type);
            }

            // Not text or image, forcing to download
            else {
                sendFileDownload(file, pr);
            }
        }

        // File does not exist
        else {
            sendResponse(404, "Not Found", "text/html", "<html><body><h1>404: File Not Found</h1></body></html>", pr);
        }
    }

    // Common function for sending response
    private void sendResponse(int status_code, String status_message, String content_type,
            String content, PrintWriter pr) throws IOException {
        pr.write("HTTP/1.0 " + status_code + " " + status_message + "\r\n");
        pr.write("Server: Java HTTP Server\r\n");
        pr.write("Date: " + new Date() + "\r\n");
        pr.write("Content-Type: " + content_type + "\r\n");
        pr.write("Content-Length: " + content.length() + "\r\n");
        pr.write("\r\n");
        pr.write(content);
        pr.flush();

        logResponse(status_code, status_message, content_type, content);
    }

    // Extracted Method
    private void writeOutStreamFile(File file) throws IOException {
        OutputStream out_stream = socket.getOutputStream();
        FileInputStream file_in = new FileInputStream(file);
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytes_read;
        while ((bytes_read = file_in.read(buffer)) != -1) {
            out_stream.write(buffer, 0, bytes_read);
        }
        out_stream.flush();
        file_in.close();
    }

    private void sendImageResponse(File file, PrintWriter pr, String mime_type) throws IOException {
        pr.write("HTTP/1.0 200 OK\r\n");
        pr.write("Server: Java HTTP Server\r\n");
        pr.write("Date: " + new Date() + "\r\n");
        pr.write("Content-Type: " + mime_type + "\r\n");
        pr.write("Content-Length: " + file.length() + "\r\n");
        pr.write("\r\n");
        pr.flush();

        logResponse(200, "OK", mime_type, file);
        writeOutStreamFile(file);
    }

    private void sendFileDownload(File file, PrintWriter pr) throws IOException {
        pr.write("HTTP/1.0 200 OK\r\n");
        pr.write("Server: Java HTTP Server\r\n");
        pr.write("Date: " + new Date() + "\r\n");
        pr.write("Content-Type: application/octet-stream\r\n");
        pr.write("Content-Disposition: attachment; filename=\"" + file.getName() +
                "\"\r\n");
        pr.write("Content-Length: " + file.length() + "\r\n");
        pr.write("\r\n");
        pr.flush();

        logResponse(200, "OK", "application/octet-stream", file);
        writeOutStreamFile(file);
    }

    // Functions required for uplaoding text/image files
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

    private void hanldeUploadRequest(String file_name, DataInputStream dis) throws IOException {
        File upload_directory = new File(UPLOAD_DIRECTORY);
        if (!upload_directory.exists()) {
            upload_directory.mkdir();
        }

        File file = new File(upload_directory, file_name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytes_read;

            while ((bytes_read = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytes_read);
            }

            System.out.println("File " + file_name + " uploaded successfully.");
        }
    }
}
