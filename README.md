# AI 文档问答 (RAG) - Java 全栈项目

本项目是一个基于 Java 和 Spring Boot 实现的 RAG (Retrieval-Augmented Generation) 系统，并搭配独立的前端应用。它提供用户管理、文档上传、智能问答和会话管理等功能，通过大语言模型 (LLM) 为用户提供文档内容的智能交互。

## 功能特性
- **用户认证与管理**: 支持用户注册、登录，采用 JWT 进行安全认证。
- **文档管理**:
    - **多格式支持**: 可上传 PDF, Word, TXT, Markdown, 代码文件等格式文档。
    - **智能处理**: 自动解析、切块、生成向量嵌入，并追踪处理状态。
    - **格式感知切分**: 基于策略模式，不同文档格式使用不同的切分策略。
    - **权限控制**: 支持文档所有者管理和公开/私有设置。
- **智能问答**:
    - **RAG 核心**: 结合 PostgreSQL (pgvector) 存储的文档向量和 LLM，提供基于文档内容的精准问答。
    - **来源引用**: 答案中会提供原文引用，便于用户核实。
- **会话与聊天**:
    - **会话历史**: 保存与 AI 的所有对话记录。
    - **实时互动**: 通过 WebSocket 实现实时聊天功能。

## 技术栈
- **后端**: Java 17, Spring Boot 3
- **AI 集成**: LangChain4J (支持 OpenAI, Kimi, Gemini 等)
- **数据库**: PostgreSQL + pgvector
- **认证**: JWT
- **实时通信**: WebSocket
- **文档解析**: Apache PDFBox, Apache POI
- **构建工具**: Maven
- **容器化**: Docker
- **前端**: React 19, TypeScript, Vite, Tailwind CSS, DaisyUI, Zustand

---

## 快速开始

### 1. 环境准备
- **Java 17+**: 确保已安装 JDK 17 或更高版本。
- **Maven**: 确保已安装 Maven。
- **Docker**: 确保已安装 Docker 和 Docker Compose。
- **Node.js**: 运行前端应用需要。

### 2. 启动数据库
项目使用 `docker-compose.yml` 来启动一个带有 `pgvector` 扩展的 PostgreSQL 数据库。

在项目根目录下运行：
```shell
docker-compose up -d
```
数据库将在 `localhost:5432` 上可用。

### 3. 配置 AI 服务
打开 `src/main/resources/application.properties` 文件，配置你的 AI 服务提供商。你需要填入你的 API Key，并根据提供商修改 Base URL。

**示例 (使用 Kimi)**:
```properties
langchain4j.open-ai.chat-model.base-url=https://api.moonshot.cn/v1
langchain4j.open-ai.chat-model.api-key=YOUR_KIMI_API_KEY # <-- 修改这里
langchain4j.open-ai.chat-model.model-name=moonshot-v1-8k

langchain4j.open-ai.embedding-model.base-url=https://api.moonshot.cn/v1
langchain4j.open-ai.embedding-model.api-key=YOUR_KIMI_API_KEY # <-- 修改这里
langchain4j.open-ai.embedding-model.model-name=moonshot-v1-embedding
```

### 4. 运行后端应用
使用 Maven 启动 Spring Boot 应用：
```shell
mvn spring-boot:run
```
应用启动后，Flyway 会自动执行数据库迁移脚本，确保数据库结构最新。服务将在 `http://localhost:8080` 上可用。

### 5. 运行前端应用
```shell
# 进入前端目录
cd frontend
# 安装依赖
npm install
# 启动开发服务器
npm run dev
```
应用通常将在 `http://localhost:5173` 上可用。

---

## API 使用示例 (后端接口)

### 用户认证
- `POST /api/register`: 用户注册
- `POST /api/login`: 用户登录

### 文档管理 (需认证)
- `POST /api/upload`: 上传文档
- `GET /api/documents`: 获取用户文档列表
- `DELETE /api/documents/{documentId}`: 删除文档

### 会话与问答 (需认证)
- `POST /api/conversations`: 创建新会话
- `GET /api/conversations`: 获取用户会话列表
- `POST /api/chat`: 进行会话聊天 (支持 REST 和 WebSocket)
- `POST /api/query`: 单次问答

---

## 项目结构
```
qarag/
├── src/main/java/com/example/qarag/
│   ├── api/             # API 控制器和 DTOs (用户、RAG、会话)
│   ├── config/          # 应用配置、JWT、Web/WebSocket 配置
│   ├── domain/          # 数据实体 (User, Document, Chunk, Conversation, ChatMessage)
│   ├── ingestion/       # 文档摄取、解析、切块和嵌入逻辑
│   │   └── chunker/     # 格式感知切分策略 (策略模式)
│   │       ├── DocumentChunker.java       # 切分策略接口
│   │       ├── DocumentChunkerFactory.java # 策略工厂
│   │       ├── RecursiveChunker.java      # 默认递归切分
│   │       ├── MarkdownChunker.java       # Markdown 标题切分
│   │       ├── PdfChunker.java            # PDF 元素感知切分
│   │       ├── CodeChunker.java           # 代码语法结构切分
│   │       └── pdf/                       # PDF 元素处理器 (嵌套策略)
│   │           ├── PdfElementProcessor.java    # 元素处理策略接口
│   │           ├── PdfElementProcessorFactory.java
│   │           ├── TextProcessor.java     # 普通文本 (PDFBox)
│   │           ├── TableProcessor.java    # 表格 (Tabula)
│   │           ├── ImageProcessor.java    # 图片 (视觉模型)
│   │           └── ScannedPageProcessor.java # 扫描件 (OCR)
│   ├── qa/              # 问答服务，RAG 核心逻辑
│   ├── repository/      # Spring Data JPA 数据仓库
│   ├── service/         # 用户、文档、会话的业务逻辑
│   └── vectorstore/     # 向量数据库交互接口
├── src/main/resources/
│   ├── db/migration/    # Flyway 数据库迁移脚本
│   ├── application.properties # Spring Boot 配置文件
├── frontend/            # 前端应用目录 (具体技术栈待定)
├── pom.xml              # Maven 项目配置
└── docker-compose.yml   # Docker Compose 数据库服务
```

## 文档切分架构

项目采用**双层策略模式**实现格式感知的文档切分：

### 第一层：文档格式策略
根据文件扩展名选择切分器：
- `MarkdownChunker` → `.md` 文件
- `PdfChunker` → `.pdf` 文件  
- `CodeChunker` → 代码文件
- `RecursiveChunker` → 默认 fallback

### 第二层：PDF 元素策略 (嵌套)
`PdfChunker` 内部根据页面内容选择处理器：

| 处理器 | 检测条件 | 处理技术 |
|--------|----------|----------|
| `ScannedPageProcessor` | 无文本+有图片 | 视觉模型 (OCR) ✅ |
| `TableProcessor` | 检测到表格 | Tabula → Markdown ✅ |
| `ImageProcessor` | 有文本+有图片 | 视觉模型 (图表理解) ✅ |
| `TextProcessor` | 始终可用 | PDFBox (fallback) ✅ |

### Vision 模型配置
```properties
app.rag.vision.enabled=true
app.rag.vision.base-url=https://api.siliconflow.cn/v1
app.rag.vision.api-key=<your-api-key>
app.rag.vision.model-name=deepseek-ai/DeepSeek-OCR
app.rag.vision.timeout-seconds=60
```


