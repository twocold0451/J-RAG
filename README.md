# J-RAG: Enterprise-grade RAG Engine in Java
# J-RAG: Java 企业级检索增强生成引擎

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![LangChain4j](https://img.shields.io/badge/LangChain4j-Integration-blue?style=for-the-badge)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)

**J-RAG** is a robust, full-stack Retrieval-Augmented Generation (RAG) system built with **Spring Boot** and **LangChain4j**. It bridges your private data with Large Language Models (LLMs) to provide accurate, context-aware answers.

**J-RAG** 是一个基于 **Spring Boot** 和 **LangChain4j** 构建的稳健全栈 RAG 系统。它将您的私有数据与大语言模型 (LLM) 连接起来，提供精准且具备上下文感知能力的智能问答。

---

## 🌟 Key Features / 核心特性

- **🔐 Secure Auth**: User registration & login with JWT protection. (用户认证与 JWT 安全保护)
- **📄 Smart Ingestion**:
  - Supports **PDF, Word, Markdown, TXT**. (支持多种格式)
  - **Format-Aware Chunking**: Specialized strategies for different file types (e.g., Markdown headers, PDF elements). (格式感知切分策略)
  - **Vision Capable**: OCR and image analysis for scanned PDFs and charts. (视觉模型支持 OCR 和图表分析)
- **🧠 Advanced RAG**:
  - **Vector Search**: Powered by PostgreSQL + `pgvector`. (基于 pgvector 的向量检索)
  - **Source Citations**: Answers include references to original document segments. (答案包含原文引用)
- **💬 Interactive Chat**:
  - Real-time chat via **WebSocket**. (WebSocket 实时聊天)
  - Conversation history management. (会话历史管理)

---

## 🛠️ Tech Stack / 技术栈

- **Backend**: Java 21, Spring Boot 3
- **AI Integration**: LangChain4j (OpenAI API Compatible)
- **Database**: PostgreSQL + pgvector extension
- **Security**: Spring Security + JWT
- **Document Processing**: Apache PDFBox, Apache POI
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS (in `frontend/` directory)
- **Containerization**: Docker & Docker Compose

---

## 🚀 Quick Start / 快速开始

### 1. Prerequisites / 环境准备
- **Java 21+**
- **Docker & Docker Compose**
- **Maven**
- **Node.js** (for frontend)

### 2. Start Database / 启动数据库
Use Docker Compose to start PostgreSQL with the `pgvector` extension.
使用 Docker Compose 启动带有 `pgvector` 扩展的 PostgreSQL。

```bash
docker-compose up -d
```

### 3. Configuration / 配置
Configure your LLM provider in `src/main/resources/application.properties` or via environment variables (Recommended).
在配置文件中设置 LLM 提供商，推荐使用环境变量。

#### Core Configuration (核心配置)
| Property | Env Variable | Description |
|----------|--------------|-------------|
| `langchain4j.open-ai.chat-model.api-key` | `CHAT_MODEL_API_KEY` | **Required**. Your LLM API Key. |
| `langchain4j.open-ai.chat-model.base-url` | `CHAT_MODEL_BASE_URL` | Base URL (e.g., OpenAI, DeepSeek, AliYun). |
| `langchain4j.open-ai.embedding-model.api-key` | `EMBEDDING_MODEL_API_KEY` | **Required**. Embedding Model Key. |
| `jwt.secret` | `JWT_SECRET` | **Required**. Secret for token generation. |
| `app.rag.vision.api-key` | `VISION_API_KEY` | Optional. For OCR/Image processing. |

**Example `application.properties`:**
```properties
# Chat Model (e.g., OpenAI, DeepSeek, Qwen)
langchain4j.open-ai.chat-model.base-url=${CHAT_MODEL_BASE_URL:https://api.openai.com/v1}
langchain4j.open-ai.chat-model.api-key=${CHAT_MODEL_API_KEY:demo}
langchain4j.open-ai.chat-model.model-name=gpt-4o

# Embedding Model
langchain4j.open-ai.embedding-model.base-url=${EMBEDDING_MODEL_BASE_URL:https://api.openai.com/v1}
langchain4j.open-ai.embedding-model.api-key=${EMBEDDING_MODEL_API_KEY:demo}
langchain4j.open-ai.embedding-model.model-name=text-embedding-3-small
```

### 4. Run Backend / 运行后端
```bash
mvn spring-boot:run
```
Server will start at `http://localhost:8080`. Flyway will handle database migrations automatically.
服务将在 8080 端口启动，Flyway 会自动处理数据库迁移。

### 5. Run Frontend / 运行前端
```bash
cd frontend
npm install
npm run dev
```
Frontend will be available at `http://localhost:5173`.
前端将在 5173 端口启动。

---

## 🏗️ Architecture / 架构设计

### Document Chunking Strategy (文档切分策略)
J-RAG uses a **Dual-Layer Strategy Pattern** for high-quality ingestion:
J-RAG 采用**双层策略模式**来实现高质量的文档摄取：

1.  **Level 1: File Type Strategy (文件类型策略)**
    - `MarkdownChunker`: Splits by headers (#, ##).
    - `PdfChunker`: Complex PDF processing.
    - `RecursiveChunker`: Fallback for generic text.

2.  **Level 2: PDF Element Strategy (PDF 元素策略)**
    - Inside `PdfChunker`, content is analyzed to select the best processor:
    - `ScannedPageProcessor`: Uses Vision LLM for OCR.
    - `TableProcessor`: Extracts tables to Markdown format.
    - `ImageProcessor`: Analyzes charts/diagrams using Vision LLM.
    - `TextProcessor`: Standard text extraction.

### Folder Structure (目录结构)
```
src/main/java/com/example/qarag/
├── api/             # REST Controllers & DTOs
├── config/          # App, Security, & WebSocket Config
├── domain/          # Entities (User, Document, Chunk)
├── ingestion/       # Document Parsing & Chunking Logic
│   ├── chunker/     # Strategy Implementations (Markdown, PDF, etc.)
│   └── vision/      # Vision LLM Service
├── qa/              # RAG Logic (Retrieval + Generation)
├── repository/      # Spring Data JPA Repositories
└── service/         # Business Logic Layer
```

---

## 📄 License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE.md) file for details.
本项目采用 Apache License 2.0 许可证。

---

_Built with ❤️ by [TwoCold](https://github.com/twocold0451)_