from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "crm-ai-runtime"
    host: str = "0.0.0.0"
    port: int = 8001
    internal_token: str = ""
    log_level: str = "INFO"

    llm_timeout_seconds: int = 60

    web_search_enabled: bool = False
    web_search_provider: str = "searxng"
    web_search_endpoint: str = ""
    web_search_api_key: str = ""
    web_search_timeout_seconds: int = 8
    web_search_max_results: int = 5


@lru_cache
def get_settings() -> Settings:
    return Settings(_env_prefix="CRM_AI_")


settings = get_settings()
