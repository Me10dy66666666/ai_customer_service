"""临时脚本：定位 langchain 在 Python 3.14 下的导入失败点。"""
import sys

try:
    import langchain
    print("langchain imported OK:", langchain.__version__)
except Exception as e:
    import traceback
    print("langchain import FAILED:")
    traceback.print_exc()

print("---")
try:
    from langchain.agents import AgentExecutor, create_tool_calling_agent
    print("langchain.agents imported OK")
except Exception as e:
    import traceback
    print("langchain.agents import FAILED:")
    traceback.print_exc()
