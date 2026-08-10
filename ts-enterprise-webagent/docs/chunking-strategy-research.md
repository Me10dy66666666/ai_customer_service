# RAG 系统文档分块策略技术调研

> 撰写日期：2026-08-10
> 项目背景：本项目（ai_customer_service）当前在 Python 侧采用 LangChain RecursiveCharacterTextSplitter 作为分块策略，向量存储使用 ChromaDB PersistentClient，嵌入模型为 `qwen3.7-text-embedding`（DashScope API）。`ts-enterprise-webagent` 子项目正在将 Python 版迁移为 TypeScript 原生实现。本报告对该策略进行系统评估，并调研所有可替代及增强方案。

---

## 目录

- [1. 主流分块策略对比](#1-主流分块策略对比)
  - [1.1 固定大小分块](#11-固定大小分块-fixed-size-chunking)
  - [1.2 递归分块](#12-递归分块-recursive-chunking)
  - [1.3 语义分块](#13-语义分块-semantic-chunking)
  - [1.4 文档结构分块](#14-文档结构分块-structure-aware-chunking)
  - [1.5 Agentic Chunking](#15-agentic-chunking)
  - [1.6 策略对比总览](#16-策略对比总览)
- [2. 图增强分块策略](#2-图增强分块策略)
  - [2.1 问题定义](#21-问题定义)
  - [2.2 KG-enhanced RAG](#22-kg-enhanced-rag)
  - [2.3 GraphRAG (Microsoft)](#23-graphrag-microsoft)
  - [2.4 LightRAG](#24-lightrag)
  - [2.5 HippoRAG](#25-hipporag)
  - [2.6 父文档检索](#26-父文档检索-parent-document-retriever)
  - [2.7 多跳检索](#27-多跳检索-multi-hop-retrieval)
  - [2.8 图增强方案总览](#28-图增强方案总览)
- [3. 最终推荐方案](#3-最终推荐方案)

---

## 1. 主流分块策略对比

### 1.1 固定大小分块 (Fixed-size Chunking)

**原理**：按固定字符数或 token 数将文本等距切分，不考虑语义边界。

**优点**：实现极简，行为可预测，零延迟。  
**缺点**：语义被随机切断，对中文不友好（无空格分隔），overlap 是唯一边界优化。  
**本项目适配度**：低。客服知识库以中文 FAQ、政策文档为主，固定切分会产生大量"半句"chunk。

**参考来源**：[1] LangChain CharacterTextSplitter - https://python.langchain.com/docs/modules/data_connection/document_transformers/character_text_splitter

---

### 1.2 递归分块 (Recursive Chunking)

**原理**：按优先级从高到低尝试多个分隔符进行递归切分：段落 → 换行 → 句号 → 叹号 → 问号 → 分号 → 逗号 → 字符兜底。

**优点**：语义完整性较好，零模型调用延迟，LangChain/LlamaIndex 内置，生产最广泛。  
**缺点**：分隔符优先级固定（无法适应 Markdown 结构），`overlap=0` 导致跨 chunk 上下文丢失。  
**本项目适配度**：中高。**当前方案，应作为基线保留，在此之上叠加增强策略。**

**参考来源**：[2] LangChain RecursiveCharacterTextSplitter - https://python.langchain.com/docs/modules/data_connection/document_transformers/recursive_text_splitter

---

### 1.3 语义分块 (Semantic Chunking)

**原理**：对文本逐句计算 embedding，比较相邻句子的余弦相似度。相似度显著下降时视为语义断点。

**优点**：语义边界精确，自适应不同文档风格，减少语义碎片化。  
**缺点**：计算成本高（每句一次 embedding API 调用，100 句 = 100 次），DashScope API 按 token 计费会大幅增加成本。对短文档（<20句）效果弱。  
**本项目适配度**：中等。**可作为长文档（>5000 字）的补充策略，不宜作为主策略。**

**参考来源**：[3] LangChain SemanticChunker - https://python.langchain.com/docs/modules/data_connection/document_transformers/semantic-chunker ；[4] LlamaIndex SemanticSplitterNodeParser

---

### 1.4 文档结构分块 (Structure-aware Chunking)

**原理**：根据 Markdown 标题层级（`#`/`##`/`###`）、代码块、表格进行切分，标题层级自动注入 chunk metadata。

**优点**：结构完整性最优，FAQ 场景极佳（每个 QA 对天然映射为独立 chunk），可精确 metadata 过滤检索。  
**缺点**：依赖文档格式（需要 Markdown 规范性），大表格/长代码块需二次缩小。  
**本项目适配度**：高。**优先引入。知识库文档天然适合结构化分块。**

**参考来源**：[5] LangChain MarkdownHeaderTextSplitter ；[6] LlamaIndex MarkdownNodeParser

---

### 1.5 Agentic Chunking

**原理**：LLM 自主判断切分点并生成 chunk 描述和语义标签。

**优点**：分块质量最高，能理解深层语义和长距离依赖。  
**缺点**：成本极高（GPT-4 级别，1000 篇文档数百美元），延迟极高，生态不成熟。  
**本项目适配度**：极低。客服知识更新频率和成本不容忍。

**参考来源**：[7] Greg Kamradt "5 Levels of Text Splitting"

---

### 1.6 策略对比总览

| 维度 | Fixed-size | Recursive | Semantic | Structure-aware | Agentic |
|---|---|---|---|---|---|
| **实现复杂度** | 极低 | 低 | 中高 | 中 | 高 |
| **语义保持度** | 差 | 中 | 高 | 高（结构化文档） | 最高 |
| **检索精度** | 低 | 中 | 高 | 高 | 最高 |
| **计算成本** | 零 | 零 | 高 | 低 | 极高 |
| **中文适配** | 差 | 中 | 好 | 好 | 好 |
| **本项目推荐度** | 备用 | **当前保留** | 补充（长文档） | **优先引入** | 不推荐 |

---

## 2. 图增强分块策略

### 2.1 问题定义

传统 RAG 存在两个核心缺陷：

**问题1 - 切块后上下文丢失**：文档连续的语义流被切散，用户问题可能命中某个孤立 chunk 导致回答不完整。

**问题2 - 跨文档知识散落**：同一类知识分散在多个文档中（如退款政策在文档A、会员权益在文档B、FAQ在文档C），传统向量检索只能独立召回，缺少逻辑联系。

---

### 2.2 KG-enhanced RAG

**原理**：在 RAG 管线上叠加知识图谱。入库时从 chunk 抽取实体和关系构建 KG，检索时通过图遍历（1-2跳）召回关联邻居 chunk。  
**优点**：显式建模知识关联，上下文扩充可控。  
**缺点**：构建成本高（需 NER/LLM 抽取 + 图数据库如 Neo4j），ChromaDB 不内置图能力。  
**本项目适配度**：中等。**中期演进方向。**

---

### 2.3 GraphRAG (Microsoft)

**原理**：LLM 抽取实体关系 → Leiden 社区检测 → 生成社区摘要 → Local/Global 双模式检索。  
**优点**：端到端自动化，社区检测发现隐式主题。  
**缺点**：极高索引成本（每 1000 token 需 610 token LLM 提取），语料规模要求大（>10000字），自带向量索引不与 ChromaDB 兼容。  
**本项目适配度**：低中。**语料规模偏小，成本过高，远期关注。**

**参考来源**：[10] Microsoft GraphRAG 论文 - https://arxiv.org/abs/2404.16130 ；[12] https://github.com/microsoft/graphrag

---

### 2.4 LightRAG

**原理**：轻量化图索引，不显式构建实体节点，每个 chunk 同时作为文本单元和图节点，关系通过共享关键词/实体权重隐式表达。仅需轻量关键词抽取，无 LLM 调用。  
**优点**：成本大幅降低，轻量（内存存储，无需图数据库），数百篇文档即可生效。  
**缺点**：语义深度有限（关键词匹配），对中文分词质量依赖。  
**本项目适配度**：中高。**图增强入门方案，契合轻量预算约束。**

**参考来源**：[13] LightRAG - https://arxiv.org/abs/2410.05779 ；[14] https://github.com/HKUDS/LightRAG

---

### 2.5 HippoRAG

**原理**：受海马体记忆索引理论启发，LLM 抽取 OpenIE 三元组 → PPR 图谱 → 检索时 PPR 传导召回。  
**优点**：生物启发的记忆模型，Multi-hop QA 基准优异。  
**缺点**：构建成本高（LLM OpenIE 抽取），目前仅 Python 无 TS 版，生态极早。  
**本项目适配度**：低。**学术关注方向，当前不推荐。**

**参考来源**：[15] HippoRAG - https://arxiv.org/abs/2405.14831

---

### 2.6 父文档检索 (Parent Document Retriever)

**原理**：双向索引。入库时将文档切分为父文档（大块 2000 token）和子文档（小块 500 token）。检索时用小块的向量匹配找精确 chunk，再通过映射返回对应的完整父文档。  
**优点**：实现简单（LangChain 内置 ~50 行），零额外模型成本，与 ChromaDB 天然兼容，直接解决问题1（上下文丢失）。  
**缺点**：不解决跨文档知识散落（仅恢复同一文档内的上下文）。  
**本项目适配度**：极高。**强烈推荐立即引入，作为分块策略的核心增强。**

**参考来源**：[17] LangChain ParentDocumentRetriever - https://python.langchain.com/docs/modules/data_connection/retrievers/parent_document_retriever

---

### 2.7 多跳检索 (Multi-hop Retrieval)

**原理**：LLM 将复杂问题拆解为子问题链 → 逐跳检索 → 合并结果生成回答。  
**优点**：解决跨文档知识散落，无需额外索引，可选方案多。  
**缺点**：延迟成倍增加，分解质量依赖 LLM，Token 消耗增加。  
**本项目适配度**：中等。**应作为 Agent 路径中复杂问题的降级检索方式，非常规默认。**

**参考来源**：[18] LangChain MultiQueryRetriever ；[19] LlamaIndex SubQuestionQueryEngine

---

### 2.8 图增强方案总览

| 维度 | KG-RAG | GraphRAG | LightRAG | HippoRAG | PDR | Multi-hop |
|---|---|---|---|---|---|---|
| **解决问题1（上下文丢失）** | 部分 | 部分 | 部分 | 部分 | **完全** | 间接 |
| **解决问题2（跨文档散落）** | **优秀** | **优秀** | 中等 | **优秀** | 不解决 | **优秀** |
| **实现复杂度** | 高 | 极高 | 中低 | 高 | **极低** | 中 |
| **额外模型成本** | 中 | **极高** | 零 | 高 | **零** | 中 |
| **ChromaDB 兼容** | 需图DB | 自带 | **兼容** | 需PPR引擎 | **天然** | **天然** |
| **本项目推荐度** | 中期方向 | 远期关注 | **入门可选** | 不推荐 | **立即引入** | Agent 路径 |

---

## 3. 最终推荐方案

### 3.1 分层渐进策略

| 层级 | 策略 | 优先级 | ChromaDB 兼容 |
|---|---|---|---|
| 主分块 | Recursive Chunking（保留并优化参数） | P0 | ✅ |
| 增强（必选） | **父文档检索 (PDR)** | **P0** | ✅ |
| 增强（推荐） | Structure-aware Chunking | P1 | ✅ |
| 增强（可选） | Semantic Chunking（仅长文档） | P2 | ✅ |
| 图增强（入门） | LightRAG 关键词图 | P2 | ✅ |
| 图增强（进阶） | KG-enhanced RAG + Neo4j | P3 | 需图DB |
| 检索增强 | Multi-hop Retrieval（Agent 路径） | P2 | ✅ |

### 3.2 分块参数建议

```python
from langchain_text_splitters import RecursiveCharacterTextSplitter

text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=800,          # 从 1000 降为 800，更适配中文语义密度
    chunk_overlap=150,       # 从 0 提升为 150（约 18%），恢复跨 chunk 上下文
    separators=[
        "\n\n",              # 段落
        "\n",                # 换行
        "。",                # 中文句号
        "！",                # 感叹号
        "？",                # 问号
        "；",                # 分号（新增）
        "，",                # 逗号（新增，次级兜底）
        " ",                 # 空格（终极兜底）
    ],
    length_function=len,
    is_separator_regex=False,
)
```

父文档检索参数：
- 子文档 `chunk_size=400`（小块更精确）
- 父文档 `chunk_size=2000`（完整语义上下文）
- `search_kwargs={"k": 5}`（检索 5 个子文档，去重父文档 2-3 个）

### 3.3 推荐的 Embedding 模型

| 模型 | 维度 | 最大输入 | 中文性能 | 成本 | 推荐度 |
|---|---|---|---|---|---|
| `qwen3.7-text-embedding`（当前） | 1024 | 8192 token | 优异 | DashScope API | 短期保留 |
| `BAAI/bge-m3` | 1024 | 8192 token | C-MTEB Top3 | 本地免费 | **最推荐**（中期） |
| `BAAI/bge-large-zh-v1.5` | 1024 | 512 token | 中文 SOTA | 本地免费 | 备选 |

### 3.4 各阶段预期精度提升

| 阶段 | 累积提升（vs 基线） | 主要场景 |
|---|---|---|
| 基线（Recursive, overlap=0） | - | - |
| P1: PDR + 参数优化 | **+15-25%** | 多条件、上下文依赖问题 |
| P2: 结构感知 | **+5-10%** | 按分类/标题过滤查询 |
| P3: LightRAG | **+10-15%** | 跨文档综合问题 |
| P4: KG-RAG | **+10-20%** | 实体间推理、复杂查询 |

---

## 参考文献

- [1] https://python.langchain.com/docs/modules/data_connection/document_transformers/character_text_splitter
- [2] https://python.langchain.com/docs/modules/data_connection/document_transformers/recursive_text_splitter
- [3] https://python.langchain.com/docs/modules/data_connection/document_transformers/semantic-chunker
- [5] https://python.langchain.com/docs/modules/data_connection/document_transformers/markdown_header_metadata
- [10] https://arxiv.org/abs/2404.16130
- [12] https://github.com/microsoft/graphrag
- [13] https://arxiv.org/abs/2410.05779
- [14] https://github.com/HKUDS/LightRAG
- [15] https://arxiv.org/abs/2405.14831
- [17] https://python.langchain.com/docs/modules/data_connection/retrievers/parent_document_retriever
- [20] https://huggingface.co/BAAI/bge-m3
- [21] https://github.com/FlagOpen/FlagEmbedding/blob/master/C_MTEB/README.md
- [22] https://docs.trychroma.com/
