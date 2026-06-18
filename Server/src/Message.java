import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;          // register, login, chat, private_chat, get_online_users, logout, create_group, join_group, leave_group, list_groups, group_chat
    private String username;
    private String password;
    private String content;
    private String target;
    private boolean success;
    private String messageText;
    private List<String> users;
    private String from;

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = users; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}