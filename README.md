# J-RAG Enterprise (Spring Boot AI Scaffold)

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.31-blueviolet?style=for-the-badge)](https://github.com/langchain4j/langchain4j)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker)](https://www.docker.com/)

[English](README.md) | [简体中文](#简体中文)

</div>

**J-RAG Enterprise** is a production-ready RAG (Retrieval-Augmented Generation) scaffold tailored for Java teams. Unlike Python-based solutions, it leverages the robust **Spring Boot** ecosystem and **PostgreSQL** (pgvector), offering enterprise-grade features like RBAC, hybrid search, and full observability out of the box.

**J-RAG Enterprise** 是专为 Java 团队打造的生产级 RAG（检索增强生成）脚手架。与 Python 生态方案不同，它基于稳健的 **Spring Boot** 生态和 **PostgreSQL** (pgvector) 构建，开箱即用，提供RBAC权限管理、混合检索和全链路可观测性等企业级特性。

---

## ✨ Features (核心特性)

| Feature | Description |
|---------|-------------|
| **☕ Java Native** | Pure Java/Spring Boot stack. No Python dependency hell. Easy to integrate into existing enterprise systems. <br> **纯 Java 技术栈**，无 Python 依赖，易于集成到现有企业系统。 |
| **🔍 Hybrid Search** | Combines **Vector Search** (Semantic) + **Keyword Search** (BM25) + **Reranking** (Jina/BGE) for maximum accuracy. <br> **混合检索**：结合向量检索（语义）+ 关键词检索（精确匹配）+ 重排序，最大化准确率。 |
| **🛡️ Enterprise RBAC** | Built-in **Role-Based Access Control**. Supports multi-tenant data isolation and user groups. <br> **企业级权限控制**：内置基于角色的访问控制，支持多租户数据隔离和用户组管理。 |
| **🧠 Agentic RAG** | Includes a **Deep Thinking Agent** capable of query decomposition and multi-step reasoning. <br> **Agentic RAG**：内置“深度思考”Agent，支持查询分解和多步推理。 |
| **📊 Observability** | Integrated with **LangFuse** for full-link tracing (latency, token usage, cost). <br> **可观测性**：集成 LangFuse，实现全链路追踪（延迟、Token 消耗、成本）。 |
| **⚡ Zero Config** | Docker-based deployment. Database schema and vector extensions are initialized automatically. <br> **零配置部署**：基于 Docker，数据库 Schema 和向量扩展自动初始化。 |
| **🔌 Model Agnostic** | Switch between **OpenAI**, **DeepSeek**, **Ollama** (Local), or **Aliyun** with a single config change. <br> **模型中立**：只需修改一行配置，即可在 OpenAI、DeepSeek、Ollama（本地）或阿里云之间切换。 |

---

## 🚀 Quick Start (快速开始)

### Prerequisites (前置要求)
*   Docker & Docker Compose

### 1. Clone & Configure (克隆与配置)

```bash
git clone https://github.com/your-username/jrag-enterprise.git
cd jrag-enterprise

# Copy the environment file
cp .env.example .env
```

Edit `.env` to set your API Keys (or use the defaults for testing):
编辑 `.env` 设置你的 API Key（或使用默认值进行测试）：

```properties
# .env
CHAT_API_KEY=sk-xxxx
EMBEDDING_API_KEY=sk-xxxx
```

### 2. Start with Docker (一键启动)

```bash
docker-compose up -d
```

Access the application (访问应用):
*   **Web UI**: [http://localhost:5173](http://localhost:5173) (or `http://localhost` in production / 生产模式下为 `http://localhost`)
*   **API Docs (Swagger)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 🏗️ Architecture (架构)

```mermaid
graph TD
    User[User / Web UI] -->|HTTP/SSE| API_Gateway[Spring Boot Backend]
    
    subgraph "RAG Engine"
        API_Gateway -->|1. Rewrite| LLM_Rewrite[LLM (Query Rewrite)]
        API_Gateway -->|2. Search| Hybrid_Search{Hybrid Search}
        Hybrid_Search -->|Vector| PGVector[(PostgreSQL pgvector)]
        Hybrid_Search -->|Keyword| PG_FullText[(PostgreSQL FullText)]
        Hybrid_Search -->|3. Rerank| Rerank_Model[Rerank Model]
        Rerank_Model -->|4. Generate| LLM_Chat[LLM (Chat)]
    end
    
    subgraph "Observability"
        API_Gateway -.->|Async Trace| LangFuse[LangFuse Server]
    end
```

---

## 💡 Technical Highlights (技术亮点)

### 1. Advanced Hybrid Search (高级混合检索)
Pure vector search often suffers from "semantic drift" and fails on exact keyword matches (e.g., specific product IDs). J-RAG solves this by:
纯向量检索常面临“语义漂移”问题，且在精确匹配（如特定产品ID）上表现不佳。J-RAG 通过以下方式解决：

*   **Dual-Path Retrieval**: Parallel execution of **Semantic Search** (pgvector) and **Keyword Search** (PostgreSQL tsvector/BM25). <br> **双路检索**：并行执行向量检索（语义）和关键词检索（基于 PostgreSQL tsvector）。
*   **Reranking Strategy**: A coarse-to-fine approach. We retrieve Top-50 candidates first, then use a high-precision Cross-Encoder model (Reranker) to re-score them, ensuring the Final Top-5 are contextually accurate. <br> **重排序策略**：采用从粗到精的方案。首先召回 Top-50 候选片段，随后使用高精度交叉编码器模型（Reranker）进行二次打分，确保最终的 Top-5 片段语义高度相关。

### 2. Deep Thinking Agent (深度思考 Agent)
For complex queries like "Compare A and B", simple retrieval often misses half the context.
对于“对比 A 和 B”等复杂问题，简单检索往往会丢失一半上下文。

*   **Query Decomposition**: The system breaks down complex queries into independent sub-queries (e.g., "Features of A", "Features of B"). <br> **查询分解**：系统将复杂问题拆解为独立的子查询（如“A的特征”、“B的特征”）。
*   **ReAct Paradigm**: Implements a "Reasoning + Acting" loop, allowing the LLM to autonomously decide when to search, read, or conclude. <br> **ReAct 范式**：实现“推理+行动”循环，允许 LLM 自主决定何时搜索、阅读或给出结论。


---

## ⚙️ Configuration (配置指南)

Modify `src/main/resources/application.properties` to customize your AI provider.
修改 `src/main/resources/application.properties` 以自定义 AI 供应商。

### Switch to DeepSeek (切换到 DeepSeek)
```properties
app.model.chat.base-url=https://api.deepseek.com
app.model.chat.model-name=deepseek-chat
app.model.chat.api-key=${CHAT_API_KEY}
```

### Switch to Local Ollama (切换到本地 Ollama)
```properties
app.model.chat.base-url=http://localhost:11434/v1
app.model.chat.model-name=llama3
app.model.chat.api-key=ollama # Any string works
```

---

## 📚 Documentation (文档)

*   [API Documentation (Swagger) / 接口文档](http://localhost:8080/swagger-ui.html)
*   [Deployment Guide / 部署指南](docs/deployment.md) (Coming Soon / 敬请期待)
*   [Developer Guide / 开发指南](docs/developer.md) (Coming Soon / 敬请期待)

## 📄 License (许可证)

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details.
本项目采用 Apache 2.0 许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。

