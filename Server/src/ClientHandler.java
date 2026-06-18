import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String currentUser;
    private volatile boolean connected = true;

    // 共享数据（从 Server 传入）
    private static Map<String, UserInfo> users;
    private static Map<String, ClientHandler> onlineHandlers;
    private static Object userLock = new Object();
    private static Object onlineLock = new Object();
    private static Object groupLock = new Object();
    private static String userFile = "users.dat";

    // 群组数据（仅内存，服务器重启后丢失）
    private static Map<String, Set<String>> groups;

    static {
        users = new ConcurrentHashMap<>();
        onlineHandlers = new ConcurrentHashMap<>();
        groups = new ConcurrentHashMap<>();
        loadUsers();
    }

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadUsers() {
        File file = new File(userFile);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<String, UserInfo> loaded = (Map<String, UserInfo>) ois.readObject();
                users.putAll(loaded);
                // 清除客户端的 Socket 引用（因为反序列化后为 null）
                for (UserInfo ui : users.values()) {
                    ui.setClientSocket(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(userFile))) {
            oos.writeObject(users);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendMessage(Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
        } catch (IOException e) {
            connected = false;
            e.printStackTrace();
        }
    }

    private void broadcast(Message msg, ClientHandler exclude) {
        synchronized (onlineLock) {
            Iterator<Map.Entry<String, ClientHandler>> it = onlineHandlers.entrySet().iterator();
            while (it.hasNext()) {
                ClientHandler handler = it.next().getValue();
                if (handler == exclude) continue;
                if (!handler.connected) {
                    it.remove();
                    continue;
                }
                handler.sendMessage(msg);
            }
        }
    }

    private void updateOnlineListFor(ClientHandler handler) {
        List<String> onlineList = new ArrayList<>();
        synchronized (onlineLock) {
            onlineList.addAll(onlineHandlers.keySet());
        }
        Message msg = new Message();
        msg.setType("online_list");
        msg.setUsers(onlineList);
        handler.sendMessage(msg);
    }

    /**
     * 向指定用户发送其所在的群组列表。
     */
    private void sendGroupListTo(ClientHandler handler) {
        String username = handler.currentUser;
        if (username == null) return;
        List<String> userGroups = new ArrayList<>();
        synchronized (groupLock) {
            for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
                if (entry.getValue().contains(username)) {
                    userGroups.add(entry.getKey());
                }
            }
        }
        Message resp = new Message();
        resp.setType("group_list");
        resp.setUsers(userGroups);
        handler.sendMessage(resp);
    }

    /**
     * 向群组所有在线成员广播消息。
     * @param groupName 群组名称
     * @param msg       要发送的消息
     * @param exclude   排除的 handler（可为 null）
     */
    private void broadcastToGroup(String groupName, Message msg, ClientHandler exclude) {
        Set<String> members;
        synchronized (groupLock) {
            members = groups.get(groupName);
        }
        if (members == null) return;
        synchronized (onlineLock) {
            for (String member : members) {
                ClientHandler handler = onlineHandlers.get(member);
                if (handler == null || handler == exclude) continue;
                if (!handler.connected) {
                    onlineHandlers.remove(member);
                    continue;
                }
                handler.sendMessage(msg);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Message msg = (Message) input.readObject();
                if (msg == null) break;

                switch (msg.getType()) {
                    case "register":
                        synchronized (userLock) {
                            if (users.containsKey(msg.getUsername())) {
                                Message resp = new Message();
                                resp.setType("register_response");
                                resp.setSuccess(false);
                                resp.setMessageText("用户名已存在");
                                sendMessage(resp);
                            } else {
                                UserInfo ui = new UserInfo();
                                ui.setPasswordHash(hashPassword(msg.getPassword()));
                                users.put(msg.getUsername(), ui);
                                saveUsers();
                                Message resp = new Message();
                                resp.setType("register_response");
                                resp.setSuccess(true);
                                resp.setMessageText("注册成功");
                                sendMessage(resp);
                            }
                        }
                        break;

                    case "login":
                        synchronized (userLock) {
                            UserInfo ui = users.get(msg.getUsername());
                            if (ui == null || !ui.getPasswordHash().equals(hashPassword(msg.getPassword()))) {
                                Message resp = new Message();
                                resp.setType("login_response");
                                resp.setSuccess(false);
                                resp.setMessageText("用户名或密码错误");
                                sendMessage(resp);
                            } else if (onlineHandlers.containsKey(msg.getUsername())) {
                                Message resp = new Message();
                                resp.setType("login_response");
                                resp.setSuccess(false);
                                resp.setMessageText("用户已在别处登录");
                                sendMessage(resp);
                            } else {
                                currentUser = msg.getUsername();
                                ui.setClientSocket(socket);
                                synchronized (onlineLock) {
                                    onlineHandlers.put(currentUser, this);
                                }
                                Message resp = new Message();
                                resp.setType("login_response");
                                resp.setSuccess(true);
                                resp.setMessageText("登录成功");
                                sendMessage(resp);
                                // 广播加入消息
                                Message joinMsg = new Message();
                                joinMsg.setType("system");
                                joinMsg.setMessageText(currentUser + " 加入聊天室");
                                broadcast(joinMsg, this);
                                // 发送在线列表给新用户
                                updateOnlineListFor(this);
                            }
                        }
                        break;

                    case "chat":
                        if (currentUser != null) {
                            Message chatMsg = new Message();
                            chatMsg.setType("chat");
                            chatMsg.setFrom(currentUser);
                            chatMsg.setContent(msg.getContent());
                            broadcast(chatMsg, null);
                        }
                        break;

                    case "private_chat":
                        if (currentUser != null) {
                            ClientHandler targetHandler;
                            synchronized (onlineLock) {
                                targetHandler = onlineHandlers.get(msg.getTarget());
                            }
                            if (targetHandler != null) {
                                Message privateMsg = new Message();
                                privateMsg.setType("private");
                                privateMsg.setFrom(currentUser);
                                privateMsg.setContent(msg.getContent());
                                targetHandler.sendMessage(privateMsg);
                                this.sendMessage(privateMsg);
                            } else {
                                Message errorMsg = new Message();
                                errorMsg.setType("system");
                                errorMsg.setMessageText("无法发送私聊给 " + msg.getTarget() + "，对方不在线");
                                sendMessage(errorMsg);
                            }
                        }
                        break;

                    case "get_online_users":
                        if (currentUser != null) {
                            updateOnlineListFor(this);
                        }
                        break;

                    case "create_group":
                        if (currentUser != null) {
                            String groupName = msg.getTarget();
                            if (groupName == null || groupName.trim().isEmpty()) {
                                Message resp = new Message();
                                resp.setType("system");
                                resp.setMessageText("群组名称不能为空");
                                sendMessage(resp);
                                break;
                            }
                            groupName = groupName.trim();
                            synchronized (groupLock) {
                                if (groups.containsKey(groupName)) {
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("群组 \"" + groupName + "\" 已存在");
                                    sendMessage(resp);
                                } else {
                                    Set<String> members = ConcurrentHashMap.newKeySet();
                                    members.add(currentUser);
                                    groups.put(groupName, members);
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("群组 \"" + groupName + "\" 创建成功，你已自动加入");
                                    sendMessage(resp);
                                    sendGroupListTo(this);
                                }
                            }
                        }
                        break;

                    case "join_group":
                        if (currentUser != null) {
                            String groupName = msg.getTarget();
                            if (groupName == null || groupName.trim().isEmpty()) {
                                Message resp = new Message();
                                resp.setType("system");
                                resp.setMessageText("群组名称不能为空");
                                sendMessage(resp);
                                break;
                            }
                            groupName = groupName.trim();
                            synchronized (groupLock) {
                                Set<String> members = groups.get(groupName);
                                if (members == null) {
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("群组 \"" + groupName + "\" 不存在");
                                    sendMessage(resp);
                                } else if (members.contains(currentUser)) {
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("你已在群组 \"" + groupName + "\" 中");
                                    sendMessage(resp);
                                } else {
                                    members.add(currentUser);
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("你已加入群组 \"" + groupName + "\"");
                                    sendMessage(resp);
                                    sendGroupListTo(this);
                                    // 通知群内其他在线成员
                                    Message notify = new Message();
                                    notify.setType("system");
                                    notify.setMessageText(currentUser + " 加入了群组 \"" + groupName + "\"");
                                    broadcastToGroup(groupName, notify, this);
                                }
                            }
                        }
                        break;

                    case "leave_group":
                        if (currentUser != null) {
                            String groupName = msg.getTarget();
                            if (groupName == null || groupName.trim().isEmpty()) {
                                Message resp = new Message();
                                resp.setType("system");
                                resp.setMessageText("群组名称不能为空");
                                sendMessage(resp);
                                break;
                            }
                            groupName = groupName.trim();
                            synchronized (groupLock) {
                                Set<String> members = groups.get(groupName);
                                if (members == null || !members.contains(currentUser)) {
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("你不在群组 \"" + groupName + "\" 中");
                                    sendMessage(resp);
                                } else {
                                    members.remove(currentUser);
                                    Message resp = new Message();
                                    resp.setType("system");
                                    resp.setMessageText("你已离开群组 \"" + groupName + "\"");
                                    sendMessage(resp);
                                    sendGroupListTo(this);
                                    // 通知群内其他在线成员
                                    Message notify = new Message();
                                    notify.setType("system");
                                    notify.setMessageText(currentUser + " 离开了群组 \"" + groupName + "\"");
                                    broadcastToGroup(groupName, notify, this);
                                    // 清理空群组
                                    if (members.isEmpty()) {
                                        groups.remove(groupName);
                                    }
                                }
                            }
                        }
                        break;

                    case "list_groups":
                        if (currentUser != null) {
                            List<String> userGroups = new ArrayList<>();
                            synchronized (groupLock) {
                                for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
                                    if (entry.getValue().contains(currentUser)) {
                                        userGroups.add(entry.getKey());
                                    }
                                }
                            }
                            Message resp = new Message();
                            resp.setType("group_list");
                            resp.setUsers(userGroups);
                            sendMessage(resp);
                        }
                        break;

                    case "group_chat":
                        if (currentUser != null) {
                            String groupName = msg.getTarget();
                            if (groupName == null || groupName.trim().isEmpty()) {
                                Message resp = new Message();
                                resp.setType("system");
                                resp.setMessageText("群组名称不能为空");
                                sendMessage(resp);
                                break;
                            }
                            groupName = groupName.trim();
                            boolean canSend = false;
                            synchronized (groupLock) {
                                Set<String> members = groups.get(groupName);
                                if (members != null && members.contains(currentUser)) {
                                    canSend = true;
                                }
                            }
                            if (!canSend) {
                                Message resp = new Message();
                                resp.setType("system");
                                resp.setMessageText("你不在群组 \"" + groupName + "\" 中，无法发送消息");
                                sendMessage(resp);
                                break;
                            }
                            Message groupMsg = new Message();
                            groupMsg.setType("group_message");
                            groupMsg.setFrom(currentUser);
                            groupMsg.setTarget(groupName);
                            groupMsg.setContent(msg.getContent());
                            // 发给群内所有在线成员（包括自己，让发送者也能看到自己的消息）
                            broadcastToGroup(groupName, groupMsg, null);
                        }
                        break;

                    case "logout":
                        return;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            // 清理资源
            if (currentUser != null) {
                synchronized (onlineLock) {
                    onlineHandlers.remove(currentUser);
                }
                synchronized (userLock) {
                    UserInfo ui = users.get(currentUser);
                    if (ui != null) ui.setClientSocket(null);
                }
                Message leaveMsg = new Message();
                leaveMsg.setType("system");
                leaveMsg.setMessageText(currentUser + " 离开了聊天室");
                broadcast(leaveMsg, this);
            }
            try { socket.close(); } catch (IOException e) {}
        }
    }
}