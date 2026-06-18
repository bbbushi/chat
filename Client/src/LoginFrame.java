import java.awt.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;

public class LoginFrame extends JFrame {
    private JTextField txtUsername, txtHost;
    private JPasswordField txtPassword;
    private JTextField txtPort;
    private JButton btnLogin, btnRegister;

    public LoginFrame() {
        setTitle("聊天室 - 登录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: 用户名
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0; gbc.gridwidth = 1;
        add(new JLabel("用户名:"), gbc);

        txtUsername = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(txtUsername, gbc);

        // Row 1: 密码
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0; gbc.gridwidth = 1;
        add(new JLabel("密码:"), gbc);

        txtPassword = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        add(txtPassword, gbc);

        // Row 2: 服务器 + 端口
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0; gbc.gridwidth = 1;
        add(new JLabel("服务器:"), gbc);

        txtHost = new JTextField("127.0.0.1", 12);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.weightx = 0.6; gbc.gridwidth = 1;
        add(txtHost, gbc);

        gbc.gridx = 2; gbc.gridy = 2;
        gbc.weightx = 0; gbc.gridwidth = 1;
        add(new JLabel("端口:"), gbc);

        txtPort = new JTextField("8888", 5);
        gbc.gridx = 3; gbc.gridy = 2;
        gbc.weightx = 0.4; gbc.gridwidth = 1;
        add(txtPort, gbc);

        // Row 3: 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        btnLogin = new JButton("登录");
        btnRegister = new JButton("注册");
        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 1.0; gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        add(buttonPanel, gbc);

        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> doRegister());

        pack();
        setSize(400, 220);
        setMinimumSize(new Dimension(300, 200));
    }

    private void doRegister() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户名和密码不能为空");
            return;
        }
        try (Socket socket = new Socket(txtHost.getText(), Integer.parseInt(txtPort.getText()));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            Message msg = new Message();
            msg.setType("register");
            msg.setUsername(username);
            msg.setPassword(password);
            out.writeObject(msg);
            out.flush();

            Message response = (Message) in.readObject();
            if (response.isSuccess()) {
                JOptionPane.showMessageDialog(this, response.getMessageText(), "注册成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, response.getMessageText(), "注册失败", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "连接服务器失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户名和密码不能为空");
            return;
        }
        try {
            Socket socket = new Socket(txtHost.getText(), Integer.parseInt(txtPort.getText()));
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Message msg = new Message();
            msg.setType("login");
            msg.setUsername(username);
            msg.setPassword(password);
            out.writeObject(msg);
            out.flush();

            Message response = (Message) in.readObject();
            if (response.isSuccess()) {
                // 登录成功，打开聊天窗口
                ChatFrame chatFrame = new ChatFrame(socket, out, in, username);
                chatFrame.setVisible(true);
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this, response.getMessageText(), "登录失败", JOptionPane.ERROR_MESSAGE);
                socket.close();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "连接服务器失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}