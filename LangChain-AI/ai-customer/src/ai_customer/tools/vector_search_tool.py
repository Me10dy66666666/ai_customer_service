from langchain_core.tools import tool
from ai_customer.core.base.vector_store import VectorStore
from ai_customer.config.settings import settings
from typing import Optional, List, Dict

# 全局单例 VectorStore（避免每次查询都重新加载）
_VECTOR_STORE: Optional[VectorStore] = None

#获取向量数据库
def get_vector_store() -> VectorStore:
    global _VECTOR_STORE
    if _VECTOR_STORE is None:
        _VECTOR_STORE = VectorStore(
            collection_name=settings.vector.collection_name,
            embedding_model=settings.vector.EMBEDDING_MODEL,
            persist_path=settings.vector.PERSIST_PATH,
            distance_metric="cosine"
        )
    return _VECTOR_STORE

@tool("query_knowledge", description="根据用户的提问从知识库中查询信息。")
def query_knowledge(query: str, top_k: int = 5) -> str:
    """
    从向量数据库中检索与查询最相关的知识片段。
    
    Args:
        query: 用户的问题或查询文本。
        top_k: 返回的匹配结果数量，默认 5。
    
    Returns:
        格式化的检索结果字符串，包含每个块的内容和元数据。
    """
    vector_store = get_vector_store()

    # VectorStore.query() 返回字典 {ids, documents, metadatas, distances}，非 Document 列表
    result = vector_store.query(query, n_results=top_k)

    documents = result.get("documents") or []
    metadatas = result.get("metadatas") or []

    if not documents:
        return "未找到与您问题相关的信息。"

    # 格式化输出：每个块包含来源和内容
    formatted_results = []
    for i, content in enumerate(documents, 1):
        meta = metadatas[i - 1] if i - 1 < len(metadatas) else {}
        source = meta.get("table_name", "未知表") if meta else "未知表"
        row_id = meta.get("row_id", "未知行") if meta else "未知行"
        formatted_results.append(
            f"【结果 {i}】来源：{source}（{row_id}）\n{content.strip()}\n"
        )

    return "\n".join(formatted_results)



# 获取知识库工具,统一管理工具
def get_knowledge_base_tools() -> list:
    """
    获取知识库工具列表。
    
    Returns:
        包含知识库工具列表的列表。
    """
    return [query_knowledge]
