import java.io.*;
import java.net.*;

public class Server {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("服务器启动，监听端口 " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("新连接来自 " + clientSocket.getRemoteSocketAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}