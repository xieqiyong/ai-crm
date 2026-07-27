from typing import Any

import httpx

from app.core.config import settings
from app.core.trace_utils import trace_search_inputs, trace_search_outputs, traceable


class CompanyWebSearchService:
    @traceable(
        run_type="tool",
        name="客户公开信息检索",
        process_inputs=trace_search_inputs,
        process_outputs=trace_search_outputs,
    )
    async def search(self, company_name: str) -> dict[str, Any]:
        company = text(company_name)
        profile = empty_profile(company)
        if not company:
            profile["sourceSummary"] = "线索中没有公司名称，未执行公开搜索。"
            return profile
        if not settings.web_search_enabled:
            profile["sourceSummary"] = "公开搜索未启用。"
            return profile
        if not text(settings.web_search_endpoint):
            profile["sourceSummary"] = "公开搜索地址未配置。"
            return profile
        provider = text(settings.web_search_provider).lower()
        if provider == "tavily":
            return await self._search_tavily(company, profile)
        if provider == "serper":
            return await self._search_serper(company, profile)
        return await self._search_searxng(company, profile)

    async def _search_searxng(self, company: str, profile: dict[str, Any]) -> dict[str, Any]:
        endpoint = text(settings.web_search_endpoint).rstrip("/")
        params = {"q": build_query(company), "format": "json"}
        async with httpx.AsyncClient(timeout=max(3, settings.web_search_timeout_seconds)) as client:
            response = await client.get(f"{endpoint}/search", params=params)
        return fill_profile_from_results(company, profile, parse_searxng(response))

    async def _search_tavily(self, company: str, profile: dict[str, Any]) -> dict[str, Any]:
        if not text(settings.web_search_api_key):
            profile["sourceSummary"] = "Tavily 密钥未配置。"
            return profile
        body = {"query": build_query(company), "max_results": max_results()}
        headers = {"Authorization": f"Bearer {settings.web_search_api_key}"}
        async with httpx.AsyncClient(timeout=max(3, settings.web_search_timeout_seconds)) as client:
            response = await client.post(text(settings.web_search_endpoint), json=body, headers=headers)
        return fill_profile_from_results(company, profile, parse_tavily(response))

    async def _search_serper(self, company: str, profile: dict[str, Any]) -> dict[str, Any]:
        if not text(settings.web_search_api_key):
            profile["sourceSummary"] = "Serper 密钥未配置。"
            return profile
        body = {"q": build_query(company), "num": max_results()}
        headers = {"X-API-KEY": settings.web_search_api_key, "Content-Type": "application/json"}
        async with httpx.AsyncClient(timeout=max(3, settings.web_search_timeout_seconds)) as client:
            response = await client.post(text(settings.web_search_endpoint), json=body, headers=headers)
        return fill_profile_from_results(company, profile, parse_serper(response))


def parse_searxng(response: httpx.Response) -> list[dict[str, str]]:
    if response.status_code < 200 or response.status_code >= 300:
        return []
    payload = response.json()
    values = []
    for item in (payload.get("results") or [])[:max_results()]:
        values.append({
            "title": text(item.get("title")),
            "url": text(item.get("url")),
            "snippet": text(item.get("content")),
        })
    return values


def parse_tavily(response: httpx.Response) -> list[dict[str, str]]:
    if response.status_code < 200 or response.status_code >= 300:
        return []
    payload = response.json()
    values = []
    for item in (payload.get("results") or [])[:max_results()]:
        values.append({
            "title": text(item.get("title")),
            "url": text(item.get("url")),
            "snippet": text(item.get("content")),
        })
    return values


def parse_serper(response: httpx.Response) -> list[dict[str, str]]:
    if response.status_code < 200 or response.status_code >= 300:
        return []
    payload = response.json()
    values = []
    for item in (payload.get("organic") or [])[:max_results()]:
        values.append({
            "title": text(item.get("title")),
            "url": text(item.get("link")),
            "snippet": text(item.get("snippet")),
        })
    return values


def fill_profile_from_results(company: str, profile: dict[str, Any], results: list[dict[str, str]]) -> dict[str, Any]:
    if not results:
        profile["sourceSummary"] = "未检索到可靠公开信息。"
        return profile
    snippets = [item["snippet"] for item in results if item.get("snippet")]
    urls = [item["url"] for item in results if item.get("url")]
    profile["available"] = True
    profile["companyName"] = company
    profile["sourceUrls"] = urls[:6]
    profile["sourceSummary"] = "；".join(snippets)[:600]
    return profile


def empty_profile(company: str) -> dict[str, Any]:
    return {
        "available": False,
        "companyName": company,
        "legalRepresentative": "",
        "keyPerson": "",
        "companyScale": "",
        "industry": "",
        "phone": "",
        "email": "",
        "website": "",
        "address": "",
        "registeredCapital": "",
        "sourceSummary": "",
        "searchedAt": "",
        "sourceUrls": [],
    }


def build_query(company: str) -> str:
    return f"{company} 公司 信息 法定代表人 规模 行业 电话 官网"


def max_results() -> int:
    value = settings.web_search_max_results
    if value < 1:
        return 1
    if value > 10:
        return 10
    return value


def text(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()
