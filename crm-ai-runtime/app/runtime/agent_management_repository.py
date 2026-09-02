import json
import re
from datetime import date, datetime
from typing import Any

from app.core.config import settings
from app.platform.database import database_client
from app.platform.id_generator import id_generator


class AgentManagementRepository:
    async def page(self, tenant_id: str, page_no: int, page_size: int) -> dict[str, Any]:
        self._require_database()
        safe_page_no = max(int(page_no or 1), 1)
        safe_page_size = min(max(int(page_size or 20), 1), 200)
        offset = (safe_page_no - 1) * safe_page_size
        total_row = await database_client.fetch_one(
            "select count(1) as total from agents where tenant_id = %s and deleted = false",
            (self._to_int(tenant_id),),
        )
        rows = await database_client.fetch_all(
            """
            select a.id, a.tenant_id, a.code, a.scene_code, a.scene_name, a.name,
                   a.description, a.system_prompt, a.model_config_id,
                   case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                   case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                   case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                   a.max_iters, a.extra_config_json, a.remark, a.enabled, a.frontend_visible,
                   a.deleted,
                   a.created_at, a.updated_at
            from agents a
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.deleted = false
            order by a.created_at desc
            limit %s offset %s
            """,
            (self._to_int(tenant_id), safe_page_size, offset),
        )
        return {
            "total": int(total_row.get("total") or 0) if total_row else 0,
            "pageNo": safe_page_no,
            "pageSize": safe_page_size,
            "records": [self._agent_response(row) for row in rows],
        }

    async def detail(self, tenant_id: str, agent_id: str | None) -> dict[str, Any]:
        return self._agent_response(await self._find_agent(tenant_id, agent_id))

    async def scenes(self, tenant_id: str) -> list[dict[str, Any]]:
        self._require_database()
        managed_rows = await database_client.fetch_all(
            """
            select s.id, s.tenant_id, s.code, s.name, s.description, s.sort_no, s.enabled,
                   s.deleted, s.created_at, s.updated_at, count(a.id) as agent_count
            from agent_scene s
            left join agents a on a.tenant_id = s.tenant_id and a.scene_code = s.code and a.deleted = false
            where s.tenant_id = %s and s.deleted = false
            group by s.id, s.tenant_id, s.code, s.name, s.description, s.sort_no, s.enabled,
                     s.deleted, s.created_at, s.updated_at
            order by s.sort_no asc, s.created_at asc
            """,
            (self._to_int(tenant_id),),
        )
        inferred_rows = await database_client.fetch_all(
            """
            select null as id, a.tenant_id, a.scene_code as code,
                   coalesce(max(a.scene_name), a.scene_code) as name,
                   null as description, 0 as sort_no, true as enabled, false as deleted,
                   min(a.created_at) as created_at, max(a.updated_at) as updated_at,
                   count(a.id) as agent_count
            from agents a
            where a.tenant_id = %s and a.deleted = false and a.scene_code is not null
              and not exists (
                  select 1 from agent_scene s
                  where s.tenant_id = a.tenant_id and s.code = a.scene_code and s.deleted = false
              )
            group by a.tenant_id, a.scene_code
            order by min(a.created_at) asc
            """,
            (self._to_int(tenant_id),),
        )
        return [self._scene_response(row) for row in managed_rows + inferred_rows]

    async def save_scene(self, tenant_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        self._require_database()
        name = self._trim(payload.get("name"))
        if not name:
            raise ValueError("场景名称不能为空")
        now = datetime.now()
        scene_id = self._to_int(payload.get("id"))
        existing = await self._find_scene(tenant_id, str(scene_id), False) if scene_id else None
        if not scene_id:
            scene_id = id_generator.next_id()
        code = self._trim(existing.get("code")) if existing else self._trim(payload.get("code"))
        if not code:
            code = "SCENE_" + str(scene_id)
        duplicate = await database_client.fetch_one(
            """
            select id from agent_scene
            where tenant_id = %s and code = %s and deleted = false and id <> %s
            limit 1
            """,
            (self._to_int(tenant_id), code, scene_id),
        )
        if duplicate is not None:
            raise ValueError("场景已存在")
        values = (
            scene_id,
            self._to_int(tenant_id),
            code,
            name,
            self._trim(payload.get("description")),
            self._int(payload.get("sortNo") or payload.get("sort_no")),
            self._bool(payload.get("enabled"), True),
            False,
            existing.get("created_at") if existing else now,
            now,
        )
        await database_client.execute(
            """
            insert into agent_scene (
                id, tenant_id, code, name, description, sort_no, enabled, deleted, created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            on conflict (id) do update set
                name = excluded.name,
                description = excluded.description,
                sort_no = excluded.sort_no,
                enabled = excluded.enabled,
                deleted = false,
                updated_at = excluded.updated_at
            """,
            values,
        )
        await database_client.execute(
            """
            update agents set scene_name = %s, updated_at = %s
            where tenant_id = %s and scene_code = %s and deleted = false
            """,
            (name, now, self._to_int(tenant_id), code),
        )
        return self._scene_response(await self._find_scene(tenant_id, str(scene_id), True))

    async def delete_scene(self, tenant_id: str, scene_id: str | None) -> bool:
        scene = await self._find_scene(tenant_id, scene_id, True)
        count_row = await database_client.fetch_one(
            """
            select count(1) as total from agents
            where tenant_id = %s and scene_code = %s and deleted = false
            """,
            (self._to_int(tenant_id), scene.get("code")),
        )
        if self._int(count_row.get("total") if count_row else 0) > 0:
            raise ValueError("当前场景已关联智能体，不能删除")
        await database_client.execute(
            """
            update agent_scene set deleted = true, updated_at = %s
            where tenant_id = %s and id = %s
            """,
            (datetime.now(), self._to_int(tenant_id), self._to_int(scene_id)),
        )
        return True

    async def save_agent(self, tenant_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        self._require_database()
        name = self._trim(payload.get("name"))
        if not name:
            raise ValueError("智能体名称不能为空")
        now = datetime.now()
        agent_id = self._to_int(payload.get("id"))
        existing = None
        if agent_id:
            existing = await self._find_agent(tenant_id, str(agent_id))
        else:
            agent_id = id_generator.next_id()
        model_config = await self._load_model_config(tenant_id, payload.get("modelConfigId") or payload.get("model_config_id"))
        if model_config:
            model_config_id = self._to_int(model_config.get("id"))
            model_provider = self._trim(model_config.get("provider")) or "OPENAI"
            model_name = self._trim(model_config.get("model_name"))
            base_url = self._trim(model_config.get("base_url"))
            api_key = self._trim(model_config.get("api_key"))
        else:
            model_config_id = None
            model_provider = self._trim(payload.get("modelProvider") or payload.get("model_provider")) or "OPENAI"
            model_name = self._trim(payload.get("modelName") or payload.get("model_name"))
            base_url = self._trim(payload.get("baseUrl") or payload.get("base_url"))
            api_key = self._trim(payload.get("apiKey") or payload.get("api_key"))
            if not api_key and existing:
                api_key = self._trim(existing.get("api_key"))
            if not model_name:
                raise ValueError("模型名称不能为空")
            if not api_key:
                raise ValueError("模型密钥不能为空")
        scene_code = self._trim(payload.get("sceneCode") or payload.get("scene_code"))
        if not scene_code:
            raise ValueError("业务场景标识不能为空")
        scene = await self._find_scene_by_code(tenant_id, scene_code)
        if scene is None:
            raise ValueError("业务场景不存在，请先创建场景")
        if not self._bool(scene.get("enabled"), True):
            raise ValueError("业务场景已停用")
        enabled = self._bool(payload.get("enabled"), True)
        frontend_visible = self._bool(
            payload.get("frontendVisible") if "frontendVisible" in payload else payload.get("frontend_visible"),
            self._bool(existing.get("frontend_visible"), True) if existing else True,
        )
        code = self._resolve_agent_code(agent_id, existing, payload, scene_code)
        extra_config_json = self._normalize_extra_config(
            payload.get("extraConfigJson") or payload.get("extra_config_json")
        )
        values = (
            agent_id,
            self._to_int(tenant_id),
            code,
            scene_code,
            self._trim(scene.get("name")),
            name,
            self._trim(payload.get("description")),
            self._trim(payload.get("systemPrompt") or payload.get("system_prompt")),
            model_config_id,
            model_provider,
            model_name,
            base_url,
            api_key,
            self._resolve_max_iters(payload.get("maxIters") or payload.get("max_iters")),
            extra_config_json,
            self._trim(payload.get("remark")),
            enabled,
            frontend_visible,
            False,
            existing.get("created_at") if existing else now,
            now,
        )
        await database_client.execute(
            """
            insert into agents (
                id, tenant_id, code, scene_code, scene_name, name, description, system_prompt,
                model_config_id, model_provider, model_name, base_url, api_key_env, max_iters,
                extra_config_json, remark, enabled, frontend_visible, deleted, created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            on conflict (id) do update set
                code = excluded.code,
                scene_code = excluded.scene_code,
                scene_name = excluded.scene_name,
                name = excluded.name,
                description = excluded.description,
                system_prompt = excluded.system_prompt,
                model_config_id = excluded.model_config_id,
                model_provider = excluded.model_provider,
                model_name = excluded.model_name,
                base_url = excluded.base_url,
                api_key_env = excluded.api_key_env,
                max_iters = excluded.max_iters,
                extra_config_json = excluded.extra_config_json,
                remark = excluded.remark,
                enabled = excluded.enabled,
                frontend_visible = excluded.frontend_visible,
                deleted = false,
                updated_at = excluded.updated_at
            """,
            values,
        )
        return await self.detail(tenant_id, str(agent_id))

    async def mcps(self, tenant_id: str, agent_id: str | None) -> list[dict[str, Any]]:
        await self._find_agent(tenant_id, agent_id)
        rows = await database_client.fetch_all(
            """
            select id, tenant_id, agent_id, name, transport_type, endpoint, command,
                   arguments_json, headers_json, enabled, deleted, created_at, updated_at
            from agent_mcp
            where tenant_id = %s and agent_id = %s and deleted = false
            order by created_at desc
            """,
            (self._to_int(tenant_id), self._to_int(agent_id)),
        )
        return [self._mcp_response(row) for row in rows]

    async def save_mcp(self, tenant_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        agent_id = self._to_int(payload.get("agentId") or payload.get("agent_id"))
        await self._find_agent(tenant_id, str(agent_id))
        name = self._trim(payload.get("name"))
        transport_type = self._trim(payload.get("transportType") or payload.get("transport_type"))
        if not name:
            raise ValueError("MCP名称不能为空")
        if not transport_type:
            raise ValueError("MCP传输类型不能为空")
        now = datetime.now()
        item_id = self._to_int(payload.get("id")) or id_generator.next_id()
        existing = await self._find_mcp(tenant_id, str(item_id), False)
        await database_client.execute(
            """
            insert into agent_mcp (
                id, tenant_id, agent_id, name, transport_type, endpoint, command, arguments_json,
                headers_json, enabled, deleted, created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, false, %s, %s)
            on conflict (id) do update set
                name = excluded.name,
                transport_type = excluded.transport_type,
                endpoint = excluded.endpoint,
                command = excluded.command,
                arguments_json = excluded.arguments_json,
                headers_json = excluded.headers_json,
                enabled = excluded.enabled,
                deleted = false,
                updated_at = excluded.updated_at
            """,
            (
                item_id,
                self._to_int(tenant_id),
                agent_id,
                name,
                transport_type,
                self._trim(payload.get("endpoint")),
                self._trim(payload.get("command")),
                self._trim(payload.get("argumentsJson") or payload.get("arguments_json")),
                self._trim(payload.get("headersJson") or payload.get("headers_json")),
                self._bool(payload.get("enabled"), True),
                existing.get("created_at") if existing else now,
                now,
            ),
        )
        return self._mcp_response(await self._find_mcp(tenant_id, str(item_id), True))

    async def delete_mcp(self, tenant_id: str, item_id: str | None) -> bool:
        await self._find_mcp(tenant_id, item_id, True)
        await database_client.execute(
            "update agent_mcp set deleted = true, updated_at = %s where tenant_id = %s and id = %s",
            (datetime.now(), self._to_int(tenant_id), self._to_int(item_id)),
        )
        return True

    async def skills(self, tenant_id: str, agent_id: str | None) -> list[dict[str, Any]]:
        await self._find_agent(tenant_id, agent_id)
        rows = await database_client.fetch_all(
            """
            select id, tenant_id, agent_id, skill_key, name, content, enabled, deleted, created_at, updated_at
            from agent_skill
            where tenant_id = %s and agent_id = %s and deleted = false
            order by created_at desc
            """,
            (self._to_int(tenant_id), self._to_int(agent_id)),
        )
        return [self._skill_response(row) for row in rows]

    async def save_skill(self, tenant_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        agent_id = self._to_int(payload.get("agentId") or payload.get("agent_id"))
        await self._find_agent(tenant_id, str(agent_id))
        name = self._trim(payload.get("name"))
        if not name:
            raise ValueError("Skill名称不能为空")
        now = datetime.now()
        item_id = self._to_int(payload.get("id")) or id_generator.next_id()
        existing = await self._find_skill(tenant_id, str(item_id), False)
        skill_key = self._resolve_skill_key(item_id, existing, payload, name)
        await database_client.execute(
            """
            insert into agent_skill (
                id, tenant_id, agent_id, skill_key, name, content, enabled, deleted, created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s, %s, false, %s, %s)
            on conflict (id) do update set
                skill_key = excluded.skill_key,
                name = excluded.name,
                content = excluded.content,
                enabled = excluded.enabled,
                deleted = false,
                updated_at = excluded.updated_at
            """,
            (
                item_id,
                self._to_int(tenant_id),
                agent_id,
                skill_key,
                name,
                self._trim(payload.get("content")),
                self._bool(payload.get("enabled"), True),
                existing.get("created_at") if existing else now,
                now,
            ),
        )
        return self._skill_response(await self._find_skill(tenant_id, str(item_id), True))

    async def delete_skill(self, tenant_id: str, item_id: str | None) -> bool:
        await self._find_skill(tenant_id, item_id, True)
        await database_client.execute(
            "update agent_skill set deleted = true, updated_at = %s where tenant_id = %s and id = %s",
            (datetime.now(), self._to_int(tenant_id), self._to_int(item_id)),
        )
        return True

    async def token_today(self, tenant_id: str, user_id: str) -> dict[str, Any]:
        today = date.today()
        usage = await database_client.fetch_one(
            """
            select input_token_count, output_token_count, total_token_count, estimated_token_count,
                   reserved_token_count, request_count, success_count, failed_count
            from agent_token_usage
            where tenant_id = %s and user_id = %s and usage_date = %s
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(user_id), today),
        )
        quota = await self._enabled_quota(tenant_id, user_id)
        daily_limit = self._daily_limit(quota)
        total = self._int(usage.get("total_token_count") if usage else 0)
        reserved = self._int(usage.get("reserved_token_count") if usage else 0)
        return {
            "usageDate": today.isoformat(),
            "dailyTokenLimit": daily_limit,
            "quotaSource": "USER" if quota else "DEFAULT",
            "inputTokenCount": self._int(usage.get("input_token_count") if usage else 0),
            "outputTokenCount": self._int(usage.get("output_token_count") if usage else 0),
            "totalTokenCount": total,
            "estimatedTokenCount": self._int(usage.get("estimated_token_count") if usage else 0),
            "reservedTokenCount": reserved,
            "remainingTokenCount": max(daily_limit - total - reserved, 0) if daily_limit > 0 else 0,
            "requestCount": self._int(usage.get("request_count") if usage else 0),
            "successCount": self._int(usage.get("success_count") if usage else 0),
            "failedCount": self._int(usage.get("failed_count") if usage else 0),
        }

    async def token_quota_overview(self, tenant_id: str) -> dict[str, Any]:
        users = await self._users(tenant_id)
        departments = await self._departments(tenant_id)
        quotas = await database_client.fetch_all(
            """
            select id, user_id, daily_token_limit, assign_scope, assign_target_id, assign_target_name,
                   remark, enabled, updated_at
            from agent_token_quota_user
            where tenant_id = %s and deleted = false
            order by updated_at desc
            """,
            (self._to_int(tenant_id),),
        )
        user_map = {item["id"]: item for item in users}
        return {
            "defaultDailyTokenLimit": max(settings.token_daily_limit, 0),
            "departments": departments,
            "users": users,
            "quotas": [self._quota_response(row, user_map) for row in quotas],
        }

    async def assign_token_quota(self, tenant_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        daily_limit = payload.get("dailyTokenLimit") or payload.get("daily_token_limit")
        if daily_limit is None or int(daily_limit) < 0:
            raise ValueError("Token额度不能小于0")
        users = await self._users(tenant_id)
        departments = await self._departments(tenant_id)
        targets = self._resolve_quota_targets(payload, users)
        if not targets:
            raise ValueError("没有匹配到需要设置额度的用户")
        scope = self._resolve_scope(payload.get("scope"))
        target_id = self._to_int(payload.get("departmentId") or payload.get("department_id")) if scope == "DEPARTMENT" else None
        target_name = self._resolve_target_name(scope, payload, users, departments)
        now = datetime.now()
        values = []
        for user in targets:
            values.append((
                """
                insert into agent_token_quota_user (
                    id, tenant_id, user_id, daily_token_limit, assign_scope, assign_target_id,
                    assign_target_name, remark, enabled, deleted, created_at, updated_at
                ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, false, %s, %s)
                on conflict (tenant_id, user_id) do update set
                    daily_token_limit = excluded.daily_token_limit,
                    assign_scope = excluded.assign_scope,
                    assign_target_id = excluded.assign_target_id,
                    assign_target_name = excluded.assign_target_name,
                    remark = excluded.remark,
                    enabled = excluded.enabled,
                    deleted = false,
                    updated_at = excluded.updated_at
                """,
                (
                    id_generator.next_id(),
                    self._to_int(tenant_id),
                    self._to_int(user.get("id")),
                    int(daily_limit),
                    scope,
                    target_id,
                    target_name,
                    self._trim(payload.get("remark")),
                    self._bool(payload.get("enabled"), True),
                    now,
                    now,
                ),
            ))
        await database_client.execute_many(values)
        return await self.token_quota_overview(tenant_id)

    async def clear_token_quota(self, tenant_id: str, user_id: str | None) -> dict[str, Any]:
        if not user_id:
            raise ValueError("用户不能为空")
        await database_client.execute(
            """
            update agent_token_quota_user
            set deleted = true, updated_at = %s
            where tenant_id = %s and user_id = %s and deleted = false
            """,
            (datetime.now(), self._to_int(tenant_id), self._to_int(user_id)),
        )
        return await self.token_quota_overview(tenant_id)

    async def _load_model_config(self, tenant_id: str, model_config_id: Any) -> dict[str, Any] | None:
        value = self._to_int(model_config_id)
        if not value:
            return None
        row = await database_client.fetch_one(
            """
            select id, provider, model_name, base_url, api_key_env as api_key, enabled
            from llm_model_config
            where tenant_id = %s and id = %s and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), value),
        )
        if row is None:
            raise ValueError("模型配置不存在")
        if not bool(row.get("enabled")):
            raise ValueError("模型配置已停用")
        return row

    async def _find_agent(self, tenant_id: str, agent_id: str | None) -> dict[str, Any]:
        self._require_database()
        if not agent_id:
            raise ValueError("Agent编号不能为空")
        row = await database_client.fetch_one(
            """
            select a.id, a.tenant_id, a.code, a.scene_code, a.scene_name, a.name,
                   a.description, a.system_prompt, a.model_config_id,
                   case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                   case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                   case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                   case when a.model_config_id is null then a.api_key_env else m.api_key_env end as api_key,
                   a.max_iters, a.extra_config_json, a.remark, a.enabled, a.frontend_visible,
                   a.deleted,
                   a.created_at, a.updated_at
            from agents a
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.id = %s and a.deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(agent_id)),
        )
        if row is None:
            raise ValueError("Agent不存在")
        return row

    async def _find_scene(
            self,
            tenant_id: str,
            scene_id: str | None,
            required: bool) -> dict[str, Any] | None:
        if not scene_id:
            if required:
                raise ValueError("场景编号不能为空")
            return None
        row = await database_client.fetch_one(
            """
            select s.id, s.tenant_id, s.code, s.name, s.description, s.sort_no, s.enabled,
                   s.deleted, s.created_at, s.updated_at,
                   (
                       select count(1) from agents a
                       where a.tenant_id = s.tenant_id and a.scene_code = s.code and a.deleted = false
                   ) as agent_count
            from agent_scene s
            where s.tenant_id = %s and s.id = %s and s.deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(scene_id)),
        )
        if row is None and required:
            raise ValueError("场景不存在")
        return row

    async def _find_scene_by_code(self, tenant_id: str, scene_code: str) -> dict[str, Any] | None:
        row = await database_client.fetch_one(
            """
            select id, tenant_id, code, name, description, sort_no, enabled,
                   deleted, created_at, updated_at
            from agent_scene
            where tenant_id = %s and code = %s and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), scene_code),
        )
        if row is not None:
            return row
        return await database_client.fetch_one(
            """
            select null as id, tenant_id, scene_code as code,
                   coalesce(max(scene_name), scene_code) as name,
                   null as description, 0 as sort_no, true as enabled, false as deleted,
                   min(created_at) as created_at, max(updated_at) as updated_at
            from agents
            where tenant_id = %s and scene_code = %s and deleted = false
            group by tenant_id, scene_code
            limit 1
            """,
            (self._to_int(tenant_id), scene_code),
        )

    async def _find_mcp(self, tenant_id: str, item_id: str | None, required: bool) -> dict[str, Any] | None:
        if not item_id:
            if required:
                raise ValueError("MCP编号不能为空")
            return None
        row = await database_client.fetch_one(
            """
            select id, tenant_id, agent_id, name, transport_type, endpoint, command,
                   arguments_json, headers_json, enabled, deleted, created_at, updated_at
            from agent_mcp
            where tenant_id = %s and id = %s and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(item_id)),
        )
        if row is None and required:
            raise ValueError("MCP配置不存在")
        return row

    async def _find_skill(self, tenant_id: str, item_id: str | None, required: bool) -> dict[str, Any] | None:
        if not item_id:
            if required:
                raise ValueError("Skill编号不能为空")
            return None
        row = await database_client.fetch_one(
            """
            select id, tenant_id, agent_id, skill_key, name, content, enabled, deleted, created_at, updated_at
            from agent_skill
            where tenant_id = %s and id = %s and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(item_id)),
        )
        if row is None and required:
            raise ValueError("Skill配置不存在")
        return row

    async def _enabled_quota(self, tenant_id: str, user_id: str) -> dict[str, Any] | None:
        return await database_client.fetch_one(
            """
            select daily_token_limit
            from agent_token_quota_user
            where tenant_id = %s and user_id = %s and enabled = true and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(user_id)),
        )

    async def _users(self, tenant_id: str) -> list[dict[str, Any]]:
        rows = await database_client.fetch_all(
            """
            select u.id, u.username, u.display_name, u.department_id, d.name as department_name, u.enabled
            from sys_user u
            left join sys_department d on d.id = u.department_id
              and d.tenant_id = u.tenant_id and d.deleted = false
            where u.tenant_id = %s and u.deleted = false
            order by u.created_at desc
            """,
            (self._to_int(tenant_id),),
        )
        return [self._user_response(row) for row in rows]

    async def _departments(self, tenant_id: str) -> list[dict[str, Any]]:
        rows = await database_client.fetch_all(
            """
            select id, parent_id, name, enabled
            from sys_department
            where tenant_id = %s and deleted = false
            order by sort_no asc, created_at asc
            """,
            (self._to_int(tenant_id),),
        )
        return [self._department_response(row) for row in rows]

    def _agent_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "tenantId": self._id(row.get("tenant_id")),
            "code": row.get("code"),
            "sceneCode": row.get("scene_code"),
            "sceneName": row.get("scene_name"),
            "name": row.get("name"),
            "description": row.get("description"),
            "systemPrompt": row.get("system_prompt"),
            "modelConfigId": self._id(row.get("model_config_id")),
            "modelProvider": row.get("model_provider"),
            "modelName": row.get("model_name"),
            "baseUrl": row.get("base_url"),
            "maxIters": row.get("max_iters"),
            "extraConfigJson": row.get("extra_config_json"),
            "remark": row.get("remark"),
            "enabled": bool(row.get("enabled")),
            "frontendVisible": bool(row.get("frontend_visible")),
            "deleted": bool(row.get("deleted")),
            "createdAt": self._datetime(row.get("created_at")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _scene_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "tenantId": self._id(row.get("tenant_id")),
            "code": row.get("code"),
            "name": row.get("name"),
            "description": row.get("description"),
            "sortNo": self._int(row.get("sort_no")),
            "agentCount": self._int(row.get("agent_count")),
            "enabled": bool(row.get("enabled")),
            "managed": row.get("id") is not None,
            "createdAt": self._datetime(row.get("created_at")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _mcp_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "tenantId": self._id(row.get("tenant_id")),
            "agentId": self._id(row.get("agent_id")),
            "name": row.get("name"),
            "transportType": row.get("transport_type"),
            "endpoint": row.get("endpoint"),
            "command": row.get("command"),
            "argumentsJson": row.get("arguments_json"),
            "headersJson": row.get("headers_json"),
            "enabled": bool(row.get("enabled")),
            "deleted": bool(row.get("deleted")),
            "createdAt": self._datetime(row.get("created_at")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _skill_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "tenantId": self._id(row.get("tenant_id")),
            "agentId": self._id(row.get("agent_id")),
            "skillKey": row.get("skill_key"),
            "name": row.get("name"),
            "content": row.get("content"),
            "enabled": bool(row.get("enabled")),
            "deleted": bool(row.get("deleted")),
            "createdAt": self._datetime(row.get("created_at")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _user_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "username": row.get("username"),
            "displayName": row.get("display_name"),
            "departmentId": self._id(row.get("department_id")),
            "departmentName": row.get("department_name"),
            "enabled": bool(row.get("enabled")),
        }

    def _department_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "parentId": self._id(row.get("parent_id")),
            "name": row.get("name"),
            "enabled": bool(row.get("enabled")),
        }

    def _quota_response(self, row: dict[str, Any], user_map: dict[str, dict[str, Any]]) -> dict[str, Any]:
        user_id = self._id(row.get("user_id"))
        user = user_map.get(user_id) or {}
        return {
            "id": self._id(row.get("id")),
            "userId": user_id,
            "username": user.get("username"),
            "displayName": user.get("displayName"),
            "departmentId": user.get("departmentId"),
            "departmentName": user.get("departmentName"),
            "dailyTokenLimit": self._int(row.get("daily_token_limit")),
            "assignScope": row.get("assign_scope"),
            "assignTargetId": self._id(row.get("assign_target_id")),
            "assignTargetName": row.get("assign_target_name"),
            "remark": row.get("remark"),
            "enabled": bool(row.get("enabled")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _resolve_quota_targets(self, payload: dict[str, Any], users: list[dict[str, Any]]) -> list[dict[str, Any]]:
        scope = self._resolve_scope(payload.get("scope"))
        if scope == "COMPANY":
            return list(users)
        if scope == "DEPARTMENT":
            department_id = self._id(payload.get("departmentId") or payload.get("department_id"))
            if not department_id:
                raise ValueError("部门不能为空")
            return [item for item in users if item.get("departmentId") == department_id]
        user_ids = set()
        for value in payload.get("userIds") or payload.get("user_ids") or []:
            item_id = self._id(value)
            if item_id:
                user_ids.add(item_id)
        if not user_ids:
            raise ValueError("用户不能为空")
        return [item for item in users if item.get("id") in user_ids]

    def _resolve_target_name(
            self,
            scope: str,
            payload: dict[str, Any],
            users: list[dict[str, Any]],
            departments: list[dict[str, Any]]) -> str:
        if scope == "COMPANY":
            return "全公司"
        if scope == "DEPARTMENT":
            department_id = self._id(payload.get("departmentId") or payload.get("department_id"))
            for department in departments:
                if department.get("id") == department_id:
                    return department.get("name") or "指定部门"
            return "指定部门"
        user_ids = payload.get("userIds") or payload.get("user_ids") or []
        if len(user_ids) == 1:
            user_id = self._id(user_ids[0])
            for user in users:
                if user.get("id") == user_id:
                    return user.get("displayName") or user.get("username") or "指定用户"
        return "指定用户"

    def _resolve_scope(self, value: Any) -> str:
        scope = str(value or "USER").strip().upper()
        if scope == "ALL":
            return "COMPANY"
        if scope not in {"USER", "DEPARTMENT", "COMPANY"}:
            raise ValueError("额度范围不正确")
        return scope

    def _resolve_agent_code(
            self,
            agent_id: int,
            existing: dict[str, Any] | None,
            payload: dict[str, Any],
            scene_code: str | None) -> str:
        code = self._trim(payload.get("code"))
        if code:
            return code
        if existing and self._trim(existing.get("code")):
            return self._trim(existing.get("code"))
        scene_token = self._normalize_code_token(scene_code)
        if scene_token:
            return "agent-" + scene_token + "-" + str(agent_id)
        return "agent-" + str(agent_id)

    def _normalize_extra_config(self, value: Any) -> str | None:
        text = self._trim(value)
        if not text:
            return None
        try:
            parsed = json.loads(text)
        except (TypeError, ValueError, json.JSONDecodeError) as ex:
            raise ValueError("附加JSON格式不正确") from ex
        if not isinstance(parsed, dict):
            raise ValueError("附加JSON必须是对象")
        return json.dumps(parsed, ensure_ascii=False, separators=(",", ":"))

    def _resolve_skill_key(
            self,
            item_id: int,
            existing: dict[str, Any] | None,
            payload: dict[str, Any],
            name: str) -> str:
        skill_key = self._normalize_code_token(payload.get("skillKey") or payload.get("skill_key"))
        if skill_key:
            return skill_key
        if existing and self._trim(existing.get("skill_key")):
            return self._trim(existing.get("skill_key"))
        name_key = self._normalize_code_token(name)
        if name_key:
            return "skill-" + name_key
        return "skill-" + str(item_id)

    def _normalize_code_token(self, value: Any) -> str | None:
        text = self._trim(value)
        if not text:
            return None
        text = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
        return text or None

    def _resolve_max_iters(self, value: Any) -> int:
        try:
            number = int(value or 8)
        except (TypeError, ValueError):
            number = 8
        return min(max(number, 1), 50)

    def _daily_limit(self, quota: dict[str, Any] | None) -> int:
        if quota and quota.get("daily_token_limit") is not None:
            return max(self._int(quota.get("daily_token_limit")), 0)
        return max(settings.token_daily_limit, 0)

    def _require_database(self) -> None:
        if not database_client.enabled():
            raise ValueError("Python AI Runtime未开启数据库连接")

    def _trim(self, value: Any) -> str | None:
        if value is None:
            return None
        text = str(value).strip()
        return text or None

    def _id(self, value: Any) -> str | None:
        if value is None:
            return None
        try:
            return str(int(value))
        except (TypeError, ValueError):
            return None

    def _to_int(self, value: Any) -> int | None:
        value_id = self._id(value)
        return int(value_id) if value_id else None

    def _int(self, value: Any) -> int:
        try:
            return int(value or 0)
        except (TypeError, ValueError):
            return 0

    def _bool(self, value: Any, default: bool) -> bool:
        if value is None:
            return default
        if isinstance(value, bool):
            return value
        return str(value).strip().lower() in {"true", "1", "yes", "y"}

    def _datetime(self, value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, datetime):
            return value.isoformat()
        return str(value)


agent_management_repository = AgentManagementRepository()
