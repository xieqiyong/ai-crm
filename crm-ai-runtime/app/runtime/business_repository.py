import json
from datetime import datetime
from typing import Any

from fastapi import HTTPException

from app.platform.database import database_client


class BusinessRepository:
    async def lead_detail(self, tenant_id: str, user_id: str, data_scope: str, lead_id: str) -> dict[str, Any]:
        row = await database_client.fetch_one(
            """
            select l.id, l.tenant_id, l.name, l.company_name, l.phone, l.email, l.source, l.status,
                   l.customer_id, c.name as customer_name, l.converted_at, l.converted_by,
                   cu.display_name as converted_by_name, l.converted_type, l.ai_summary,
                   l.ai_suggested_customer_name, l.ai_suggested_contact_name, l.ai_confidence,
                   l.ai_analyzed_at, l.owner_id, ou.display_name as owner_name, l.remark,
                   l.created_at, l.updated_at
            from crm_lead l
            left join crm_customer c on c.id = l.customer_id and c.tenant_id = l.tenant_id and c.deleted = false
            left join sys_user cu on cu.id = l.converted_by and cu.tenant_id = l.tenant_id and cu.deleted = false
            left join sys_user ou on ou.id = l.owner_id and ou.tenant_id = l.tenant_id and ou.deleted = false
            where l.tenant_id = %s and l.id = %s and l.deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(lead_id)),
        )
        if row is None:
            raise HTTPException(status_code=404, detail="线索不存在")
        if (data_scope or "").upper() == "SELF" and self._id(row.get("owner_id")) != self._id(user_id):
            raise HTTPException(status_code=403, detail="无权访问该线索")
        return self._lead_response(row)

    async def save_lead_ai_analysis(
            self,
            tenant_id: str,
            lead_id: str,
            summary: str,
            customer_name: str | None,
            contact_name: str | None,
            confidence: Any) -> dict[str, Any]:
        now = datetime.now()
        await database_client.execute(
            """
            update crm_lead
            set ai_summary = %s,
                ai_suggested_customer_name = %s,
                ai_suggested_contact_name = %s,
                ai_confidence = %s,
                ai_analyzed_at = %s,
                updated_at = %s
            where tenant_id = %s and id = %s and deleted = false
            """,
            (
                summary,
                customer_name,
                contact_name,
                self._float(confidence),
                now,
                now,
                self._to_int(tenant_id),
                self._to_int(lead_id),
            ),
        )
        row = await database_client.fetch_one(
            """
            select l.id, l.tenant_id, l.name, l.company_name, l.phone, l.email, l.source, l.status,
                   l.customer_id, c.name as customer_name, l.converted_at, l.converted_by,
                   cu.display_name as converted_by_name, l.converted_type, l.ai_summary,
                   l.ai_suggested_customer_name, l.ai_suggested_contact_name, l.ai_confidence,
                   l.ai_analyzed_at, l.owner_id, ou.display_name as owner_name, l.remark,
                   l.created_at, l.updated_at
            from crm_lead l
            left join crm_customer c on c.id = l.customer_id and c.tenant_id = l.tenant_id and c.deleted = false
            left join sys_user cu on cu.id = l.converted_by and cu.tenant_id = l.tenant_id and cu.deleted = false
            left join sys_user ou on ou.id = l.owner_id and ou.tenant_id = l.tenant_id and ou.deleted = false
            where l.tenant_id = %s and l.id = %s and l.deleted = false
            limit 1
            """,
            (self._to_int(tenant_id), self._to_int(lead_id)),
        )
        return self._lead_response(row)

    def lead_ai_response(
            self,
            lead: dict[str, Any],
            result: dict[str, Any],
            run_id: str | None,
            conversation_id: str | None,
            events: list[Any],
            saved_lead: dict[str, Any] | None = None) -> dict[str, Any]:
        draft = self._convert_draft(lead, result.get("convertDraft"))
        customer_profile = self._customer_profile(result.get("customerProfile"))
        summary = self._sales_summary(result, customer_profile)
        return {
            "leadId": lead.get("id"),
            "runId": run_id,
            "conversationId": conversation_id,
            "leadName": lead.get("name"),
            "available": True,
            "success": True,
            "message": "线索 AI 分析完成",
            "summary": summary,
            "conclusionTitle": self._text(result.get("conclusionTitle")) or "线索分析结论",
            "salesConclusion": self._text(result.get("salesConclusion") or result.get("summary")),
            "stage": self._stage(result.get("stage") or lead.get("status")),
            "priority": self._priority(result.get("priority")),
            "recommendConvert": bool(result.get("recommendConvert")),
            "score": self._clamp_int(result.get("score"), 0, 100),
            "confidence": self._clamp_float(result.get("confidence"), 0.0, 1.0),
            "reason": self._text(result.get("reason")),
            "nextAction": self._text(result.get("nextAction")) or self._first_text(result.get("nextActions")),
            "keyFindings": self._string_list(result.get("keyFindings"), 4),
            "nextActions": self._string_list(result.get("nextActions"), 4),
            "riskWarnings": self._string_list(result.get("riskWarnings"), 4),
            "convertDraft": draft,
            "customerProfile": customer_profile,
            "rawOutput": json.dumps(result, ensure_ascii=False, default=str)[:2000],
            "runtimeEvents": events,
            "lead": saved_lead or lead,
        }

    def parse_result(self, value: str) -> dict[str, Any]:
        text = self._text(value)
        if not text:
            return {}
        start = text.find("{")
        end = text.rfind("}")
        if start < 0 or end <= start:
            return {}
        try:
            parsed = json.loads(text[start:end + 1])
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}

    def _lead_response(self, row: dict[str, Any] | None) -> dict[str, Any]:
        if row is None:
            return {}
        return {
            "id": self._id(row.get("id")),
            "tenantId": self._id(row.get("tenant_id")),
            "name": row.get("name"),
            "companyName": row.get("company_name"),
            "phone": row.get("phone"),
            "email": row.get("email"),
            "source": row.get("source"),
            "status": row.get("status"),
            "customerId": self._id(row.get("customer_id")),
            "customerName": row.get("customer_name"),
            "convertedAt": self._datetime(row.get("converted_at")),
            "convertedBy": self._id(row.get("converted_by")),
            "convertedByName": row.get("converted_by_name"),
            "convertedType": row.get("converted_type"),
            "aiSummary": row.get("ai_summary"),
            "aiSuggestedCustomerName": row.get("ai_suggested_customer_name"),
            "aiSuggestedContactName": row.get("ai_suggested_contact_name"),
            "aiConfidence": self._float(row.get("ai_confidence")),
            "aiAnalyzedAt": self._datetime(row.get("ai_analyzed_at")),
            "ownerId": self._id(row.get("owner_id")),
            "ownerName": row.get("owner_name"),
            "remark": row.get("remark"),
            "createdAt": self._datetime(row.get("created_at")),
            "updatedAt": self._datetime(row.get("updated_at")),
        }

    def _sales_summary(self, result: dict[str, Any], customer_profile: dict[str, Any]) -> str:
        lines = [
            "### " + (self._text(result.get("conclusionTitle")) or "线索分析结论"),
            "",
            self._text(result.get("salesConclusion") or result.get("summary")) or "暂无销售结论",
            "",
            "#### 关键证据",
        ]
        lines.extend(self._markdown_list(self._string_list(result.get("keyFindings"), 4), "暂无关键证据"))
        lines.extend(["", "#### 下一步动作"])
        lines.extend(self._markdown_list(self._string_list(result.get("nextActions"), 4), self._text(result.get("nextAction")) or "暂无建议动作"))
        risks = self._string_list(result.get("riskWarnings"), 4)
        if risks:
            lines.extend(["", "#### 风险提醒"])
            lines.extend(self._markdown_list(risks, ""))
        if customer_profile.get("companyScale") or customer_profile.get("industry") or customer_profile.get("sourceUrls"):
            lines.extend(["", "#### AI搜索客户档案"])
            lines.append("- 公司规模：" + (customer_profile.get("companyScale") or "未确认"))
            lines.append("- 公司行业：" + (customer_profile.get("industry") or "未确认"))
            source_urls = customer_profile.get("sourceUrls") or []
            if source_urls:
                lines.append("- 来源链接：")
                lines.extend(self._markdown_list(source_urls[:3], ""))
        return "\n".join(lines)[:6000]

    def _convert_draft(self, lead: dict[str, Any], value: Any) -> dict[str, Any]:
        draft = value if isinstance(value, dict) else {}
        return {
            "customerName": self._text(draft.get("customerName")) or self._text(lead.get("companyName")) or self._text(lead.get("name")),
            "industry": self._text(draft.get("industry")),
            "contactName": self._text(draft.get("contactName")) or self._text(lead.get("name")),
            "contactPhone": self._text(draft.get("contactPhone")) or self._text(lead.get("phone")),
            "contactEmail": self._text(draft.get("contactEmail")) or self._text(lead.get("email")),
            "level": self._text(draft.get("level")) or "NORMAL",
            "status": self._text(draft.get("status")) or "POTENTIAL",
            "ownerId": lead.get("ownerId"),
            "remark": self._text(draft.get("remark")) or self._default_remark(lead),
        }

    def _customer_profile(self, value: Any) -> dict[str, Any]:
        profile = value if isinstance(value, dict) else {}
        return {
            "companyScale": self._text(profile.get("companyScale")),
            "industry": self._text(profile.get("industry")),
            "sourceUrls": self._string_list(profile.get("sourceUrls"), 3),
        }

    def _markdown_list(self, values: list[str], empty: str) -> list[str]:
        if not values:
            return ["- " + empty] if empty else []
        return ["- " + item for item in values if item]

    def _default_remark(self, lead: dict[str, Any]) -> str:
        remark = self._text(lead.get("remark"))
        return "原线索备注：" + remark if remark else ""

    def _string_list(self, value: Any, max_size: int) -> list[str]:
        if not isinstance(value, list):
            return []
        result: list[str] = []
        for item in value:
            text = self._text(item)
            if text:
                result.append(text[:300])
            if len(result) >= max_size:
                break
        return result

    def _first_text(self, value: Any) -> str:
        values = self._string_list(value, 1)
        return values[0] if values else ""

    def _stage(self, value: Any) -> str:
        text = self._text(value).upper()
        if text in {"NEW", "FOLLOWING", "QUALIFIED", "CONVERTED", "CLOSED", "UNKNOWN"}:
            return text
        return "UNKNOWN"

    def _priority(self, value: Any) -> str:
        text = self._text(value).upper()
        if text in {"HIGH", "MEDIUM", "LOW"}:
            return text
        return "MEDIUM"

    def _clamp_int(self, value: Any, minimum: int, maximum: int) -> int:
        try:
            number = int(value)
        except (TypeError, ValueError):
            number = minimum
        return max(minimum, min(maximum, number))

    def _clamp_float(self, value: Any, minimum: float, maximum: float) -> float:
        number = self._float(value)
        return max(minimum, min(maximum, number))

    def _float(self, value: Any) -> float:
        try:
            return float(value or 0)
        except (TypeError, ValueError):
            return 0.0

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

    def _text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    def _datetime(self, value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, datetime):
            return value.isoformat()
        return str(value)


business_repository = BusinessRepository()
