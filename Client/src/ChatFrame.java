import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * 聊天室主窗口，负责 UI 展示、消息发送与接收。
 */
public class ChatFrame extends JFrame {
    /** Socket 连接与对象流，用于客户端与服务器通信。 */
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    /** 聊天显示、输入与操作按钮等 UI 组件。 */
    private JTextArea txtChat;
    private JTextField txtInput;
    private JButton btnSend;
    private DefaultListModel<String> onlineListModel;
    private JList<String> onlineList;
    private JButton btnRefresh;
    private JButton btnPrivate;

    /** 群组相关 UI 组件。 */
    private DefaultListModel<String> groupListModel;
    private JList<String> groupList;
    private JButton btnCreateGroup;
    private JButton btnJoinGroup;
    private JButton btnLeaveGroup;

    /** 退出时用于停止接收线程的标志。 */
    private volatile boolean running = true;

    /**
     * 构造聊天窗口。
     *
     * @param socket   客户端与服务器的连接
     * @param out      发送消息的对象输出流
     * @param in       接收消息的对象输入流
     * @param username 当前用户名
     */
    public ChatFrame(Socket socket, ObjectOutputStream out, ObjectInputStream in, String username) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.username = username;
        initUI();
        startReceiving();
        sendMessage("get_online_users", null, null);
        sendMessage("list_groups", null, null);
    }

    /** 初始化界面与事件绑定。 */
    private void initUI() {
        setTitle("聊天室 - " + username);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- 聊天显示区（左侧）---
        txtChat = new JTextArea();
        txtChat.setEditable(false);
        txtChat.setLineWrap(true);
        JScrollPane chatScroll = new JScrollPane(txtChat);

        // --- 右侧面板：使用选项卡切换在线用户 / 群组 ---
        JTabbedPane tabPane = new JTabbedPane();

        // 选项卡1：在线用户
        JPanel onlinePanel = new JPanel(new BorderLayout(0, 4));
        onlineListModel = new DefaultListModel<>();
        onlineList = new JList<>(onlineListModel);
        onlinePanel.add(new JScrollPane(onlineList), BorderLayout.CENTER);

        JPanel onlineBtnPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        btnRefresh = new JButton("刷新用户");
        btnPrivate = new JButton("私聊");
        onlineBtnPanel.add(btnRefresh);
        onlineBtnPanel.add(btnPrivate);
        onlinePanel.add(onlineBtnPanel, BorderLayout.SOUTH);

        tabPane.addTab("在线用户", onlinePanel);

        // 选项卡2：群组
        JPanel groupPanel = new JPanel(new BorderLayout(0, 4));
        groupListModel = new DefaultListModel<>();
        groupList = new JList<>(groupListModel);
        groupPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);

        JPanel groupBtnPanel = new JPanel(new GridLayout(3, 1, 0, 4));
        btnCreateGroup = new JButton("创建群组");
        btnJoinGroup = new JButton("加入群组");
        btnLeaveGroup = new JButton("离开群组");
        groupBtnPanel.add(btnCreateGroup);
        groupBtnPanel.add(btnJoinGroup);
        groupBtnPanel.add(btnLeaveGroup);
        groupPanel.add(groupBtnPanel, BorderLayout.SOUTH);

        tabPane.addTab("群组", groupPanel);

        // 左右分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, tabPane);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerSize(5);
        add(splitPane, BorderLayout.CENTER);

        // --- 底部输入栏 ---
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        txtInput = new JTextField();
        inputPanel.add(txtInput, BorderLayout.CENTER);
        btnSend = new JButton("发送");
        btnSend.setPreferredSize(new Dimension(90, 30));
        inputPanel.add(btnSend, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // --- 事件绑定 ---
        btnRefresh.addActionListener(e -> sendMessage("get_online_users", null, null));
        btnPrivate.addActionListener(e -> startPrivateChat());
        btnSend.addActionListener(e -> sendChat());
        txtInput.addActionListener(e -> sendChat());
        onlineList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) startPrivateChat();
            }
        });

        // 群组相关事件绑定
        btnCreateGroup.addActionListener(e -> {
            String groupName = JOptionPane.showInputDialog(this,
                    "请输入要创建的群组名称:", "创建群组", JOptionPane.PLAIN_MESSAGE);
            if (groupName != null && !groupName.trim().isEmpty()) {
                sendMessage("create_group", groupName.trim(), null);
                sendMessage("list_groups", null, null);
            }
        });

        btnJoinGroup.addActionListener(e -> {
            String groupName = JOptionPane.showInputDialog(this,
                    "请输入要加入的群组名称:", "加入群组", JOptionPane.PLAIN_MESSAGE);
            if (groupName != null && !groupName.trim().isEmpty()) {
                sendMessage("join_group", groupName.trim(), null);
                sendMessage("list_groups", null, null);
            }
        });

        btnLeaveGroup.addActionListener(e -> {
            String selected = groupList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this,
                        "请先从群组列表中选择一个群组", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定要离开群组 \"" + selected + "\" 吗?",
                    "离开群组", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                sendMessage("leave_group", selected, null);
                sendMessage("list_groups", null, null);
            }
        });

        // 双击群组列表 → 发送群聊消息
        groupList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = groupList.getSelectedValue();
                    if (selected != null) {
                        String message = JOptionPane.showInputDialog(ChatFrame.this,
                                "发送到群组 \"" + selected + "\":", "群聊", JOptionPane.PLAIN_MESSAGE);
                        if (message != null && !message.trim().isEmpty()) {
                            sendMessage("group_chat", selected, message.trim());
                        }
                    }
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                logout();
            }
        });

        setSize(750, 520);
        setMinimumSize(new Dimension(500, 350));
    }

    /** 与选中的在线用户发起私聊。 */
    private void startPrivateChat() {
        String target = onlineList.getSelectedValue();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "请先从在线用户列表中选择一个用户", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String message = JOptionPane.showInputDialog(this, "发送给 " + target + ":", "私聊", JOptionPane.PLAIN_MESSAGE);
        if (message != null && !message.trim().isEmpty()) {
            sendMessage("private_chat", target, message);
        }
    }

    /** 从输入框发送聊天消息，支持私聊指令。 */
    private void sendChat() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;
        if (text.startsWith("/private ")) {
            String[] parts = text.split(" ", 3);
            if (parts.length >= 3) {
                sendMessage("private_chat", parts[1], parts[2]);
            } else {
                appendChat("系统提示: 私聊格式错误，正确格式: /private 用户名 消息", true);
            }
        } else if (text.startsWith("/group ")) {
            String[] parts = text.split(" ", 3);
            if (parts.length >= 3) {
                sendMessage("group_chat", parts[1], parts[2]);
            } else {
                appendChat("系统提示: 群聊格式错误，正确格式: /group 群组名 消息", true);
            }
        } else {
            sendMessage("chat", null, text);
        }
        txtInput.setText("");
    }

    /**
     * 组装并发送消息对象到服务器。
     *
     * @param type    消息类型
     * @param target  目标用户（可为空）
     * @param content 消息内容（可为空）
     */
    private void sendMessage(String type, String target, String content) {
        try {
            Message msg = new Message();
            msg.setType(type);
            if (target != null) msg.setTarget(target);
            if (content != null) msg.setContent(content);
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            appendChat("发送失败，连接可能已断开", true);
        }
    }

    /** 启动后台线程接收服务器消息。 */
    private void startReceiving() {
        new Thread(() -> {
            while (running) {
                try {
                    Message msg = (Message) in.readObject();
                    if (msg == null) break;
                    switch (msg.getType()) {
                        case "chat":
                            appendChat(msg.getFrom() + ": " + msg.getContent(), false);
                            break;
                        case "private":
                            appendChat("[私聊] " + msg.getFrom() + ": " + msg.getContent(), false);
                            break;
                        case "system":
                            appendChat("【系统】" + msg.getMessageText(), true);
                            break;
                        case "online_list":
                            updateOnlineList(msg.getUsers());
                            break;
                        case "group_message":
                            appendChat("[群聊-" + msg.getTarget() + "] " + msg.getFrom() + ": " + msg.getContent(), false);
                            break;
                        case "group_list":
                            updateGroupList(msg.getUsers());
                            break;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    if (running) {
                        appendChat("与服务器的连接已断开", true);
                        running = false;
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "连接已断开", "错误", JOptionPane.ERROR_MESSAGE);
                            logout();
                        });
                    }
                    break;
                }
            }
        }).start();
    }

    /**
     * 追加聊天内容并自动滚动到底部。
     *
     * @param text     文本内容
     * @param isSystem 是否系统提示
     */
    private void appendChat(String text, boolean isSystem) {
        SwingUtilities.invokeLater(() -> {
            txtChat.append(text + "\n");
            txtChat.setCaretPosition(txtChat.getDocument().getLength());
        });
    }

    /**
     * 更新在线用户列表。
     *
     * @param users 在线用户集合
     */
    private void updateOnlineList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            onlineListModel.clear();
            for (String user : users) {
                if (!user.equals(username)) {
                    onlineListModel.addElement(user);
                }
            }
        });
    }

    /**
     * 更新用户的群组列表。
     * @param groups 群组名称集合
     */
    private void updateGroupList(List<String> groups) {
        SwingUtilities.invokeLater(() -> {
            groupListModel.clear();
            if (groups != null) {
                for (String group : groups) {
                    groupListModel.addElement(group);
                }
            }
        });
    }

    /** 退出并释放资源。 */
    private void logout() {
        running = false;
        try {
            sendMessage("logout", null, null);
            out.close();
            in.close();
            socket.close();
        } catch (IOException e) {}
        System.exit(0);
    }
}