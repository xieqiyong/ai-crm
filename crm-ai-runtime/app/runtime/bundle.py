from dataclasses import dataclass

from app.mcp.registry import McpToolBundle
from app.runtime.execution_context import AgentExecutionContext


@dataclass
class RuntimeGraphBundle:
    graph: object
    execution_context: AgentExecutionContext
    mcp_bundle: McpToolBundle
    input_state: dict
