import logging

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
            row = await database_client.fetch_one(
                """
                select id, code, scene_code, scene_name, name, description, system_prompt,
                       model_provider, model_name, base_url, api_key_env as api_key,
                       max_iters, extra_config_json
                from agents
                where tenant_id = %s and id = %s and enabled = true and deleted = false
                limit 1
                """,
                (self._to_int(request.tenant_id), self._to_int(request.agent.id)),
            )
            if row is not None:
                return AgentDefinition.from_row(row)
        row = await database_client.fetch_one(
            """
            select id, code, scene_code, scene_name, name, description, system_prompt,
                   model_provider, model_name, base_url, api_key_env as api_key,
                   max_iters, extra_config_json
            from agents
            where tenant_id = %s and scene_code = %s and enabled = true and deleted = false
            order by updated_at desc
            limit 1
            """,
            (self._to_int(request.tenant_id), request.scene_code),
        )
        if row is None:
            return AgentDefinition.from_runtime_agent(request.agent)
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
