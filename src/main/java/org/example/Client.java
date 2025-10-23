package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

public class Client {
    private String host;
    private int port;
    private String userName;
    private final File logFile = new File("file.log");

    public Client() throws IOException {
        loadSettings();
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
    }

    private void loadSettings() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("src/settings.txt"));
        for (String line : lines) {
            if (line.startsWith("host=")) {
                host = line.substring(5);
            } else if (line.startsWith("port=")) {
                port = Integer.parseInt(line.substring(5));
            }
        }
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Enter your name:");
            userName = userInput.readLine();

            // Отправляем имя серверу
            out.println(userName);

            // Поток для чтения сообщений с сервера
            Thread readerThread = new Thread(() -> {
                String line;
                try {
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                        logMessage(userName, line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            readerThread.start();

            String message;
            while ((message = userInput.readLine()) != null) {
                if (message.equalsIgnoreCase("/exit")) {
                    out.println("/exit");
                    break;
                }
                out.println(message);
                logMessage(userName, message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logMessage(String user, String message) {
        String time = LocalDateTime.now().toString();
        String logEntry = String.format("%s [%s]: %s%n", time, user, message);
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(logEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        new Client().start();
    }
}