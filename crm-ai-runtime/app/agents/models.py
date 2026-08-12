from dataclasses import dataclass, field
from typing import Any

from app.schemas.runtime import RuntimeAgent, RuntimeResource


@dataclass
class AgentDefinition:
    id: str | None = None
    code: str | None = None
    scene_code: str | None = None
    scene_name: str | None = None
    name: str | None = None
    description: str | None = None
    system_prompt: str | None = None
    model_provider: str | None = None
    model_name: str | None = None
    base_url: str | None = None
    api_key: str | None = None
    max_iters: int | None = None
    extra_config_json: str | None = None

    @classmethod
    def from_runtime_agent(cls, value: RuntimeAgent | None):
        if value is None:
            return cls()
        return cls(
            id=value.id,
            code=value.code,
            scene_code=value.scene_code,
            scene_name=value.scene_name,
            name=value.name,
            description=value.description,
            system_prompt=value.system_prompt,
            model_provider=value.model_provider,
            model_name=value.model_name,
            base_url=value.base_url,
            api_key=value.api_key,
            max_iters=value.max_iters,
            extra_config_json=value.extra_config_json,
        )

    @classmethod
    def from_row(cls, row: dict[str, Any]):
        return cls(
            id=str(row.get("id")) if row.get("id") is not None else None,
            code=row.get("code"),
            scene_code=row.get("scene_code"),
            scene_name=row.get("scene_name"),
            name=row.get("name"),
            description=row.get("description"),
            system_prompt=row.get("system_prompt"),
            model_provider=row.get("model_provider"),
            model_name=row.get("model_name"),
            base_url=row.get("base_url"),
            api_key=row.get("api_key"),
            max_iters=row.get("max_iters"),
            extra_config_json=row.get("extra_config_json"),
        )

    def to_runtime_agent(self) -> RuntimeAgent:
        return RuntimeAgent(
            id=self.id,
            code=self.code,
            sceneCode=self.scene_code,
            sceneName=self.scene_name,
            name=self.name,
            description=self.description,
            systemPrompt=self.system_prompt,
            modelProvider=self.model_provider,
            modelName=self.model_name,
            baseUrl=self.base_url,
            apiKey=self.api_key,
            maxIters=self.max_iters,
            extraConfigJson=self.extra_config_json,
        )


@dataclass
class SkillDefinition:
    id: str | None = None
    code: str | None = None
    name: str | None = None
    description: str | None = None
    content: str | None = None
    config: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_runtime_resource(cls, value: RuntimeResource):
        return cls(
            id=value.id,
            code=value.code,
            name=value.name,
            description=value.description,
            content=value.content,
            config=value.config or {},
        )

    @classmethod
    def from_row(cls, row: dict[str, Any]):
        return cls(
            id=str(row.get("id")) if row.get("id") is not None else None,
            code=row.get("skill_key"),
            name=row.get("name"),
            content=row.get("content"),
        )

    def to_runtime_resource(self) -> RuntimeResource:
        return RuntimeResource(
            id=self.id,
            code=self.code,
            name=self.name,
            description=self.description,
            content=self.content,
            config=self.config,
        )


@dataclass
class McpServerDefinition:
    id: str | None = None
    name: str | None = None
    code: str | None = None
    description: str | None = None
    transport_type: str | None = None
    endpoint: str | None = None
    command: str | None = None
    arguments_json: str | None = None
    headers_json: str | None = None
    config: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_runtime_resource(cls, value: RuntimeResource):
        config = value.config or {}
        return cls(
            id=value.id,
            code=value.code,
            name=value.name,
            description=value.description,
            transport_type=config.get("transportType") or config.get("transport_type"),
            endpoint=config.get("endpoint"),
            command=config.get("command"),
            arguments_json=config.get("argumentsJson") or config.get("arguments_json"),
            headers_json=config.get("headersJson") or config.get("headers_json"),
            config=config,
        )

    @classmethod
    def from_row(cls, row: dict[str, Any]):
        return cls(
            id=str(row.get("id")) if row.get("id") is not None else None,
            name=row.get("name"),
            transport_type=row.get("transport_type"),
            endpoint=row.get("endpoint"),
            command=row.get("command"),
            arguments_json=row.get("arguments_json"),
            headers_json=row.get("headers_json"),
        )

    def to_runtime_resource(self) -> RuntimeResource:
        config = dict(self.config)
        config.update({
            "transportType": self.transport_type,
            "endpoint": self.endpoint,
            "command": self.command,
            "argumentsJson": self.arguments_json,
            "headersJson": self.headers_json,
        })
        return RuntimeResource(
            id=self.id,
            code=self.code,
            name=self.name,
            description=self.description,
            config=config,
        )
