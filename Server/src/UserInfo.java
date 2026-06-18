import java.io.Serializable;
import java.net.Socket;

public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String passwordHash;
    private transient Socket clientSocket; // 不序列化

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Socket getClientSocket() { return clientSocket; }
    public void setClientSocket(Socket clientSocket) { this.clientSocket = clientSocket; }
}