import json
from datetime import datetime
from typing import Any

from app.platform.database import database_client
from app.reports.service import reports_from_metadata


class AssistantRepository:
    async def agents(self, tenant_id: str, user_id: str) -> list[dict[str, Any]]:
        rows = await database_client.fetch_all(
            """
            select
                a.id, a.code, a.scene_code, a.scene_name, a.name, a.description,
                case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                a.max_iters, a.enabled,
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
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.enabled = true and a.frontend_visible = true
              and a.deleted = false
            order by a.updated_at desc
            """,
            (self._to_int(user_id), self._to_int(user_id), self._to_int(tenant_id)),
        )
        return [self._agent_response(row) for row in rows]

    async def agent_runtime_payload(self, tenant_id: str, agent_id: str) -> dict[str, Any]:
        row = await database_client.fetch_one(
            """
            select a.id, a.code, a.scene_code, a.scene_name, a.name, a.description,
                   a.system_prompt, a.model_config_id,
                   case when a.model_config_id is null then a.model_provider else m.provider end as model_provider,
                   case when a.model_config_id is null then a.model_name else m.model_name end as model_name,
                   case when a.model_config_id is null then a.base_url else m.base_url end as base_url,
                   case when a.model_config_id is null then a.api_key_env else m.api_key_env end as api_key,
                   a.max_iters, a.extra_config_json,
                   m.id as resolved_model_config_id, m.enabled as model_config_enabled
            from agents a
            left join llm_model_config m
              on m.tenant_id = a.tenant_id and m.id = a.model_config_id and m.deleted = false
            where a.tenant_id = %s and a.id = %s and a.enabled = true
              and a.frontend_visible = true and a.deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(agent_id)),
        )
        if row is None:
            raise ValueError("智能体不存在或已停用")
        if row.get("model_config_id") is not None:
            if row.get("resolved_model_config_id") is None:
                raise ValueError("智能体关联的大模型配置不存在")
            if not bool(row.get("model_config_enabled")):
                raise ValueError("智能体关联的大模型配置已停用")
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
                select c.id, c.agent_id, c.title, c.scene_code, c.status,
                       c.last_message_at, c.created_at
                from conversation c
                inner join agents a
                  on a.tenant_id = c.tenant_id and a.id = c.agent_id
                 and a.enabled = true and a.frontend_visible = true and a.deleted = false
                where c.tenant_id = %s and c.user_id = %s and c.agent_id = %s
                  and c.deleted = false
                order by c.last_message_at desc
                limit 50
                """,
                (self._to_int(tenant_id), self._to_int(user_id), self._to_int(agent_id)),
            )
        else:
            rows = await database_client.fetch_all(
                """
                select c.id, c.agent_id, c.title, c.scene_code, c.status,
                       c.last_message_at, c.created_at
                from conversation c
                inner join agents a
                  on a.tenant_id = c.tenant_id and a.id = c.agent_id
                 and a.enabled = true and a.frontend_visible = true and a.deleted = false
                where c.tenant_id = %s and c.user_id = %s and c.deleted = false
                order by c.last_message_at desc
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
        report_rows = await database_client.fetch_all(
            """
            select e.run_id, e.metadata_json
            from agent_events e
            inner join agent_run r
              on r.tenant_id = e.tenant_id and r.id = e.run_id and r.deleted = false
            where e.tenant_id = %s and r.user_id = %s and e.conversation_id = %s
              and e.event_type = 'REPORT_READY'
            order by e.sequence_no asc
            """,
            (self._to_int(tenant_id), self._to_int(user_id), self._to_int(conversation_id)),
        )
        reports_by_run: dict[str, list[dict[str, Any]]] = {}
        for report_row in report_rows:
            run_id = str(report_row.get("run_id") or "")
            reports_by_run.setdefault(run_id, []).extend(reports_from_metadata(report_row.get("metadata_json")))
        result: list[dict[str, Any]] = []
        for row in rows:
            result.append(self._user_message(row))
            if row.get("output_text") or row.get("status") in {"FAILED", "STOPPED"}:
                result.append(self._assistant_message(
                    row,
                    reports_by_run.get(str(row.get("id") or ""), []),
                ))
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
            select c.id
            from conversation c
            inner join agents a
              on a.tenant_id = c.tenant_id and a.id = c.agent_id
             and a.enabled = true and a.frontend_visible = true and a.deleted = false
            where c.tenant_id = %s and c.user_id = %s and c.id = %s and c.deleted = false
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

    def _assistant_message(
            self,
            row: dict[str, Any],
            reports: list[dict[str, Any]] | None = None) -> dict[str, Any]:
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
            "attachments": [self._report_attachment(item) for item in reports or []],
        }

    def _report_attachment(self, report: dict[str, Any]) -> dict[str, Any]:
        return {
            "artifactId": report.get("artifactId"),
            "fileName": report.get("fileName"),
            "contentType": report.get("contentType"),
            "format": report.get("format"),
            "size": report.get("size"),
            "downloadEndpoint": report.get("downloadEndpoint") or "/api/agent-assistant/report/download",
            "createdAt": report.get("createdAt"),
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
