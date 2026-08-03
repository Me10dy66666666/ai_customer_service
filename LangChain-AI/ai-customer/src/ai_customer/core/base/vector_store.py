import logging
import os
from typing import List, Dict, Optional, Any, Union
import chromadb
from chromadb.utils import embedding_functions

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VectorStore:
    """
    生产级 ChromaDB 向量存储封装类。
    支持自定义嵌入模型、批量添加、查询过滤、异常自动恢复等。
    """

    def __init__(
        self,
        collection_name: str ,
        persist_directory: str ,
        embedding_model: str ,
        distance_metric: str = "cosine",
        **kwargs
    ):
        """
        初始化向量存储客户端与集合。

        Args:
            collection_name: 集合名称（类似表名）
            persist_directory: 本地持久化目录
            embedding_model: 嵌入模型名称（支持 sentence-transformers 或 OpenAI 等）
            distance_metric: 距离度量方式，支持 "cosine", "l2", "ip"
            **kwargs: 传递给 chromadb.PersistentClient 的其他参数
        """
        self.collection_name = collection_name
        self.persist_directory = persist_directory

        # 1. 创建嵌入函数
        try:
            if embedding_model.startswith("qwen"):
                # Qwen 模型通过 DashScope OpenAI 兼容接口调用
                # 需要 DASHSCOPE_API_KEY 环境变量
                api_key = os.getenv("DASHSCOPE_API_KEY", "")
                self.embedding_fn = embedding_functions.OpenAIEmbeddingFunction(
                    api_key=api_key,
                    api_base="https://dashscope.aliyuncs.com/compatible-mode/v1",
                    model_name=embedding_model,
                )
            else:
                # 本地 sentence-transformers 模型
                self.embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
                    model_name=embedding_model
                )
            logger.info(f"加载嵌入模型成功: {embedding_model}")
        except Exception as e:
            logger.error(f"加载嵌入模型失败: {e}")
            raise

        # 2. 创建持久化客户端
        try:
            self.client = chromadb.PersistentClient(
                path=persist_directory,
                **kwargs
            )
            logger.info(f"连接持久化目录成功: {persist_directory}")
        except Exception as e:
            logger.error(f"连接 ChromaDB 失败: {e}")
            raise

        # 3. 获取或创建集合，指定距离度量
        try:
            self.collection = self.client.get_or_create_collection(
                name=collection_name,
                embedding_function=self.embedding_fn,
                metadata={"hnsw:space": distance_metric}
            )
            logger.info(f"集合 '{collection_name}' 就绪，距离度量: {distance_metric}")
        except Exception as e:
            logger.error(f"获取/创建集合失败: {e}")
            raise

    def add_documents(
        self,
        ids: List[str],
        documents: List[str],
        metadatas: Optional[List[Dict]] = None,
        batch_size: int = 1000,
        show_progress: bool = True
    ) -> int:
        """
        批量添加文档到向量库（自动分块以避免内存溢出）。

        Args:
            ids: 文档唯一 ID 列表
            documents: 文档文本列表
            metadatas: 元数据字典列表（可选）
            batch_size: 每批次添加的文档数量
            show_progress: 是否显示进度条（需安装 tqdm）

        Returns:
            成功添加的文档数量
        """
        if len(ids) != len(documents):
            raise ValueError("ids 和 documents 长度必须一致")
        if metadatas and len(metadatas) != len(ids):
            raise ValueError("metadatas 长度必须与 ids 一致")

        total = len(ids)
        added = 0

        # 可选进度条
        iterator = range(0, total, batch_size)
        if show_progress:
            try:
                from tqdm import tqdm
                iterator = tqdm(iterator, desc="添加文档", unit="batch")
            except ImportError:
                logger.warning("未安装 tqdm，进度条不可用")

        for i in iterator:
            end = min(i + batch_size, total)
            batch_ids = ids[i:end]
            batch_docs = documents[i:end]
            batch_metas = metadatas[i:end] if metadatas else None

            try:
                self.collection.add(
                    ids=batch_ids,
                    documents=batch_docs,
                    metadatas=batch_metas
                )
                added += len(batch_ids)
            except Exception as e:
                logger.error(f"批次添加失败 (索引 {i}-{end}): {e}")
                # 生产环境可选择性重试或跳过，此处继续执行
                continue

        logger.info(f"成功添加 {added}/{total} 条文档")
        return added

    def query(
        self,
        query_text: str,
        n_results: int = 5,
        where: Optional[Dict] = None,
        where_document: Optional[Dict] = None,
        include: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        执行语义查询，返回最相似的文档。

        Args:
            query_text: 查询文本
            n_results: 返回结果数量
            where: 元数据过滤条件，例如 {"source": "wiki"}
            where_document: 文档内容过滤条件，例如 {"$contains": "Python"}
            include: 指定返回字段，默认只返回 documents, metadatas, distances

        Returns:
            查询结果字典，包含 ids, distances, documents, metadatas 等
        """
        if not query_text or not isinstance(query_text, str):
            raise ValueError("query_text 必须是非空字符串")

        try:
            result = self.collection.query(
                query_texts=[query_text],
                n_results=n_results,
                where=where,
                where_document=where_document,
                include=include or ["documents", "metadatas", "distances"]
            )
            # 将结果中的单元素列表扁平化（简化调用方取值）
            return {
                k: v[0] if isinstance(v, list) and len(v) == 1 and k != "ids" else v
                for k, v in result.items()
            }
        except Exception as e:
            logger.error(f"查询失败: {e}")
            raise

    def delete_documents(self, ids: List[str]) -> int:
        """
        根据 ID 删除文档。

        Args:
            ids: 要删除的文档 ID 列表

        Returns:
            成功删除的数量
        """
        if not ids:
            return 0
        try:
            self.collection.delete(ids=ids)
            logger.info(f"成功删除 {len(ids)} 条文档")
            return len(ids)
        except Exception as e:
            logger.error(f"删除失败: {e}")
            raise

    def get_document(self, doc_id: str) -> Optional[Dict]:
        """
        根据 ID 获取单条文档详情。

        Args:
            doc_id: 文档 ID

        Returns:
            包含文档内容、元数据等的字典，若不存在返回 None
        """
        try:
            result = self.collection.get(ids=[doc_id])
            if result and result['ids']:
                return {
                    'id': result['ids'][0],
                    'document': result['documents'][0] if result['documents'] else None,
                    'metadata': result['metadatas'][0] if result['metadatas'] else None
                }
        except Exception as e:
            logger.error(f"获取文档失败: {e}")
        return None

    def count(self) -> int:
        """返回集合中的文档总数"""
        return self.collection.count()

    def list_collections(self) -> List[str]:
        """列出当前客户端中的所有集合名称"""
        return [c.name for c in self.client.list_collections()]

    def delete_collection(self) -> None:
        """删除当前集合（谨慎操作）"""
        try:
            self.client.delete_collection(self.collection_name)
            logger.warning(f"集合 '{self.collection_name}' 已删除")
        except Exception as e:
            logger.error(f"删除集合失败: {e}")
            raise

    def __repr__(self) -> str:
        return f"<VectorStore(collection='{self.collection_name}', count={self.count()})>"