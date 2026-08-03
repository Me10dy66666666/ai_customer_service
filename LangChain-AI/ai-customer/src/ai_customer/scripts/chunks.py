from langchain_text_splitters import RecursiveCharacterTextSplitter
from ai_customer.core.base.tables import aiCustomer_tab
from ai_customer.core.base.mysqlConnector import get_engine
from sqlalchemy import inspect
from ai_customer.core.base.vector_store import VectorStore
from sqlalchemy import select


from ai_customer.config.settings import settings

def fetch_and_chunk():
    engine = get_engine()
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=1000, 
        chunk_overlap=0,
        separators=["\n\n", "\n", "。", "！", "？"]
        )
    
    # ---------- 提取规则依然定义在业务层（chunks.py），但利用表名做映射 ----------
    # 注意：这里的 Key 是表的名字（字符串），因为 Table 对象在运行时才能确定，
    # 用字符串做 Key 最稳定、最易读。
    extractor_map = {
        "knowledge_documents": lambda row: f"文章标题：{row.title}\n文章内容：{row.content}\n分类：{row.category}\n发布时间：{row.plushed_at}\n审核人：{row.reviewed_by}",
        #"historical_orders": lambda row: f"订单id: {row.id}\n用户id:  {row.user_id}\n购买了:  {row.product_name}\n有关配置为:  {row.product_model}\n数量为: {row.quantity}\n订单金额:  {row.amount} 元\n总价为:  {row.total_amount} 元\n订单状态:  {row.order_status}\n下单时间:  {row.create_time}。",
        #"chat_messages": lambda row: f"会话id:  {row.session_id}\n发送人:  {row.sender_type}\n发送人id: {row.sender_id}\n消息内容: {row.content}\n会话内消息序号：{row.message_seq}\n时间： {row.create_time}"
    }

    vectorDB = VectorStore(
    collection_name=settings.vector.collection_name,
    embedding_model=settings.vector.EMBEDDING_MODEL,
    persist_path=settings.vector.PERSIST_PATH,
    distance_metric="cosine"
)

    # 初始化容器
    all_ids = []
    all_chunks = []
    all_metadatas = []



    
    with engine.connect() as conn:
        # ---------- 遍历统一注册表（aiCustomer_tab） ----------
        for table in aiCustomer_tab:
            print(f"正在处理表: {table.name}")
            
            # 根据表名动态获取对应的提取器
            extractor = extractor_map.get(table.name)
            if not extractor:
                print(f"警告：表 {table.name} 未配置提取器，跳过")
                continue

            # 动态获取主键（用于行标识）
            pk_cols = [c.name for c in inspect(table).primary_key]


            result = conn.execute(select(table))
            for row in result:
                content = extractor(row)
                # ... 后续切块入库逻辑 ...
                chunks = text_splitter.split_text(content)

                # 自动提取元数据（过滤长文本）
            row_dict = dict(row._mapping)
            blacklist = ["content", "product_model"]  # 根据实际表结构调整
            auto_meta = {k: v for k, v in row_dict.items() if k not in blacklist}
            
            # 生成行唯一ID
            pk_vals = "_".join(str(getattr(row, c)) for c in pk_cols)
            row_id = f"{table.name}_{pk_vals}"
            
            for idx, chunk in enumerate(chunks):
                all_chunks.append(chunk)
                all_metadatas.append({
                    **auto_meta,
                    "table_name": table.name,
                    "row_id": row_id,
                    "chunk_index": idx
                })
                all_ids.append(f"{row_id}_chunk_{idx}")


    print(f"总共收集了 {len(all_chunks)} 个块，开始批量入库...")
    vectorDB.add_documents(
    documents=all_chunks,
    metadatas=all_metadatas,
    ids=all_ids 
    )
print("入库完成！")
