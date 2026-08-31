import asyncio
import io
import json
import logging
import re
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
from uuid import uuid4

from app.core.config import settings
from app.platform.database import database_client
from app.reports.renderer import report_renderer

logger = logging.getLogger("crm_ai_runtime.report")


class ReportService:
    def __init__(self):
        self._minio_client = None
        self._bucket_ready = False
        self._ephemeral: dict[str, dict[str, Any]] = {}

    async def generate(
            self,
            tenant_id: str,
            user_id: str,
            run_id: str,
            conversation_id: str | None,
            title: str,
            content: str,
            formats: list[str] | None) -> list[dict[str, Any]]:
        if not settings.report_enabled:
            raise RuntimeError("智能体报告生成功能未开启")
        report_title = self._title(title)
        report_content = str(content or "").strip()
        if not report_content:
            raise ValueError("报告内容不能为空")
        max_chars = max(int(settings.report_max_content_chars or 0), 1000)
        if len(report_content) > max_chars:
            raise ValueError("报告内容超过最大长度限制，请精简后重新生成")
        normalized_formats = self._formats(formats)
        generated_at = datetime.now()
        reports = []
        for report_format in normalized_formats:
            artifact_id = uuid4().hex
            started_at = datetime.now()
            data, content_type, extension = await asyncio.to_thread(
                report_renderer.render,
                report_title,
                report_content,
                report_format,
            )
            file_name = self._file_name(report_title, generated_at, extension)
            storage_key = self._storage_key(
                tenant_id,
                user_id,
                artifact_id,
                file_name,
                generated_at,
            )
            storage_type = await asyncio.to_thread(
                self._store,
                storage_key,
                data,
                content_type,
            )
            report = {
                "artifactId": artifact_id,
                "runId": str(run_id or ""),
                "conversationId": str(conversation_id or "") or None,
                "fileName": file_name,
                "contentType": content_type,
                "format": report_format,
                "size": len(data),
                "storageType": storage_type,
                "storageKey": storage_key,
                "downloadEndpoint": "/api/agent-assistant/report/download",
                "createdAt": generated_at.isoformat(),
            }
            self._remember(tenant_id, user_id, report)
            reports.append(report)
            logger.info(
                "智能体报告生成完成 tenantId=%s userId=%s runId=%s format=%s size=%s elapsedMs=%s",
                tenant_id,
                user_id,
                run_id,
                report_format,
                len(data),
                int((datetime.now() - started_at).total_seconds() * 1000),
            )
        return reports

    async def find_artifact(self, tenant_id: str, user_id: str, artifact_id: str | None) -> dict[str, Any]:
        normalized_id = str(artifact_id or "").strip()
        if not normalized_id:
            raise ValueError("报告文件编号不能为空")
        cached = self._ephemeral.get(normalized_id)
        if cached and cached.get("tenantId") == str(tenant_id) and cached.get("userId") == str(user_id):
            report = dict(cached.get("report") or {})
            self._validate_storage_key(tenant_id, user_id, report.get("storageKey"))
            return report
        if not database_client.enabled():
            raise ValueError("报告文件不存在或无权访问")
        rows = await database_client.fetch_all(
            """
            select e.metadata_json
            from agent_events e
            inner join agent_run r
              on r.tenant_id = e.tenant_id and r.id = e.run_id and r.deleted = false
            inner join conversation c
              on c.tenant_id = e.tenant_id and c.id = e.conversation_id and c.deleted = false
            where e.tenant_id = %s and r.user_id = %s and e.event_type = 'REPORT_READY'
              and e.metadata_json like %s
            order by e.created_at desc
            limit 20
            """,
            (self._to_int(tenant_id), self._to_int(user_id), "%" + normalized_id + "%"),
        )
        for row in rows:
            for report in reports_from_metadata(row.get("metadata_json")):
                if str(report.get("artifactId") or "") == normalized_id:
                    self._validate_storage_key(tenant_id, user_id, report.get("storageKey"))
                    return report
        raise ValueError("报告文件不存在或无权访问")

    async def read_artifact(self, report: dict[str, Any]) -> bytes:
        storage_key = str(report.get("storageKey") or "").strip()
        if not storage_key:
            raise ValueError("报告文件存储信息不完整")
        return await asyncio.to_thread(
            self._read,
            storage_key,
            str(report.get("storageType") or ""),
        )

    def _store(self, storage_key: str, data: bytes, content_type: str) -> str:
        if settings.report_minio_enabled:
            client = self._client()
            self._ensure_bucket(client)
            client.put_object(
                settings.report_minio_bucket,
                storage_key,
                io.BytesIO(data),
                len(data),
                content_type=content_type,
            )
            return "MINIO"
        path = self._local_path(storage_key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return "LOCAL"

    def _read(self, storage_key: str, storage_type: str) -> bytes:
        if storage_type.upper() == "MINIO":
            client = self._client()
            response = client.get_object(settings.report_minio_bucket, storage_key)
            try:
                return response.read()
            finally:
                response.close()
                response.release_conn()
        path = self._local_path(storage_key)
        if not path.exists() or not path.is_file():
            raise ValueError("报告文件不存在")
        return path.read_bytes()

    def _client(self):
        if self._minio_client is not None:
            return self._minio_client
        if not settings.report_minio_access_key or not settings.report_minio_secret_key:
            raise RuntimeError("报告存储已启用MinIO，但访问密钥未配置完整")
        from minio import Minio

        endpoint = str(settings.report_minio_endpoint or "").strip()
        parsed = urlparse(endpoint if "://" in endpoint else "http://" + endpoint)
        if not parsed.hostname:
            raise RuntimeError("报告存储MinIO地址不正确")
        host = parsed.hostname
        if parsed.port:
            host += ":" + str(parsed.port)
        self._minio_client = Minio(
            host,
            access_key=settings.report_minio_access_key,
            secret_key=settings.report_minio_secret_key,
            secure=parsed.scheme.lower() == "https",
        )
        return self._minio_client

    def _ensure_bucket(self, client) -> None:
        if self._bucket_ready:
            return
        bucket = str(settings.report_minio_bucket or "crm").strip()
        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)
        self._bucket_ready = True

    def _formats(self, values: list[str] | None) -> list[str]:
        source = values or ["docx", "pdf", "html"]
        result = []
        for value in source:
            normalized = str(value or "").strip().lower()
            if normalized == "word":
                normalized = "docx"
            if normalized not in {"docx", "pdf", "html"}:
                raise ValueError("报告格式仅支持docx、pdf和html")
            if normalized not in result:
                result.append(normalized)
        if not result:
            raise ValueError("请至少选择一种报告格式")
        return result

    def _title(self, value: str) -> str:
        title = re.sub(r"\s+", " ", str(value or "")).strip()
        if not title:
            return "智能分析报告"
        return title[:120]

    def _file_name(self, title: str, generated_at: datetime, extension: str) -> str:
        safe = re.sub(r"[\\/:*?\"<>|\x00-\x1f]", "-", title).strip(" .-")
        safe = safe[:80] or "智能分析报告"
        return "%s-%s%s" % (safe, generated_at.strftime("%Y%m%d-%H%M%S"), extension)

    def _storage_key(
            self,
            tenant_id: str,
            user_id: str,
            artifact_id: str,
            file_name: str,
            generated_at: datetime) -> str:
        return "ai-reports/%s/%s/%s/%s/%s" % (
            self._safe_id(tenant_id),
            self._safe_id(user_id),
            generated_at.strftime("%Y%m%d"),
            artifact_id,
            file_name,
        )

    def _local_path(self, storage_key: str) -> Path:
        root = Path(settings.report_local_dir).expanduser().resolve()
        target = (root / storage_key).resolve()
        if root != target and root not in target.parents:
            raise ValueError("报告文件存储地址不合法")
        return target

    def _validate_storage_key(self, tenant_id: str, user_id: str, storage_key: Any) -> None:
        prefix = "ai-reports/%s/%s/" % (self._safe_id(tenant_id), self._safe_id(user_id))
        if not str(storage_key or "").startswith(prefix):
            raise ValueError("报告文件归属校验失败")

    def _remember(self, tenant_id: str, user_id: str, report: dict[str, Any]) -> None:
        artifact_id = str(report.get("artifactId") or "")
        if not artifact_id:
            return
        self._ephemeral[artifact_id] = {
            "tenantId": str(tenant_id),
            "userId": str(user_id),
            "report": dict(report),
        }
        while len(self._ephemeral) > 1000:
            self._ephemeral.pop(next(iter(self._ephemeral)))

    def _safe_id(self, value: Any) -> str:
        text = str(value or "").strip()
        if not re.fullmatch(r"[A-Za-z0-9_-]+", text):
            raise ValueError("报告文件归属编号不正确")
        return text

    def _to_int(self, value: Any) -> int | None:
        try:
            return int(value)
        except (TypeError, ValueError):
            return None


def reports_from_metadata(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            return []
    if not isinstance(value, dict):
        return []
    reports = value.get("reports") or value.get("attachments") or []
    if not isinstance(reports, list):
        return []
    result = []
    for report in reports:
        if isinstance(report, dict) and report.get("artifactId") and report.get("fileName"):
            result.append(dict(report))
    return result


report_service = ReportService()
