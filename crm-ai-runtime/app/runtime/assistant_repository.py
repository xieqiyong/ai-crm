import json
from datetime import datetime
from typing import Any

from app.platform.database import database_client


class AssistantRepository:
    async def agents(self, tenant_id: str, user_id: str) -> list[dict[str, Any]]:
        rows = await database_client.fetch_all(
            """
            select
                a.id, a.code, a.scene_code, a.scene_name, a.name, a.description,
                a.model_provider, a.model_name, a.max_iters, a.enabled,
                (
                    select count(1)
                    from conversation c
                    where c.tenant_id = a.tenant_id
                      and c.user_id = %s
                      and c.agent_id = a.id
                      and c.deleted = false
                ) as conversation_count,
                (
                    select max(c.last_message_at)
                    from conversation c
                    where c.tenant_id = a.tenant_id
                      and c.user_id = %s
                      and c.agent_id = a.id
                      and c.deleted = false
                ) as last_message_at
            from agents a
            where a.tenant_id = %s and a.enabled = true and a.deleted = false
            order by a.updated_at desc
            """,
            (self._to_int(user_id), self._to_int(user_id), self._to_int(tenant_id)),
        )
        return [self._agent_response(row) for row in rows]

    async def agent_runtime_payload(self, tenant_id: str, agent_id: str) -> dict[str, Any]:
        row = await database_client.fetch_one(
            """
            select id, code, scene_code, scene_name, name, description, system_prompt,
                   model_provider, model_name, base_url, api_key_env as api_key,
                   max_iters, extra_config_json
            from agents
            where tenant_id = %s and id = %s and enabled = true and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(agent_id)),
        )
        if row is None:
            raise ValueError("智能体不存在或已停用")
        return {
            "id": self._id_text(row.get("id")),
            "code": row.get("code"),
            "sceneCode": row.get("scene_code"),
            "sceneName": row.get("scene_name"),
            "name": row.get("name"),
            "description": row.get("description"),
            "systemPrompt": row.get("system_prompt"),
            "modelProvider": row.get("model_provider"),
            "modelName": row.get("model_name"),
            "baseUrl": row.get("base_url"),
            "apiKey": row.get("api_key"),
            "maxIters": row.get("max_iters"),
            "extraConfigJson": row.get("extra_config_json"),
        }

    async def conversations(self, tenant_id: str, user_id: str, agent_id: str | None) -> list[dict[str, Any]]:
        if agent_id:
            rows = await database_client.fetch_all(
                """
                select id, agent_id, title, scene_code, status, last_message_at, created_at
                from conversation
                where tenant_id = %s and user_id = %s and agent_id = %s and deleted = false
                order by last_message_at desc
                limit 50
                """,
                (self._to_int(tenant_id), self._to_int(user_id), self._to_int(agent_id)),
            )
        else:
            rows = await database_client.fetch_all(
                """
                select id, agent_id, title, scene_code, status, last_message_at, created_at
                from conversation
                where tenant_id = %s and user_id = %s and deleted = false
                order by last_message_at desc
                limit 50
                """,
                (self._to_int(tenant_id), self._to_int(user_id)),
            )
        return [self._conversation_response(row) for row in rows]

    async def messages(self, tenant_id: str, user_id: str, conversation_id: str) -> list[dict[str, Any]]:
        await self._require_conversation(tenant_id, user_id, conversation_id)
        rows = await database_client.fetch_all(
            """
            select id, status, input_text, input_json, output_text, error_message, created_at, updated_at, finished_at
            from agent_run
            where tenant_id = %s and user_id = %s and conversation_id = %s and deleted = false
            order by created_at asc
            """,
            (self._to_int(tenant_id), self._to_int(user_id), self._to_int(conversation_id)),
        )
        result: list[dict[str, Any]] = []
        for row in rows:
            result.append(self._user_message(row))
            if row.get("output_text") or row.get("status") in {"FAILED", "STOPPED"}:
                result.append(self._assistant_message(row))
        return result

    async def delete_conversation(self, tenant_id: str, user_id: str, conversation_id: str) -> bool:
        await self._require_conversation(tenant_id, user_id, conversation_id)
        await database_client.execute(
            """
            update conversation
            set deleted = true, updated_at = %s
            where tenant_id = %s and user_id = %s and id = %s and deleted = false
            """,
            (datetime.now(), self._to_int(tenant_id), self._to_int(user_id), self._to_int(conversation_id)),
        )
        return True

    async def _require_conversation(self, tenant_id: str, user_id: str, conversation_id: str) -> None:
        row = await database_client.fetch_one(
            """
            select id
            from conversation
            where tenant_id = %s and user_id = %s and id = %s and deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(user_id), self._to_int(conversation_id)),
        )
        if row is None:
            raise ValueError("会话不存在或无权访问")

    def _agent_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "code": row.get("code"),
            "sceneCode": row.get("scene_code"),
            "sceneName": row.get("scene_name"),
            "name": row.get("name"),
            "description": row.get("description"),
            "modelProvider": row.get("model_provider"),
            "modelName": row.get("model_name"),
            "maxIters": row.get("max_iters"),
            "enabled": bool(row.get("enabled")),
            "conversationCount": self._id(row.get("conversation_count")) or 0,
            "lastMessageAt": self._datetime(row.get("last_message_at")),
        }

    def _conversation_response(self, row: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": self._id(row.get("id")),
            "agentId": self._id(row.get("agent_id")),
            "title": row.get("title"),
            "sceneCode": row.get("scene_code"),
            "status": row.get("status"),
            "lastMessageAt": self._datetime(row.get("last_message_at")),
            "createdAt": self._datetime(row.get("created_at")),
        }

    def _user_message(self, row: dict[str, Any]) -> dict[str, Any]:
        context = self._parse_context(row.get("input_json"))
        content = context.get("userMessage") or row.get("input_text") or ""
        attachments = context.get("attachments") or []
        return {
            "runId": self._id(row.get("id")),
            "role": "user",
            "content": content,
            "status": row.get("status"),
            "createdAt": self._datetime(row.get("created_at")),
            "attachments": attachments if isinstance(attachments, list) else [],
        }

    def _assistant_message(self, row: dict[str, Any]) -> dict[str, Any]:
        status = row.get("status")
        content = row.get("output_text") or ""
        if status == "FAILED" and not content:
            content = "运行失败：" + str(row.get("error_message") or "")
        if status == "STOPPED" and not content:
            content = "本次回答已终止。"
        return {
            "runId": self._id(row.get("id")),
            "role": "assistant",
            "content": content,
            "status": status,
            "createdAt": self._datetime(row.get("finished_at") or row.get("updated_at")),
            "attachments": [],
        }

    def _parse_context(self, value: str | None) -> dict[str, Any]:
        if not value:
            return {}
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}

    def _datetime(self, value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, datetime):
            return value.isoformat()
        return str(value)

    def _id(self, value: Any) -> int | None:
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    def _id_text(self, value: Any) -> str | None:
        if value is None:
            return None
        return str(value)

    def _to_int(self, value: Any) -> int | None:
        return self._id(value)


assistant_repository = AssistantRepository()
