# 项目摘要：AI 文档问答 (RAG) - Java 全栈项目

本文档总结了 `qarag` 项目，这是一个使用 Java 和 Spring Boot 实现的 AI 文档问答（检索增强生成 - RAG）系统。它具备高级文档处理能力、感知格式的分块策略，并集成了大语言模型（LLM）以实现智能问答。

## 核心功能
- **用户管理与认证**：安全的用户注册、登录以及基于 JWT 的会话管理。
- **高级文档管理**：
    - **上传**：支持 PDF、Word (.docx/.doc)、Excel (.xlsx/.xls)、Markdown 和普通文本文件。
    - **深度结构分析**：
        - **PDF**：逐页处理，支持元素检测（文本、表格、图片）。
        - **Word (.docx)**：解析标题、段落和表格的结构；将表格转换为 Markdown 格式。
        - **Excel**：逐个工作表（Sheet）处理；将电子表格转换为 Markdown 表格；处理图片/图表。
        - **Markdown**：基于标题层级（#, ##, ...）进行语义拆分。
    - **视觉集成**：使用视觉语言模型（VLM）分析并描述 PDF、Word 和 Excel 文档中的图片、图表和扫描页。
    - **元数据丰富**：为分块（Chunk）添加上下文元数据（例如页码、工作表名称、标题路径）。
- **向量存储**：利用 PostgreSQL 和 `pgvector` 扩展存储文本嵌入（Embeddings）。
- **智能问答**：使用 RAG 生成带有来源引用的答案。
- **对话**：通过 WebSocket 进行实时聊天，并支持历史记录管理。

## 技术栈
- **后端**：Java 17, Spring Boot 3
- **AI 集成**：LangChain4J (支持 OpenAI, Kimi, Gemini 等)
- **数据库**：PostgreSQL + pgvector
- **认证**：JWT
- **文档解析**：
    - Apache PDFBox (PDF)
    - Apache POI (Word, Excel)
    - Tabula (PDF 表格)
- **视觉**：自定义视觉服务 (OpenAI 兼容 API)

## 高级分块架构

本项目采用了复杂的**策略模式**进行文档入库，针对每种文件类型优化了处理流程。

### 1. PDF 处理 (`PdfChunker`)
- **策略**：逐页元素检测与处理。
- **文本**：精确提取，排除页眉/页脚（通过 `TextProcessor`）。
- **表格**：检测并转换为 Markdown 格式（通过 `TableProcessor` / Tabula）。
- **图片/图表**：通过视觉模型提取并生成描述（通过 `ImageProcessor`）。
- **OCR**：针对扫描页面的自动回退机制（通过 `ScannedPageProcessor`）。
- **安全**：检测并在无提取权限时拒绝加密文件。
- **优化**：内存高效加载（`MemoryUsageSetting.setupTempFileOnly`）。

### 2. Excel 处理 (`ExcelChunker`)
- **策略**：逐个工作表独立处理。
- **表格处理**：将电子表格数据转换为 **Markdown 表格**，以便 LLM 理解行列结构。
- **分块逻辑**：自定义“表头感知”分块，确保长表格被切分到多个块时，**表头会在每个块中重复**，以保持上下文完整。
- **视觉**：提取嵌入的图片/图表，并将其描述插入到相应的行上下文中。
- **元数据**：为每个块添加 `sheet_name`（工作表名）和 `source`（来源文件）。

### 3. Word 处理 (`WordChunker`)
- **.docx (高级)**：
    - **结构**：解析 Heading 1-6 层级以构建语义层级结构。
    - **元数据**：为分段附加 **标题路径**（例如 "用户指南 > 安装 > Windows"）。
    - **表格**：将嵌入的表格转换为 Markdown。
    - **图片**：使用视觉模型提取并描述内联图片。
- **.doc (传统)**：回退到使用 Apache POI 进行纯文本提取。

### 4. Markdown 处理 (`MarkdownChunker`)
- **策略**：基于标题层级的语义拆分。
- **上下文**：维护当前标题栈，为每个块生成 **标题路径** 元数据，确保独立的片段保留文档上下文。

### 5. 视觉服务 (`ingestion.vision`)
一个集中的服务接口，对接兼容 OpenAI 的视觉 API（如 GPT-4o, Gemini Pro Vision, DeepSeek-VL），为文档中发现的视觉内容生成文本描述。

## 数据入库与向量化流程

系统的核心是将非结构化文档转化为可检索的向量数据，具体流程如下：

1.  **智能分块 (Intelligent Chunking)**
    系统摒弃了通用的“按字符长度切分”，而是针对不同格式采用定制化策略，确保语义完整性：
    - **PDF (`PdfChunker`)**: 
        - **逐页处理**: 遍历文档每一页，独立提取内容。
        - **多模态解析**: 同时运行文本提取器、表格识别器（转为 Markdown）和视觉模型（描述图片/图表）。
        - **动态切分**: 如果单页内容超过 Chunk Size，使用递归切分器进行二次切分，并附带页码元数据。
    - **Excel (`ExcelChunker`)**: 
        - **结构化转换**: 将二维表格转化为 Markdown 格式文本，保持行列关系。
        - **表头粘滞**: 实施“表头感知”切分，当长表格被截断时，强制在每个新块的开头重复表头，确保 LLM 知道每一列的含义。
        - **图表描述**: 调用视觉模型将嵌入的统计图表转化为文字摘要插入上下文。
    - **Word (`WordChunker`)**: 
        - **层级感知**: 解析 Heading 1-6 样式，构建文档树。
        - **路径追踪**: 生成 "User Guide > Installation" 形式的 `header_path` 元数据，让片段即使脱离文档也知道自己属于哪个章节。
    - **Markdown (`MarkdownChunker`)**: 
        - **语义边界**: 严格按照标题层级进行切分，避免在段落或列表项中间截断。

2.  **内容清洗 (Content Cleaning)**
    - 在向量化之前，系统会自动过滤低价值噪音，例如：
        - 页码标识（如 "Page 1 of 10"）
        - 常见的机密/内部声明（如 "Confidential", "Internal Use Only"）
    - 过滤掉清洗后内容为空的片段，保证索引质量。

3.  **向量生成 (Vector Embedding)**
    - 清洗后的文本被送入 Embedding Model（配置为 `BAAI/bge-m3`）。
    - 模型将文本转换为高维浮点向量（Vector），捕捉文本的语义特征。

4.  **混合存储 (Hybrid Storage)**
    - **向量数据**：存入 PostgreSQL 的 `chunks` 表中 `vector` 列（使用 `pgvector` 类型），用于语义相似度检索。
    - **文本数据**：原始文本存入 `content` 列，用于构建 LLM 的上下文 (Context)。
    - **元数据**：存储 `document_id`, `chunk_index` 以及来源信息，支持精确溯源。

## 高级检索 (Advanced Retrieval)

为了解决单一向量检索在精确匹配和特定标识符搜索上的不足，本项目实现了 **混合检索 (Hybrid Search)** 策略。

### 1. 混合检索策略
结合了两种互补的检索技术：
- **向量检索 (Vector Search)**：利用 `pgvector` 进行语义相似度匹配，擅长理解查询意图和模糊匹配。
- **关键词检索 (Keyword Search)**：利用 PostgreSQL 的全文检索功能 (`tsvector` + `GIN` 索引)，擅长精确匹配专有名词、型号和数字。

### 2. 结果融合 (RRF)
检索结果通过 **倒数排名融合 (Reciprocal Rank Fusion, RRF)** 算法进行合并与重排序：
- 分别执行向量搜索和关键词搜索，各获取 Top-K 结果。
- 计算每个文档在两个列表中的排名倒数之和作为最终得分：`score = 1/(k + rank_vector) + 1/(k + rank_keyword)`。
- 这种方法无需归一化分数，能够鲁棒地平衡两路召回结果。

### 3. 中文分词优化
针对中文语境，集成了 **Jieba 分词**：
- **写入时**：在数据入库阶段，使用 Jieba 对 Chunk 内容进行分词，生成关键词列表存入 `content_keywords` 字段，并自动同步到 `content_search` (tsvector) 索引。
- **查询时**：使用 Jieba 对用户 Query 进行分词，并结合 **停用词表 (Stop Words)** 过滤（如去除“分析”、“一下”等噪音词），构建精准的布尔查询。

## 配置

### 数据库
使用 Docker Compose 管理 PostgreSQL + `pgvector`。
```shell
docker-compose up -d
```

### AI & 视觉配置
在 `src/main/resources/application.properties` 中配置：

```properties
# LLM & Embedding (大模型与嵌入)
langchain4j.open-ai.chat-model.base-url=https://api.example.com/v1
langchain4j.open-ai.chat-model.api-key=YOUR_API_KEY
langchain4j.open-ai.embedding-model.base-url=https://api.example.com/v1

# Vision Service (用于图片/OCR分析)
app.rag.vision.enabled=true
app.rag.vision.base-url=https://api.example.com/v1
app.rag.vision.api-key=YOUR_VISION_API_KEY
app.rag.vision.model-name=gpt-4o
```

## 项目结构亮点
- `ingestion/chunker/`:
    - `DocumentChunker.java`: 统一接口（自解析）。
    - `PdfChunker.java`, `ExcelChunker.java`, `WordChunker.java`, `MarkdownChunker.java`: 专用实现。
- `ingestion/vision/`:
    - `VisionService.java`: 集中式图像分析服务。
- `ingestion/chunker/pdf/`:
    - PDF 元素处理器 (`TextProcessor`, `TableProcessor` 等)。

## API 使用
- **上传**: `POST /api/upload` (支持 PDF, DOCX, XLSX, MD, TXT)
- **对话**: `POST /api/chat`
- **查询**: `POST /api/query`
