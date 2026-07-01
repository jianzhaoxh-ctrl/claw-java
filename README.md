# ClawDesktop — Java 桌面 AI 助手

> 基于 OpenClaw 架构的 Java 21 + JavaFX 21 桌面端个人 AI 助手，支持多 LLM Provider、工具调用、定时任务、插件系统、多通道消息接入。

## 目录

- [项目来源](#项目来源)
- [技术栈](#技术栈)
- [项目架构](#项目架构)
- [核心模块详解](#核心模块详解)
- [软件运行临时目录](#软件运行临时目录)
- [启动方式](#启动方式)
- [配置说明](#配置说明)
- [打包发布](#打包发布)
- [注意事项](#注意事项)

---

## 项目来源

本项目是 [OpenClaw](https://github.com/openclaw/openclaw)（Node.js/TypeScript 实现的个人 AI 助手）的 **Java 移植版**。原版 OpenClaw 是一个运行在终端/服务端的 AI Agent，本项目将其核心架构移植到 Java 桌面环境，并添加了 JavaFX 图形界面、系统托盘、全局快捷键等桌面集成能力。

- **原版项目**：OpenClaw (Node.js)
- **本项目**：ClawDesktop (Java 21)
- **版本**：0.1.0-SNAPSHOT
- **包名**：`com.openclaw.desktop`

---

## 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 语言 | Java 21 | 使用 record、sealed、switch 表达式等新特性 |
| UI 框架 | JavaFX 21 | 桌面 GUI |
| UI 主题 | AtlantaFX 2.0.1 | 提供 Primer Dark/Light、Cupertino、Dracula 等现代主题 |
| 响应式 | Project Reactor 3.6 | `Mono`/`Flux` 驱动异步流程 |
| HTTP 服务器 | Reactor Netty 1.1 | 内置 Gateway HTTP + WebSocket 服务 |
| JSON | Jackson 2.17 | 序列化/反序列化 |
| 配置 | Typesafe Config (HOCON) | `application.conf` + 环境变量覆盖 |
| 依赖注入 | Google Guice 7.0 | （引入但主要用于插件 Context） |
| 数据库 | SQLite (sqlite-jdbc 3.45) | 记忆存储 + Cron 任务持久化 |
| 日志 | SLF4J + Logback | 控制台 + 滚动文件日志 |
| 邮件 | Angus Mail (Jakarta Mail 2.1) | 邮件通道支持 |
| 构建 | Maven | shade 插件打 fat jar，jpackage 打原生安装包 |
| 测试 | JUnit 5 | 覆盖核心模块 |

---

## 项目架构

```
┌──────────────────────────────────────────────────────────┐
│                     DesktopApplication                     │
│                   (JavaFX 入口 + UI 层)                    │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ ChatView │  │SettingsView│ │ThemeManager│ │SessionList│ │
│  └────┬─────┘  └─────┬────┘  └──────────┘  └───────────┘ │
│       │               │                                    │
├───────┼───────────────┼──────────────────────────────────┤
│       ▼               ▼                                    │
│  ┌─────────────────────────────────────────────────────┐ │
│  │                    Agent (核心)                       │ │
│  │  ┌──────────┐  ┌───────────┐  ┌────────────────┐   │ │
│  │  │AgentLoop │  │AgentConfig│  │ApprovalManager │   │ │
│  │  └────┬─────┘  └───────────┘  └────────────────┘   │ │
│  │       │                                              │ │
│  │  ┌────▼──────────────────────────────────────────┐  │ │
│  │  │           LLM Provider Registry               │  │ │
│  │  │ OpenAI │ Anthropic │ DeepSeek │ Qwen │ Ollama │  │ │
│  │  │ Google │ Groq │ Mistral │ ... (20+ providers) │  │ │
│  │  └───────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ToolRegist│  │SessionMgr│  │CronSched │  │SkillMgr  │ │
│  │  (9+工具) │  │ (会话管理)│  │(定时任务) │  │(技能系统) │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │PluginMgr │  │McpClient │  │EventBus  │  │ContextEng│ │
│  │(插件系统) │  │  (MCP)   │  │(事件总线) │  │(上下文)  │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              Gateway Server (Netty HTTP)             │ │
│  │  /v1/chat  /v1/sessions  /v1/tools  /v1/config  /ws │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │              Desktop Integration                     │ │
│  │ Tray │ Notification │ Hotkey │ Clipboard │ AutoStart│ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 源码包结构

```
src/main/java/com/openclaw/desktop/
├── ClawDesktop.java          # 核心启动类（无 UI，纯后端组装）
├── ClawDesktopApp.java       # 简化版启动类（无 UI）
├── DesktopApplication.java   # JavaFX 主入口（完整桌面应用）
├── agent/                    # Agent 核心
│   ├── Agent.java            #   AI 对话循环 + 工具调用
│   ├── AgentLoop.java        #   v2.0 生命周期事件流
│   ├── AgentConfig.java      #   Agent 配置
│   ├── AgentState.java       #   Agent 状态管理
│   ├── AgentContext.java     #   运行上下文
│   ├── AgentEvent.java       #   生命周期事件
│   ├── AgentMessage.java     #   消息封装
│   ├── GoalTracker.java      #   目标追踪
│   ├── SubAgentManager.java  #   子 Agent 管理
│   ├── ReasoningLevel.java   #   推理级别枚举
│   ├── AbortSignal.java      #   中断信号
│   └── ToolCall.java         #   工具调用记录
├── approval/                 # 工具审批系统
│   ├── ApprovalManager.java  #   审批管理器
│   ├── ApprovalPolicy.java   #   审批策略
│   ├── ApprovalRequest.java  #   审批请求
│   └── ApprovalResult.java   #   审批结果
├── autoreply/                # 自动回复引擎
├── channel/                  # 消息通道
│   ├── Channel.java          #   通道接口
│   ├── ChannelRegistry.java  #   通道注册表
│   ├── discord/              #   Discord 通道
│   ├── feishu/               #   飞书通道
│   ├── mail/                 #   邮件通道
│   ├── matrix/               #   Matrix 通道
│   ├── qqbot/                #   QQ 机器人通道
│   ├── slack/                #   Slack 通道
│   ├── telegram/             #   Telegram 通道
│   ├── webchat/              #   Web 聊天通道
│   ├── webhook/              #   Webhook 通道
│   └── wechat/               #   企业微信通道
├── command/                  # 命令系统 (/help, /config 等)
│   ├── impl/                 #   具体命令实现
│   └── CommandManager.java
├── config/                   # 配置系统
│   ├── ClawConfig.java       #   主配置 record
│   ├── ConfigLoader.java     #   HOCON 加载器
│   ├── ConfigReloadManager.java  #热重载
│   └── ConfigWatcher.java    #   文件监听
├── context/                  # 上下文管理
│   ├── ContextEngine.java    #   Token 估算 + 压缩
│   ├── TokenEstimator.java   #   Token 估算器
│   └── CompactConfig.java    #   压缩配置
├── cron/                     # 定时任务
│   ├── CronScheduler.java    #   调度器
│   ├── CronExecutor.java     #   执行器
│   ├── CronJobStore.java     #   SQLite 持久化
│   ├── CronExpression.java   #   Cron 表达式解析
│   └── CronSchedule.java     #   调度计划 (sealed)
├── desktop/                  # 桌面集成
│   ├── SystemTrayManager.java    # 系统托盘
│   ├── NotificationManager.java  # 通知
│   ├── GlobalHotkeyManager.java  # 全局快捷键
│   ├── WindowManager.java        # 窗口管理
│   ├── ClipboardManager.java     # 剪贴板
│   ├── AutoStartManager.java     # 开机自启
│   ├── DesktopOps.java           # 桌面操作
│   └── SystemInfo.java           # 系统信息
├── flow/                     # 流程引擎
├── gateway/                  # HTTP 网关
│   ├── api/                  #   REST API
│   │   ├── ChatApi.java      #     /v1/chat
│   │   ├── ConfigApi.java    #     /v1/config
│   │   ├── HealthApi.java    #     /health
│   │   ├── SessionApi.java   #     /v1/sessions
│   │   └── ToolApi.java      #     /v1/tools
│   ├── server/               #   服务器
│   │   ├── GatewayServer.java    # Netty HTTP 服务器
│   │   └── GatewayRouter.java    # 路由配置
│   └── ws/                  #   WebSocket
├── hook/                     # Hook 系统
├── infra/                    # 基础设施
│   ├── errors/               #   异常
│   ├── health/               #   健康检查
│   └── logging/              #   日志
├── keystore/                 # 密钥管理
│   ├── KeyManager.java       #   密钥管理器
│   ├── KeyEncryptor.java     #   加密器
│   └── KeyStore.java         #   密钥存储
├── llm/                      # LLM 抽象层
│   ├── LlmProvider.java      #   Provider 接口
│   ├── LlmProviderRegistry.java  # 注册表
│   ├── LlmRequest.java       #   请求
│   ├── LlmResponse.java      #   响应
│   ├── LlmEvent.java         #   流式事件
│   ├── Message.java          #   消息（sealed）
│   ├── MessageContent.java   #   消息内容（sealed）
│   └── provider/             #   20+ Provider 实现
│       ├── OpenAiProvider.java
│       ├── AnthropicProvider.java
│       ├── DeepSeekProvider.java
│       ├── QwenProvider.java       # 通义千问
│       ├── OllamaProvider.java     # 本地模型
│       ├── GoogleProvider.java
│       ├── GroqProvider.java
│       └── ... (共 20+ 个)
├── mcp/                      # MCP 协议客户端
│   ├── McpClient.java        #   MCP 客户端
│   ├── McpClientManager.java #   多服务器管理
│   ├── McpTool.java          #   MCP 工具
│   └── McpToolAdapter.java   #   工具适配器
├── media/                    # 视觉/媒体
├── memory/                   # 记忆数据库 (SQLite)
├── pairing/                  # 设备配对
├── plugin/                   # 插件系统
│   ├── ClawPlugin.java       #   插件接口 (SPI)
│   ├── PluginManager.java    #   插件管理器
│   ├── PluginLoader.java     #   类加载器
│   ├── PluginInstaller.java  #   插件安装器
│   ├── EventBus.java         #   事件总线
│   └── example/EchoPlugin.java  # 示例插件
├── rag/                      # RAG 检索增强
│   ├── RAGEngine.java
│   ├── EmbeddingService.java
│   ├── SemanticSearch.java
│   └── VectorStore.java
├── session/                  # 会话管理
│   ├── Session.java          #   会话对象
│   ├── SessionManager.java   #   管理器
│   ├── SessionStore.java     #   磁盘持久化
│   ├── SessionKey.java       #   会话标识
│   └── Transcript.java       #   对话记录
├── skill/                    # 技能系统
│   ├── SkillManager.java     #   技能管理器
│   ├── SkillRegistry.java    #   注册表
│   ├── SkillMdParser.java    #   SKILL.md 解析器
│   └── SkillDefinition.java  #   技能定义
├── task/                     # 任务管理
├── tool/                     # 工具系统
│   ├── Tool.java             #   工具接口
│   ├── ToolRegistry.java     #   工具注册表
│   └── core/                 #   内置工具
│       ├── ReadFileTool.java
│       ├── WriteFileTool.java
│       ├── EditFileTool.java
│       ├── DeleteFileTool.java
│       ├── ListFilesTool.java
│       ├── ShellExecTool.java
│       ├── WebSearchTool.java
│       ├── WebFetchTool.java
│       ├── ProcessTool.java
│       ├── BrowserAutomationTool.java
│       ├── CodeSandboxTool.java
│       ├── ImageGenerationTool.java
│       ├── VideoGenerationTool.java
│       ├── MediaUnderstandingTool.java
│       ├── DocumentExtractTool.java
│       ├── DiffComparisonTool.java
│       ├── CalendarTool.java
│       └── EmailTool.java
├── trajectory/               # 轨迹记录/回放
├── tts/                      # 语音合成
│   ├── TtsProvider.java
│   ├── ElevenLabsTtsProvider.java
│   ├── AzureSpeechTtsProvider.java
│   └── LocalTtsProvider.java
├── types/                    # JSON 类型
└── ui/                       # UI 层
    ├── ClawDesktopUI.java
    ├── ShortcutManager.java
    ├── chat/                 #   聊天界面
    ├── md/                   #   Markdown 渲染
    ├── session/              #   会话面板
    ├── settings/             #   设置界面
    └── theme/                #   主题管理
```

---

## 核心模块详解

### 1. Agent（AI 对话核心）

- **Agent.java**：封装 AI 对话循环，支持同步/流式聊天，最多 20 轮工具调用
- **AgentLoop.java**：v2.0 重构版，完整生命周期事件流（AgentStart → TurnStart → MessageStart → TextDelta → ToolCallStart → ... → AgentEnd）
- 工具调用流程：用户消息 → LLM → 返回 tool_calls → 执行工具 → 结果回传 → 再次调用 LLM → 直到无 tool_calls

### 2. LLM Provider（20+ 模型供应商）

支持的 Provider：
- **国际**：OpenAI、Anthropic、Google、Groq、Mistral、Cohere、xAI、Perplexity、HuggingFace、Nvidia、Together、Fireworks、Cerebras
- **国内**：DeepSeek、通义千问(Qwen)、MiniMax、Moonshot(月之暗面)、StepFun、SiliconFlow、百度千帆、火山引擎
- **本地**：Ollama、LM Studio、vLLM
- **云平台**：Azure OpenAI、AWS Bedrock、OpenRouter、LiteLLM

### 3. Tool（工具系统）

内置 18 个工具：
| 工具 | 说明 |
|------|------|
| read_file / write_file / edit_file / delete_file / list_files | 文件操作 |
| shell_exec | Shell 命令执行 |
| process | 进程管理 |
| web_search / web_fetch | 网络搜索/抓取 |
| browser_automation | 浏览器自动化 |
| code_sandbox | 代码沙箱 |
| image_generation / video_generation / media_understanding | AI 媒体工具 |
| document_extract / diff_comparison | 文档工具 |
| calendar / email | 信息工具 |

### 4. Gateway（HTTP 网关）

基于 Reactor Netty，默认端口 **7180**（HTTP）+ **7181**（WebSocket）：

| 路径 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/v1/chat` | POST | 聊天接口 |
| `/v1/chat/stream` | GET | 流式聊天 |
| `/v1/sessions` | GET | 会话列表 |
| `/v1/sessions/{key}` | GET/DELETE | 会话详情/删除 |
| `/v1/sessions/{key}/reset` | POST | 重置会话 |
| `/v1/tools` | GET | 工具列表 |
| `/v1/tools/{name}` | GET | 工具详情 |
| `/v1/config` | GET/PATCH | 配置读取/更新 |
| `/ws` | WebSocket | 实时通信 |

### 5. Cron（定时任务）

- 基于 SQLite 持久化，应用重启后自动恢复
- 支持三种调度类型：`At`（一次性）、`Every`（间隔）、`Cron`（Cron 表达式）
- 支持 `SYSTEM_EVENT` 和 `AGENT_TURN` 两种触发负载

### 6. 插件系统

- 基于 Java SPI（ServiceLoader）发现内置插件
- 支持外部 JAR 插件热加载/卸载
- 通过 `PluginContext` 注入配置、工具注册表、会话管理器等
- `EventBus` 提供插件间通信

### 7. 技能系统

- 从 classpath 加载内置技能（`skills/*/SKILL.md`）
- 从 `~/.clawdesktop/skills/` 加载外部技能
- 基于触发词匹配，动态注入到 Agent System Prompt
- 内置技能：`file-operations`、`web-search`、`code-review`

### 8. 多通道接入

支持的通道：Discord、飞书、邮件、Matrix、QQ 机器人、Slack、Telegram、WebChat、Webhook、企业微信

### 9. 桌面集成

- **系统托盘**：最小化到托盘、右键菜单
- **通知**：系统通知 + 点击回调
- **窗口管理**：多窗口支持
- **剪贴板**：剪贴板监听
- **开机自启**：跨平台自启动配置

---

## 软件运行临时目录

ClawDesktop 在运行时会在以下位置创建临时目录和文件：

### 1. 用户数据目录：`~/.clawdesktop/`

这是**主数据目录**，在项目启动时**会自动创建**。包含：

```
~/.clawdesktop/
├── application.conf           # 用户配置文件（首次运行从 classpath 复制/生成）
├── memory.db                  # 记忆数据库 (SQLite)
├── cron.db                    # Cron 任务数据库 (SQLite)
├── sessions/                  # 会话持久化目录
│   ├── main_default.json      #   主会话文件
│   ├── main_<agentId>.json    #   其他会话
│   └── archive/               #   归档目录（>7天自动压缩为 .json.gz）
├── skills/                    # 外部技能目录
│   └── <skill-name>/
│       └── SKILL.md
└── plugins/                   # 外部插件目录
    └── <plugin>.jar
```

**自动创建逻辑**：
- `~/.clawdesktop/` → 由 `SkillManager` 和 `PluginManager` 在初始化时通过 `Paths.get(user.home, ".clawdesktop", ...)` 创建
- `sessions/` 和 `sessions/archive/` → 由 `SessionStore` 构造函数中 `Files.createDirectories()` 创建
- `memory.db` → 由 `MemoryDatabase.initialize()` 中 `Files.createDirectories(path.getParent())` 创建父目录
- `cron.db` → 由 `CronJobStore.initialize()` 中 `Files.createDirectories(path.getParent())` 创建父目录

### 2. 工作目录下的相对路径目录

根据 `logback.xml` 和默认配置，项目运行时还会在工作目录（即 `java -jar` 的执行目录）下创建：

```
./data/
├── logs/                      # 日志目录
│   ├── claw.log               # 当前日志
│   └── claw.YYYY-MM-DD.N.log.gz  # 滚动日志（10MB/文件，保留30天，上限500MB）
└── memory/                    # 默认记忆数据库目录（当使用默认配置时）
    └── claw.db                # 默认记忆数据库
```

**注意**：`data/logs/` 由 Logback 的 `RollingFileAppender` 自动创建。当配置文件中 `memory.db-path` 使用相对路径（如默认值 `data/memory/claw.db`）时，`data/memory/` 由 `MemoryDatabase` 创建。

### 3. Cron 数据库路径推导

Cron 数据库路径由 `ClawDesktop.java` 中动态推导：
```java
var dbPath = Paths.get(config.memory().dbPath()).getParent();
var cronDb = dbPath != null ? dbPath.resolve("cron.db").toString() : "data/cron.db";
```
即与记忆数据库放在同一目录下。

### 4. 临时目录总结

| 路径 | 创建者 | 创建时机 | 是否自动创建 |
|------|--------|----------|:---:|
| `~/.clawdesktop/` | SkillManager / PluginManager | 启动时 | ✅ |
| `~/.clawdesktop/sessions/` | SessionStore 构造函数 | 启动时 | ✅ |
| `~/.clawdesktop/sessions/archive/` | SessionStore 构造函数 | 启动时 | ✅ |
| `~/.clawdesktop/skills/` | SkillManager.initSkillSystem() | 启动时 | ✅ |
| `~/.clawdesktop/plugins/` | PluginManager.initPluginSystem() | 启动时 | ✅ |
| `~/.clawdesktop/memory.db` | MemoryDatabase.initialize() | 首次写入时 | ✅（父目录） |
| `~/.clawdesktop/cron.db` | CronJobStore.initialize() | 启动时 | ✅（父目录） |
| `data/logs/` | Logback RollingFileAppender | 首次写日志时 | ✅ |
| `data/memory/` | MemoryDatabase（使用默认配置时） | 初始化时 | ✅ |

---

## 启动方式

### 方式一：JavaFX 桌面应用（推荐）

```bash
# 编译
mvn clean package -DskipTests

# 运行（方式 1：javafx-maven-plugin）
mvn javafx:run

# 运行（方式 2：直接运行 fat jar）
java -jar target/claw-java-0.1.0-SNAPSHOT.jar

# 运行（方式 3：直接运行主类）
java -cp target/claw-java-0.1.0-SNAPSHOT.jar com.openclaw.desktop.DesktopApplication
```

主类：`com.openclaw.desktop.DesktopApplication`（JavaFX 入口）

### 方式二：无 UI 后端模式

```bash
# 运行 ClawDesktopApp（简化版，无 JavaFX UI）
java -cp target/claw-java-0.1.0-SNAPSHOT.jar com.openclaw.desktop.ClawDesktopApp

# 运行 ClawDesktop（完整后端，含 Cron/Plugin/Skill/MCP）
# 需要自定义启动脚本，因为 ClawDesktop 无 main 方法
```

### 方式三：开发模式（IDE）

在 IntelliJ IDEA 中：
1. 导入为 Maven 项目
2. 运行 `DesktopApplication.main()` 即可启动

---

## 配置说明

### 配置文件位置

配置加载优先级（从高到低）：
1. **环境变量**：`CLAW_` 前缀的环境变量覆盖（如 `CLAW_LLM_PROVIDERS_QWEN_APIKEY`）
2. **用户配置**：`~/.clawdesktop/application.conf`
3. **Classpath 默认**：`src/main/resources/application.conf`

### 配置项

```hocon
gateway {
  port = 7180           # HTTP 端口
  ws-port = 7181        # WebSocket 端口
  bind-address = "127.0.0.1"
  cors-enabled = true
}

agent {
  id = "default"
  name = "ClawDesktop"
  model-id = "qwen-turbo"        # 模型 ID
  system-prompt = "..."
  reasoning-level = "off"         # off / low / medium / high
  max-tokens = 4096
  temperature = 0.7
}

llm {
  default-provider = "qwen"       # 默认 LLM Provider

  providers {
    openai {
      apikey = ""                 # OpenAI API Key
      base-url = "https://api.openai.com/v1"
    }
    anthropic {
      apikey = ""
      base-url = "https://api.anthropic.com"
    }
    deepseek {
      apikey = ""
      base-url = "https://api.deepseek.com/v1"
    }
    qwen {
      apikey = ""                 # 通义千问 API Key
      base-url = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    }
    ollama {
      base-url = "http://localhost:11434"   # 本地 Ollama（无需 apikey）
    }
  }
}

memory {
  db-path = "${user.home}/.clawdesktop/memory.db"
  embedding-enabled = false
}
```

> **提示**：API Key 支持三种写法：`apiKey`、`api-key`、`apikey`，ConfigLoader 均可识别。

---

## 打包发布

### Windows（.msi 安装包）

```bash
# 前置条件：JDK 21+、WiX Toolset 3.x、Maven
scripts\package-windows.bat

# 或通过 Maven profile
mvn -Pjpackage package
```

输出：`packaging/dist/windows/ClawDesktop-0.1.0.msi`

### Linux（.deb / .rpm）

```bash
# 前置条件：JDK 21+、dpkg-deb（UOS）或 rpmbuild（麒麟）、Maven

# 同时生成 deb + rpm
./scripts/package-linux.sh all

# 仅生成 deb（统信 UOS）
./scripts/package-linux.sh deb

# 仅生成 rpm（麒麟 OS）
./scripts/package-linux.sh rpm
```

输出：`packaging/dist/linux/`

### Linux 桌面集成

项目包含 `.desktop` 文件用于 Linux 桌面集成：
- `packaging/clawdesktop.desktop` — 应用启动入口
- `packaging/clawdesktop-autostart.desktop` — 开机自启入口

---

## 注意事项

### 1. JDK 版本要求

- **必须使用 JDK 21+**，项目大量使用 record、sealed interface、switch 模式匹配等 Java 21 特性
- jpackage 打包需要 JDK 21 自带的 jpackage 工具

### 2. JavaFX 依赖

JavaFX 21 通过 Maven 依赖引入，无需单独安装 JavaFX SDK。但如果使用模块化打包，需注意 JavaFX 模块路径。

### 3. SQLite 原生库

sqlite-jdbc 会自动按平台下载对应的原生库，无需手动配置。但在某些信创平台（如龙芯架构）可能需要手动指定原生库路径。

### 4. 配置文件热重载

`ConfigWatcher` 可监听配置文件变化并热重载，但当前 `ClawDesktopApp` 中注释掉了 `configWatcher.start()`，仅 `DesktopApplication` 通过设置界面手动触发重载。

### 5. 工具审批机制

`ApprovalManager` 支持两种策略：
- `CONFIRM_DANGEROUS`：仅危险操作（如删除文件、执行命令）需确认
- `CONFIRM_ALL`：所有工具调用均需确认

在 `DesktopApplication` 中通过 JavaFX 弹窗实现审批 UI。

### 6. 会话持久化

会话保存在 `~/.clawdesktop/sessions/` 下，超过 7 天的会话自动归档为 `.json.gz` 压缩文件。应用启动时自动恢复磁盘上的活跃会话。

### 7. MCP 传输协议

当前 MCP 客户端**仅支持 stdio 传输**（启动子进程），SSE 传输尚未实现。如需使用 MCP 工具，需配置 stdio 类型的 MCP 服务器。

### 8. 多入口类说明

项目有三个入口类，用途不同：
- **`DesktopApplication`**：完整桌面应用入口（JavaFX UI + 后端 + 桌面集成），**推荐使用**
- **`ClawDesktopApp`**：简化版后端入口（HTTP 服务 + 基础工具），无 UI，适合服务器部署
- **`ClawDesktop`**：核心后端组装类（含 Cron/Plugin/Skill/MCP 全子系统），无 main 方法，需被其他类调用

### 9. 信创适配

项目针对统信 UOS 和麒麟 OS 做了打包适配：
- Linux 打包脚本同时生成 `.deb`（UOS/Debian 系）和 `.rpm`（麒麟/CentOS 系）
- 默认尝试连接本地 Ollama，支持纯本地化部署（信创友好）
- 不依赖任何外部云服务即可运行（使用 Ollama 作为 LLM Provider 时）

### 10. 日志配置

日志级别：
- `com.openclaw`：DEBUG（开发时详细输出）
- `reactor.netty`：INFO
- `io.netty`：WARN
- 根级别：INFO

日志文件：`data/logs/claw.log`，10MB 滚动，保留 30 天，总上限 500MB。

### 11. 端口冲突

默认 HTTP 端口 7180、WebSocket 端口 7181。如端口被占用，可在配置文件中修改 `gateway.port` 和 `gateway.ws-port`。

---

## 开发信息

- **构建工具**：Maven
- **打包方式**：Maven Shade（fat jar）+ jpackage（原生安装包）
- **测试框架**：JUnit 5
- **CI 建议**：`mvn clean package`（编译 + 测试 + 打包）
- **代码风格**：Java record 优先，不可变数据结构，Reactor 响应式编程

---

*文档版本：2026-07-01 | 项目版本：0.1.0-SNAPSHOT*
