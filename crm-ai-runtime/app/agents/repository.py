import logging
from typing import Any

from app.agents.models import AgentDefinition, McpServerDefinition, SkillDefinition
from app.core.config import settings
from app.platform.database import database_client
from app.schemas.runtime import RuntimeRunRequest

logger = logging.getLogger(__name__)


class AgentRepository:
    async def resolve_agent(self, request: RuntimeRunRequest) -> AgentDefinition:
        if self._use_database(request):
            try:
                agent = await self._load_agent_by_scene(request)
                if agent.id:
                    return agent
            except ValueError:
                raise
            except Exception as ex:
                if settings.agent_config_fail_fast:
                    raise
                logger.warning("读取数据库智能体配置失败，使用请求中的智能体配置，原因：%s", ex)
        return AgentDefinition.from_runtime_agent(request.agent)

    async def resolve_skills(self, request: RuntimeRunRequest, agent: AgentDefinition) -> list[SkillDefinition]:
        values = [SkillDefinition.from_runtime_resource(item) for item in request.skills]
        if not self._use_database(request) or not agent.id:
            return values
        try:
            rows = await database_client.fetch_all(
                """
                select id, skill_key, name, content
                from agent_skill
                where tenant_id = %s and agent_id = %s and enabled = true and deleted = false
                order by updated_at desc
                """,
                (self._to_int(request.tenant_id), self._to_int(agent.id)),
            )
            return [SkillDefinition.from_row(item) for item in rows]
        except Exception as ex:
            if settings.agent_config_fail_fast:
                raise
            logger.warning("读取数据库Skill配置失败，使用请求中的Skill配置，原因：%s", ex)
            return values

    async def resolve_mcps(self, request: RuntimeRunRequest, agent: AgentDefinition) -> list[McpServerDefinition]:
        values = [McpServerDefinition.from_runtime_resource(item) for item in request.mcps]
        if not self._use_database(request) or not agent.id:
            return values
        try:
            rows = await database_client.fetch_all(
                """
                select id, name, transport_type, endpoint, command, arguments_json, headers_json
                from agent_mcp
                where tenant_id = %s and agent_id = %s and enabled = true and deleted = false
                order by updated_at desc
                """,
                (self._to_int(request.tenant_id), self._to_int(agent.id)),
            )
            return [McpServerDefinition.from_row(item) for item in rows]
        except Exception as ex:
            if settings.agent_config_fail_fast:
                raise
            logger.warning("读取数据库MCP配置失败，使用请求中的MCP配置，原因：%s", ex)
            return values

    async def _load_agent_by_scene(self, request: RuntimeRunRequest) -> AgentDefinition:
        if request.agent and request.agent.id:
            row = await self._load_agent_by_id(request)
            if row is not None:
                return self._agent_definition(row)
            raise ValueError("当前租户和场景下不存在指定智能体，或智能体已停用")
        row = await database_client.fetch_one(
            """
            select a.id, a.code, a.scene_code, a.scene_name, a.name, a.description, a.system_prompt,
                   a.model_config_id,
                   case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                   case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                   case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                   case when a.model_config_id is null then a.api_key_env else m.api_key_env end as api_key,
                   a.max_iters, a.extra_config_json,
                   m.id as resolved_model_config_id, m.enabled as model_config_enabled
            from agents a
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.scene_code = %s and a.enabled = true and a.deleted = false
            order by a.updated_at desc
            limit 1
            """,
            (self._to_int(request.tenant_id), request.scene_code),
        )
        if row is None:
            return AgentDefinition.from_runtime_agent(request.agent)
        return self._agent_definition(row)

    async def _load_agent_by_id(self, request: RuntimeRunRequest) -> dict[str, Any] | None:
        scene_code = (request.scene_code or "").strip()
        if scene_code:
            return await database_client.fetch_one(
                """
                select a.id, a.code, a.scene_code, a.scene_name, a.name, a.description, a.system_prompt,
                       a.model_config_id,
                       case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                       case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                       case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                       case when a.model_config_id is null then a.api_key_env else m.api_key_env end as api_key,
                       a.max_iters, a.extra_config_json,
                       m.id as resolved_model_config_id, m.enabled as model_config_enabled
                from agents a
                left join llm_model_config m
                  on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
                where a.tenant_id = %s and a.id = %s and a.scene_code = %s
                  and a.enabled = true and a.deleted = false
                limit 1
                """,
                (self._to_int(request.tenant_id), self._to_int(request.agent.id), scene_code),
            )
        return await database_client.fetch_one(
            """
            select a.id, a.code, a.scene_code, a.scene_name, a.name, a.description, a.system_prompt,
                   a.model_config_id,
                   case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                   case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                   case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                   case when a.model_config_id is null then a.api_key_env else m.api_key_env end as api_key,
                   a.max_iters, a.extra_config_json,
                   m.id as resolved_model_config_id, m.enabled as model_config_enabled
            from agents a
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.id = %s and a.enabled = true and a.deleted = false
            limit 1
            """,
            (self._to_int(request.tenant_id), self._to_int(request.agent.id)),
        )

    def _agent_definition(self, row: dict[str, Any]) -> AgentDefinition:
        if row.get("model_config_id") is not None:
            if row.get("resolved_model_config_id") is None:
                raise ValueError("智能体关联的大模型配置不存在")
            if not bool(row.get("model_config_enabled")):
                raise ValueError("智能体关联的大模型配置已停用")
        return AgentDefinition.from_row(row)

    def _use_database(self, request: RuntimeRunRequest) -> bool:
        return (
            settings.agent_config_source.strip().lower() == "database"
            and database_client.enabled()
            and bool((request.scene_code or "").strip())
        )

    def _to_int(self, value):
        if value is None or value == "":
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None


agent_repository = AgentRepository()
