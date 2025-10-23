package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final File logFile = new File("file.log");

    public Server() throws IOException {
        loadSettings();
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
    }

    private void loadSettings() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("src/settings.txt"));
        for (String line : lines) {
            if (line.startsWith("port=")) {
                port = Integer.parseInt(line.substring(5));
            }
        }
    }

    public void start() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                pool.execute(handler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void broadcast(String message, String sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (!client.getUserName().equals(sender)) {
                    client.sendMessage(message);
                }
            }
        }
        logMessage(sender, message);
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

    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userName;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        public String getUserName() {
            return userName;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        @Override
        public void run() {
            try {
                out.println("Enter your name:");
                userName = in.readLine();
                broadcast(userName + " joined the chat", "Server");
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/exit")) {
                        break;
                    }
                    broadcast(line, userName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clients.remove(this);
                    socket.close();
                    broadcast(userName + " left the chat", "Server");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Server().start();
    }
}