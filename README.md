# Java 聊天室

网络实验作业，仅供学习使用。聊天室支持私聊、群聊等功能，支持同一网络下聊天。不同区域网络需要内网穿透。

## 功能

- 用户注册/登录（密码 SHA-256 哈希存储）
- 公聊消息广播
- 私聊（一对一消息）
- 群聊：支持创建群组、加入群组、离开群组、群内聊天
- 在线用户列表

> 群组数据仅保存在内存中，服务器重启后丢失。用户数据（`users.dat`）持久化不受影响。

## 架构概览

```
┌──────────┐    ┌──────────┐    ┌──────────┐
│  客户端A  │    │  客户端B  │    │  客户端C  │
│ (Client) │    │ (Client) │    │ (Client) │
└─────┬────┘    └─────┬────┘    └─────┬────┘
      │               │               │
      └───────────────┼───────────────┘
                      │    TCP Socket 连接
               ┌──────▼──────┐
               │   服务器      │
               │  (Server)   │
               │  端口 8888   │
               └─────────────┘
```

### 服务器端（Server）— 4 个核心文件

**Server.java — "接线员"**

服务端入口。打开 8888 端口，循环等待客户端连接，每来一个连接就启动一个新线程（ClientHandler）处理。

```java
ServerSocket serverSocket = new ServerSocket(8888);
while (true) {
    Socket clientSocket = serverSocket.accept();
    new Thread(new ClientHandler(clientSocket)).start();
}
```

**ClientHandler.java — "服务生"**

核心逻辑所在。`run()` 方法循环读取客户端消息，根据消息类型分派处理：

| 消息类型 | 服务器做什么 |
|---------|------------|
| `register` | 检查用户名是否重复 → 密码 SHA-256 哈希 → 存入 `users.dat` → 回复结果 |
| `login` | 验证用户名密码 → 检查是否已在线 → 加入在线列表 → 广播加入消息 |
| `chat` | 广播给所有在线用户 |
| `private_chat` | 找到目标用户，单独发送 |
| `get_online_users` | 返回当前在线人员名单 |
| `create_group` | 检查群名是否重复 → 创建群组并自动加入创建者 |
| `join_group` | 检查群是否存在/是否已加入 → 加入群组 → 通知群内其他成员 |
| `leave_group` | 检查是否在群内 → 移出群组 → 通知群内其他成员 → 空群自动删除 |
| `list_groups` | 返回用户所在的群组名列表 |
| `group_chat` | 向群内所有在线成员广播消息 |
| `logout` | 清理资源，广播离开消息 |

共享数据与锁：

- `users`（ConcurrentHashMap）— 持久化到 `users.dat`，受 `userLock` 保护
- `onlineHandlers`（ConcurrentHashMap）— 用户名 → ClientHandler 映射，受 `onlineLock` 保护
- `groups`（ConcurrentHashMap）— 群组名 → 成员集合，**仅内存**，受 `groupLock` 保护

三个锁相互独立，避免嵌套造成死锁。

**UserInfo.java — "用户档案"**

```java
public class UserInfo implements Serializable {
    private String passwordHash;           // SHA-256 哈希后的密码
    private transient Socket clientSocket; // 标记 transient，不参与序列化
}
```

**Message.java — "消息协议"**

客户端和服务器之间的通用语言，`Serializable` 对象，通过 Java 序列化在网络上传输。包含字段：`type`、`username`、`password`、`content`、`target`、`from`、`success`、`messageText`、`users`。

> ⚠️ `Message.java` 在客户端和服务端各有一份**一模一样的副本**。两边必须有相同的类定义（包括 `serialVersionUID`），否则反序列化失败。

### 客户端（Client）— 4 个核心文件

**Client.java — "启动器"**

```java
SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
```
在事件调度线程（EDT）上启动登录窗口，确保 Swing 线程安全。

**LoginFrame.java — "登录/注册窗口"**

Swing 窗口，包含用户名、密码、服务器地址、端口输入框，以及登录和注册按钮。

- **注册**：建立短连接，发送 `register` 消息，收到回复后断开
- **登录**：建立连接，发送 `login` 消息，成功后将连接传给 ChatFrame，登录窗口关闭

**ChatFrame.java — "聊天主窗口"**

UI 布局：

```
┌──────────────────────────────────┐
│  聊天室 - 用户名                  │
├─────────────────────┬────────────┤
│                     │ ┌────────┐ │
│   聊天消息显示区      │ │在线用户│ │
│   (JTextArea)       │ ├────────┤ │
│                     │ │ 群组   │ │
│                     │ │ (选项卡) │ │
│                     │ │        │ │
│                     │ │ 用户列表│ │
│                     │ │ 刷新/私聊│ │
│                     │ │        │ │
│                     │ │ 群组列表│ │
│                     │ │ 创建/加入│ │
│                     │ │ /离开   │ │
├─────────────────────┼────────────┤
│   消息输入框          │ [发送]     │
└─────────────────────┴────────────┘
```

右侧面板使用 `JTabbedPane` 分为两个选项卡："在线用户"和"群组"。

后台接收线程 `startReceiving()`：单独开一个线程不断读取服务器消息，根据消息类型更新界面：

| 消息类型 | 客户端处理 |
|---------|----------|
| `chat` | 显示 "用户名: 消息内容" |
| `private` | 显示 "[私聊] 用户名: 消息内容" |
| `system` | 显示 "【系统】XXX 加入了聊天室" |
| `online_list` | 更新右侧在线用户列表 |
| `group_message` | 显示 "[群聊-群名] 用户名: 消息内容" |
| `group_list` | 更新群组选项卡中的群组列表 |

> 为什么需要单独的线程？`in.readObject()` 在没消息时会阻塞，放主线程里界面会卡死。

**Message.java** — 与服务器端完全相同的消息协议类。

## 一个完整的"登录→聊天"流程

```
客户端                        服务器
  │                            │
  │──── register ────────────►│  注册（短连接，用完就断）
  │◄─── register_response ────│
  │                            │
  │──── login ────────────────►│  登录（这个连接会被保留）
  │◄─── login_response ───────│
  │◄─── system "X加入聊天室" ──│  服务器广播给其他在线用户
  │◄─── online_list ──────────│  发送在线名单
  │◄─── group_list ───────────│  发送用户的群组列表
  │                            │
  │──── chat "大家好" ────────►│  发送公聊消息
  │◄─── chat "Y: 你好" ───────│  收到别人的消息
  │                            │
  │──── private_chat ─────────►│  发送私聊
  │         (target=张三)       │
  │                            │──── private ───► 张三的客户端
  │                            │
  │──── create_group ─────────►│  创建群组
  │         (target=技术群)     │
  │◄─── system "创建成功" ─────│
  │                            │
  │──── join_group ───────────►│  加入群组
  │◄─── system "你已加入" ─────│
  │                            │──── system "B 加入了群组" ──► 群内其他成员
  │                            │
  │──── group_chat ───────────►│  群聊消息
  │◄─── group_message ────────│  自己也收到自己的消息
  │                            │──── group_message ──► 群内其他在线成员
  │                            │
  │──── leave_group ──────────►│  离开群组
  │                            │──── system "B 离开了群组" ──► 群内其他成员
  │                            │
  │──── logout ───────────────►│  退出
  │                            │──── system "X离开了" ──► 广播
  X  连接关闭                   X  清理资源
```

## 构建与运行

需要 **JDK 17+**。

### 启动服务端

**VS Code：** 在 Java Projects 视图中运行 `Server` 主类。

**命令行：**

```bash
cd Server
javac -d bin src/*.java
java -cp bin Server
```

服务器监听 8888 端口。

### 启动客户端

**VS Code：** 在 Java Projects 视图中运行 `Client` 主类。

**命令行：**

```bash
cd Client
javac -d bin src/*.java
java -cp bin Client
```

保持服务端运行，然后可启动多个客户端实例登录不同账号进行聊天测试。

### 修改端口

1. 打开 `Server/src/Server.java`，修改 `PORT` 常量（默认 8888）
2. 客户端在登录界面的端口栏填入相同值后再连接

## 使用说明

### 注册/登录流程

1. 首次使用点击"注册"，输入用户名和密码
2. 注册成功后点击"登录"
3. 登录成功将进入聊天窗口

### 私聊

- 在输入框使用 `/private 用户名 消息` 命令
- 或在右侧"在线用户"选项卡选中用户 → 点击"私聊"按钮 → 弹窗输入内容
- 或双击在线用户列表中的用户

### 群聊

- **创建群组**：点击右侧"群组"选项卡 → "创建群组" → 输入群组名称
- **加入群组**：点击"加入群组" → 输入已有群组名称
- **发送群消息**：
  - 双击群组列表中的群组名 → 输入消息
  - 或在消息输入框中使用 `/group 群组名 消息` 命令
- **离开群组**：选中群组 → 点击"离开群组" → 确认退出

> 群组数据仅保存在服务器内存中，服务器重启后需要重新创建和加入。

## 常见问题

### 端口被占用

如果启动时报端口占用，请先停止其他占用 8888 端口的程序，或修改服务端端口并保持客户端一致。

### 客户端无法连接

请确认服务端已启动，且本机防火墙未阻止 8888 端口。

### 用户数据文件位置

用户数据默认保存为服务端运行目录下的 `users.dat` 文件。

## 目录结构

```
SocketExperiment/
├── Server/
│   ├── src/
│   │   ├── Server.java          # 服务端入口
│   │   ├── ClientHandler.java   # 客户端连接处理（核心逻辑）
│   │   ├── UserInfo.java        # 用户信息
│   │   └── Message.java         # 消息协议
│   └── bin/                     # 编译输出
├── Client/
│   ├── src/
│   │   ├── Client.java          # 客户端入口
│   │   ├── LoginFrame.java      # 登录/注册窗口
│   │   ├── ChatFrame.java       # 聊天主窗口
│   │   └── Message.java         # 消息协议（与 Server 保持一致）
│   └── bin/                     # 编译输出
└── README.md
```

## 对初学者的学习建议

1. **先理解 `Message` 类** — 这是两端的"共同语言"，搞清楚每个字段的用途
2. **服务器端看 `ClientHandler.run()` 里的 switch** — 这是整个项目的"调度中心"
3. **客户端看 `ChatFrame.startReceiving()`** — 理解客户端如何接收和处理消息
4. **关键词理解**：
   - `Socket`：网络连接的"管道"，数据在里面流动
   - `ObjectInputStream` / `ObjectOutputStream`：把 Java 对象变成字节流发出去，再变回来
   - `Thread`：独立的执行路径，让程序能"同时"做多件事
   - `synchronized`：锁，防止多个线程同时修改同一份数据导致混乱
   - `transient`：标记某个字段"不参与序列化"
   - `Serializable`：标记一个类可以被序列化

如果想深入理解，可以试着：修改服务器端口 → 运行服务器 → 运行两个客户端 → 互相发消息看看效果。也可以进一步探索群组功能：创建群组 → 加入群组 → 群内聊天 → 离开群组。从"能跑起来"到"理解原理"是学习编程最有效的路径。
