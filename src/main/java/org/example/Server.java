package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private int port;
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final File logFile = new File("file.log");

    public Server() {
        configureLogging();
        try {
            loadSettings();
            serverSocket = new ServerSocket(port);
            logger.info("Server started on port " + port);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка при инициализации сервера", e);
            throw new RuntimeException("Failed to initialize server", e);
        }
    }

    private static void configureLogging() {
        try {
            LogManager.getLogManager().reset();
            Logger rootLogger = Logger.getLogger("");
            FileHandler fh = new FileHandler("server.log", true);
            fh.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(fh);
            rootLogger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Ошибка настройки логирования: " + e.getMessage());
        }
    }

    private void loadSettings() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("settings.txt"));
            for (String line : lines) {
                if (line.startsWith("port=")) {
                    port = Integer.parseInt(line.substring(5));
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Не удалось прочитать настройки, используется порт по умолчанию 12345", e);
            port = 12345;
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Некорректный формат порта в настройках, используется порт по умолчанию 12345", e);
            port = 12345;
        }
    }

    public void start() {
        logger.info("Ожидание подключений...");
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                pool.execute(handler);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Ошибка при приеме клиента", e);
            }
        }
    }


    private void broadcast(String message, String sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (!Objects.equals(client.getUserName(), sender)) {
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
            logger.log(Level.WARNING, "Ошибка при записи в лог-файл", e);
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        logger.info("Клиент отключился: " + client.getUserName());
        logMessage("Server", client.getUserName() + " отключился");
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            pool.shutdownNow();
            logger.info("Сервер остановлен");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка при остановке сервера", e);
        }
    }

    class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Ошибка при создании обработчика клиента", e);
                closeQuietly();
            }
        }

        public String getUserName() {
            return userName;
        }

        public void sendMessage(String message) {
            try {
                out.println(message);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка при отправке сообщения клиенту " + userName, e);
            }
        }

        @Override
        public void run() {
            try {
                out.println("Enter your name:");
                userName = in.readLine();
                if (userName == null || userName.trim().isEmpty()) {
                    userName = "Anonymous";
                }
                logMessage("Server", userName + " joined the chat");
                broadcast(userName + " joined the chat", "Server");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/exit")) {
                        break; // команда выхода
                    }
                    broadcast(line, userName);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Ошибка связи с клиентом " + userName, e);
            } finally {
                closeQuietly();
                broadcast(userName + " left the chat", "Server");
                logMessage("Server", userName + " left the chat");
                removeClient(this);
            }
        }

        private void closeQuietly() {
            try {
                if (in != null) in.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Ошибка при закрытии входных потоков клиента " + userName, e);
            }
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Ошибка при закрытии сокета клиента " + userName, e);
                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Запуск сервера...");
        try {
            new Server().start();
        } catch (RuntimeException e) {
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
}