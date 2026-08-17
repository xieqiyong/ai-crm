from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="allow", coerce_numbers_to_str=True)


class RuntimeAgent(CamelModel):
    id: str | None = None
    code: str | None = None
    scene_code: str | None = Field(default=None, alias="sceneCode")
    scene_name: str | None = Field(default=None, alias="sceneName")
    name: str | None = None
    description: str | None = None
    system_prompt: str | None = Field(default=None, alias="systemPrompt")
    model_provider: str | None = Field(default=None, alias="modelProvider")
    model_name: str | None = Field(default=None, alias="modelName")
    base_url: str | None = Field(default=None, alias="baseUrl")
    api_key: str | None = Field(default=None, alias="apiKey")
    max_iters: int | None = Field(default=None, alias="maxIters")
    extra_config_json: str | None = Field(default=None, alias="extraConfigJson")


class RuntimeResource(CamelModel):
    id: str | None = None
    name: str | None = None
    code: str | None = None
    description: str | None = None
    content: str | None = None
    config: dict[str, Any] = Field(default_factory=dict)


class RuntimeRunRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    run_id: str | None = Field(default=None, alias="runId")
    conversation_id: str | None = Field(default=None, alias="conversationId")
    scene_code: str | None = Field(default=None, alias="sceneCode")
    business_type: str | None = Field(default=None, alias="businessType")
    business_id: str | None = Field(default=None, alias="businessId")
    message: str = ""
    session_id: str | None = Field(default=None, alias="sessionId")
    injected_prompt: str | None = Field(default=None, alias="injectedPrompt")
    rendered_system_prompt: str | None = Field(default=None, alias="renderedSystemPrompt")
    context: dict[str, Any] = Field(default_factory=dict)
    agent: RuntimeAgent | None = None
    mcps: list[RuntimeResource] = Field(default_factory=list)
    skills: list[RuntimeResource] = Field(default_factory=list)


class RuntimeEvent(CamelModel):
    id: str
    type: str
    content: str | None = None
    tool_name: str | None = Field(default=None, alias="toolName")
    metadata: dict[str, Any] = Field(default_factory=dict)


class RuntimeRunResponse(CamelModel):
    success: bool = True
    output: str = ""
    events: list[RuntimeEvent] = Field(default_factory=list)
    run_id: str | None = Field(default=None, alias="runId")
    conversation_id: str | None = Field(default=None, alias="conversationId")
    thread_id: str | None = Field(default=None, alias="threadId")
    checkpoint_enabled: bool = Field(default=False, alias="checkpointEnabled")
    trace_enabled: bool = Field(default=False, alias="traceEnabled")
    trace_id: str | None = Field(default=None, alias="traceId")


class AssistantAttachment(CamelModel):
    file_name: str | None = Field(default=None, alias="fileName")
    content_type: str | None = Field(default=None, alias="contentType")
    size: int | None = None
    storage_key: str | None = Field(default=None, alias="storageKey")
    url: str | None = None


class AssistantAgentListRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")


class AssistantConversationListRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    agent_id: str | None = Field(default=None, alias="agentId")


class AssistantMessagesRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    conversation_id: str = Field(alias="conversationId")


class AssistantConversationActionRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    conversation_id: str = Field(alias="conversationId")


class AssistantRunRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    request_id: str = Field(alias="requestId")
    agent_id: str = Field(alias="agentId")
    conversation_id: str | None = Field(default=None, alias="conversationId")
    message: str = ""
    data_scope: str | None = Field(default=None, alias="dataScope")
    permissions: list[str] = Field(default_factory=list)
    attachments: list[AssistantAttachment] = Field(default_factory=list)


class AssistantStopRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str = Field(alias="userId")
    request_id: str = Field(alias="requestId")


class AgentManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    page_no: int = Field(default=1, alias="pageNo")
    page_size: int = Field(default=20, alias="pageSize")


class AgentIdManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    id: str | None = None
    agent_id: str | None = Field(default=None, alias="agentId")


class AgentSaveManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    id: str | None = None
    code: str | None = None
    scene_code: str | None = Field(default=None, alias="sceneCode")
    scene_name: str | None = Field(default=None, alias="sceneName")
    name: str | None = None
    description: str | None = None
    system_prompt: str | None = Field(default=None, alias="systemPrompt")
    model_config_id: str | None = Field(default=None, alias="modelConfigId")
    model_provider: str | None = Field(default=None, alias="modelProvider")
    model_name: str | None = Field(default=None, alias="modelName")
    base_url: str | None = Field(default=None, alias="baseUrl")
    api_key: str | None = Field(default=None, alias="apiKey")
    max_iters: int | None = Field(default=None, alias="maxIters")
    extra_config_json: str | None = Field(default=None, alias="extraConfigJson")
    remark: str | None = None
    enabled: bool | None = None


class AgentMcpSaveManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    id: str | None = None
    agent_id: str = Field(alias="agentId")
    name: str | None = None
    transport_type: str | None = Field(default=None, alias="transportType")
    endpoint: str | None = None
    command: str | None = None
    arguments_json: str | None = Field(default=None, alias="argumentsJson")
    headers_json: str | None = Field(default=None, alias="headersJson")
    enabled: bool | None = None


class AgentSkillSaveManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    id: str | None = None
    agent_id: str = Field(alias="agentId")
    skill_key: str | None = Field(default=None, alias="skillKey")
    name: str | None = None
    content: str | None = None
    enabled: bool | None = None


class AgentTokenQuotaAssignManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    scope: str | None = None
    department_id: str | None = Field(default=None, alias="departmentId")
    user_ids: list[str] = Field(default_factory=list, alias="userIds")
    daily_token_limit: int | None = Field(default=None, alias="dailyTokenLimit")
    enabled: bool | None = None
    remark: str | None = None


class AgentTokenQuotaClearManageRequest(CamelModel):
    tenant_id: str = Field(alias="tenantId")
    user_id: str | None = Field(default=None, alias="userId")
    target_user_id: str | None = Field(default=None, alias="targetUserId")
    clear_user_id: str | None = Field(default=None, alias="clearUserId")
