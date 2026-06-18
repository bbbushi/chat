## 开始使用

欢迎来到 VS Code Java 世界。以下说明帮助你快速上手在 VS Code 中编写和运行 Java 代码。

## 功能

- 用户注册/登录（密码 SHA-256 哈希存储）
- 公聊消息广播
- 私聊（一对一消息）
- 群聊：支持创建群组、加入群组、离开群组、群内聊天
- 在线用户列表

> 群组数据仅保存在内存中，服务器重启后丢失。用户数据（`users.dat`）持久化不受影响。

## 使用说明

### 前置条件

- 已安装 JDK 17+
- VS Code 已安装 Java 相关扩展，或终端可用 `javac`/`java`

### 运行（VS Code）

1. 打开工作区。
2. 在 Java Projects 视图中运行 `Server` 主类。
3. 服务器监听 8888 端口。

### 运行（命令行）

在工作区根目录执行：

```bash
cd Server
javac -d bin src/*.java
java -cp bin Server
```

保持服务端运行，然后在 Client 模块启动客户端。

### 修改端口

1. 打开 `Server/src/Server.java`。
2. 修改 `PORT` 常量（默认 8888）。
3. 客户端请在登录界面将端口改为相同值后再连接。

## 目录结构

工作区默认包含两个目录：

- `src`：源码目录
- `lib`：依赖目录

编译输出默认生成在 `bin` 目录。

> 如需自定义目录结构，请打开 `.vscode/settings.json` 并修改相关设置。

## 依赖管理

`JAVA PROJECTS` 视图可用于管理依赖。更多说明见：
https://github.com/microsoft/vscode-java-dependency#manage-dependencies

## 常见问题

### 端口被占用

如果启动时报端口占用，请先停止其他占用 8888 端口的程序，或修改服务端端口并保持客户端一致。

### 客户端无法连接

请确认服务端已启动，且本机防火墙未阻止 8888 端口。

### 用户数据文件位置

用户数据默认保存为服务端运行目录下的 `users.dat` 文件。
