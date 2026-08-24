import json
from datetime import date, datetime
from typing import Any
from uuid import uuid4

from app.agents.models import AgentDefinition
from app.core.config import settings
from app.platform.database import database_client
from app.platform.id_generator import id_generator
from app.schemas.runtime import RuntimeRunRequest


class RuntimeStore:
    async def start_run(self, request: RuntimeRunRequest, agent: AgentDefinition) -> None:
        if not database_client.enabled():
            return
        now = datetime.now()
        agent_id = self._to_int(agent.id)
        if not agent_id:
            return
        await self._ensure_token_quota(request, agent)
        if not request.conversation_id:
            conversation_id = id_generator.next_id()
            request.conversation_id = str(conversation_id)
            await database_client.execute(
                """
                insert into conversation (
                    id, tenant_id, user_id, agent_id, session_id, scene_code, business_type, business_id,
                    title, status, context_json, last_message_at, deleted, created_at, updated_at
                ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, false, %s, %s)
                """,
                (
                    conversation_id,
                    self._to_int(request.tenant_id),
                    self._to_int(request.user_id),
                    agent_id,
                    self._session_id(request),
                    request.scene_code,
                    request.business_type,
                    request.business_id,
                    self._title(request),
                    "ACTIVE",
                    self._json(request.context),
                    now,
                    now,
                    now,
                ),
            )
        else:
            await database_client.execute(
                """
                update conversation
                set last_message_at = %s, updated_at = %s
                where id = %s and tenant_id = %s and user_id = %s and deleted = false
                """,
                (now, now, self._to_int(request.conversation_id), self._to_int(request.tenant_id), self._to_int(request.user_id)),
            )
        if not request.run_id:
            request.run_id = str(id_generator.next_id())
        await database_client.execute(
            """
            insert into agent_run (
                id, tenant_id, user_id, agent_id, conversation_id, session_id, scene_code, business_type,
                business_id, status, input_text, input_json, started_at, deleted, created_at, updated_at,
                usage_estimated
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'RUNNING', %s, %s, %s, false, %s, %s, true)
            """,
            (
                self._to_int(request.run_id),
                self._to_int(request.tenant_id),
                self._to_int(request.user_id),
                agent_id,
                self._to_int(request.conversation_id),
                self._session_id(request),
                request.scene_code,
                request.business_type,
                request.business_id,
                request.message,
                self._json(request.context),
                now,
                now,
                now,
            ),
        )

    async def finish_run(self, request: RuntimeRunRequest, output: str, events: list[dict[str, Any]], usage: dict[str, int]) -> None:
        if not database_client.enabled() or not request.run_id:
            return
        now = datetime.now()
        started_at = await self._run_started_at(request)
        elapsed_ms = int((now - started_at).total_seconds() * 1000) if started_at else None
        input_tokens = self._int_value(usage.get("inputTokens"))
        output_tokens = self._int_value(usage.get("outputTokens"))
        total_tokens = self._int_value(usage.get("totalTokens")) or input_tokens + output_tokens
        await database_client.execute_many([
            (
                """
                update agent_run
                set status = 'SUCCESS',
                    output_text = %s,
                    finished_at = %s,
                    elapsed_ms = %s,
                    input_token_count = %s,
                    output_token_count = %s,
                    total_token_count = %s,
                    estimated_token_count = %s,
                    usage_estimated = %s,
                    updated_at = %s
                where id = %s and tenant_id = %s and deleted = false
                """,
                (
                    output,
                    now,
                    elapsed_ms,
                    input_tokens,
                    output_tokens,
                    total_tokens,
                    total_tokens,
                    total_tokens <= 0,
                    now,
                    self._to_int(request.run_id),
                    self._to_int(request.tenant_id),
                ),
            ),
            (
                """
                update conversation
                set last_message_at = %s, updated_at = %s
                where id = %s and tenant_id = %s and deleted = false
                """,
                (now, now, self._to_int(request.conversation_id), self._to_int(request.tenant_id)),
            ),
        ])
        await self.save_events(request, events)
        await self.add_token_usage(request, input_tokens, output_tokens, total_tokens, True)

    async def fail_run(self, request: RuntimeRunRequest, error: str) -> None:
        if not database_client.enabled() or not request.run_id:
            return
        now = datetime.now()
        started_at = await self._run_started_at(request)
        elapsed_ms = int((now - started_at).total_seconds() * 1000) if started_at else None
        await database_client.execute(
            """
            update agent_run
            set status = 'FAILED', error_message = %s, finished_at = %s, elapsed_ms = %s, updated_at = %s
            where id = %s and tenant_id = %s and deleted = false
            """,
            (self._shrink(error, 1000), now, elapsed_ms, now, self._to_int(request.run_id), self._to_int(request.tenant_id)),
        )
        await self.add_token_usage(request, 0, 0, 0, False)

    async def cancel_run(self, request: RuntimeRunRequest) -> None:
        if not database_client.enabled() or not request.run_id:
            return
        now = datetime.now()
        started_at = await self._run_started_at(request)
        elapsed_ms = int((now - started_at).total_seconds() * 1000) if started_at else None
        await database_client.execute(
            """
            update agent_run
            set status = 'STOPPED', finished_at = %s, elapsed_ms = %s, updated_at = %s
            where id = %s and tenant_id = %s and deleted = false
            """,
            (
                now,
                elapsed_ms,
                now,
                self._to_int(request.run_id),
                self._to_int(request.tenant_id),
            ),
        )

    async def stop_run(self, tenant_id: str, user_id: str, request_id: str) -> bool:
        return False

    async def save_events(self, request: RuntimeRunRequest, events: list[dict[str, Any]]) -> None:
        if not database_client.enabled() or not request.run_id or not request.conversation_id:
            return
        agent_id = self._to_int(request.agent.id if request.agent else None)
        if not agent_id:
            return
        values = []
        sequence = 1
        for event in events or []:
            values.append((
                """
                insert into agent_events (
                    id, tenant_id, run_id, conversation_id, agent_id, sequence_no, event_id, event_type,
                    content, tool_name, metadata_json, created_at
                ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    id_generator.next_id(),
                    self._to_int(request.tenant_id),
                    self._to_int(request.run_id),
                    self._to_int(request.conversation_id),
                    agent_id,
                    sequence,
                    str(event.get("id") or uuid4()),
                    self._shrink(str(event.get("type") or "EVENT"), 64),
                    event.get("content"),
                    event.get("toolName") or event.get("tool_name"),
                    self._json(event.get("metadata") or {}),
                    datetime.now(),
                ),
            ))
            sequence += 1
        await database_client.execute_many(values)

    async def add_token_usage(
            self,
            request: RuntimeRunRequest,
            input_tokens: int,
            output_tokens: int,
            total_tokens: int,
            success: bool) -> None:
        if not database_client.enabled():
            return
        now = datetime.now()
        estimated = total_tokens if total_tokens > 0 else self._estimate_tokens(request.message)
        await database_client.execute(
            """
            insert into agent_token_usage (
                id, tenant_id, user_id, usage_date, input_token_count, output_token_count, total_token_count,
                estimated_token_count, reserved_token_count, request_count, success_count, failed_count,
                created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, 0, 1, %s, %s, %s, %s)
            on conflict (tenant_id, user_id, usage_date) do update set
                input_token_count = agent_token_usage.input_token_count + excluded.input_token_count,
                output_token_count = agent_token_usage.output_token_count + excluded.output_token_count,
                total_token_count = agent_token_usage.total_token_count + excluded.total_token_count,
                estimated_token_count = agent_token_usage.estimated_token_count + excluded.estimated_token_count,
                request_count = agent_token_usage.request_count + 1,
                success_count = agent_token_usage.success_count + excluded.success_count,
                failed_count = agent_token_usage.failed_count + excluded.failed_count,
                updated_at = excluded.updated_at
            """,
            (
                id_generator.next_id(),
                self._to_int(request.tenant_id),
                self._to_int(request.user_id),
                date.today(),
                input_tokens,
                output_tokens,
                total_tokens,
                estimated,
                1 if success else 0,
                0 if success else 1,
                now,
                now,
            ),
        )

    async def _run_started_at(self, request: RuntimeRunRequest) -> datetime | None:
        row = await database_client.fetch_one(
            "select started_at from agent_run where id = %s and tenant_id = %s limit 1",
            (self._to_int(request.run_id), self._to_int(request.tenant_id)),
        )
        return row.get("started_at") if row else None

    async def _ensure_token_quota(self, request: RuntimeRunRequest, agent: AgentDefinition) -> None:
        daily_limit = await self._daily_token_limit(request)
        if daily_limit <= 0:
            return
        usage = await database_client.fetch_one(
            """
            select total_token_count, reserved_token_count
            from agent_token_usage
            where tenant_id = %s and user_id = %s and usage_date = %s
            limit 1
            """,
            (self._to_int(request.tenant_id), self._to_int(request.user_id), date.today()),
        )
        total = self._int_value(usage.get("total_token_count") if usage else 0)
        reserved = self._int_value(usage.get("reserved_token_count") if usage else 0)
        estimate = self._estimate_runtime_tokens(request, agent)
        remaining = daily_limit - total - reserved
        if remaining <= 0 or estimate > remaining:
            raise RuntimeError("今日AI Token额度不足，剩余额度：%s，本次预估需要：%s" % (max(remaining, 0), estimate))

    async def _daily_token_limit(self, request: RuntimeRunRequest) -> int:
        quota = await database_client.fetch_one(
            """
            select daily_token_limit
            from agent_token_quota_user
            where tenant_id = %s and user_id = %s and enabled = true and deleted = false
            limit 1
            """,
            (self._to_int(request.tenant_id), self._to_int(request.user_id)),
        )
        if quota and quota.get("daily_token_limit") is not None:
            return max(self._int_value(quota.get("daily_token_limit")), 0)
        return max(int(settings.token_daily_limit or 0), 0)

    def _session_id(self, request: RuntimeRunRequest) -> str:
        if request.session_id:
            return request.session_id[:128]
        if request.conversation_id:
            return ("conversation-" + str(request.conversation_id))[:128]
        return ("runtime-" + str(request.run_id or id_generator.next_id()))[:128]

    def _title(self, request: RuntimeRunRequest) -> str:
        value = request.context.get("conversationTitle") or request.message or "新会话"
        value = str(value).strip().replace("\n", " ")
        if len(value) > 24:
            value = value[:24] + "…"
        return value

    def _json(self, value: Any) -> str:
        return json.dumps(value or {}, ensure_ascii=False, default=str)

    def _to_int(self, value: Any) -> int | None:
        if value is None or value == "":
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    def _int_value(self, value: Any) -> int:
        try:
            return int(value or 0)
        except (TypeError, ValueError):
            return 0

    def _estimate_tokens(self, value: str) -> int:
        text = value or ""
        return max(1, int(len(text) / 2)) if text else 0

    def _estimate_runtime_tokens(self, request: RuntimeRunRequest, agent: AgentDefinition) -> int:
        values = [
            request.message,
            request.injected_prompt,
            request.rendered_system_prompt,
            agent.system_prompt if agent else None,
            self._json(request.context),
        ]
        total = 0
        for value in values:
            total += self._estimate_tokens(value or "")
        return total + max(int(settings.token_reserve_output_tokens or 0), 0)

    def _shrink(self, value: str, max_length: int) -> str:
        text = value or ""
        if len(text) <= max_length:
            return text
        return text[:max_length]


runtime_store = RuntimeStore()
