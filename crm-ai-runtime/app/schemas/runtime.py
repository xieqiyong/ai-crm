from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")


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
