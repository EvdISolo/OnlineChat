
    import org.example.Server;
    import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

    public class ChatServerTest {

        private static final int PORT = 12345;
        private Thread serverThread;

        @BeforeEach
        public void startServer() throws IOException {
            serverThread = new Thread(() -> {
                try {
                    Server.main(new String[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();

            // Дать серверу время запуститься
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @AfterEach
        public void stopServer() throws InterruptedException {
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
                serverThread.join();
            }
        }

        @Test
        public void testClientSendReceive() throws IOException, InterruptedException {
            // Подключение клиента
            Socket socket = new Socket("localhost", PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Отправляем имя
            in.readLine(); // "Введите ваше имя:"
            out.write("TestUser");
            out.newLine();
            out.flush();

            // Отправляем сообщение
            String testMessage = "Hello, world!";
            out.write(testMessage);
            out.newLine();
            out.flush();

            // Проверяем, что сообщение получено (должно прийти другое сообщение, т.к. сервер пересылает всем)
            String received = in.readLine();
            Assertions.assertTrue(received.contains("TestUser: " + testMessage));

            socket.close();
        }
    }

