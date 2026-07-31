from sqlalchemy import MetaData, Table
from ai_customer.core.base.mysqlConnector import get_engine

metadata = MetaData()
knowledge_documents = Table("knowledge_documents", metadata, autoload_with=get_engine())
#historical_orders = Table("historical_orders", metadata, autoload_with=get_engine())
#chat_messages = Table("chat_messages", metadata, autoload_with=get_engine())
aiCustomer_tab = [knowledge_documents]
