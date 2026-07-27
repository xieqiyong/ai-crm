from functools import lru_cache
import os

try:
    from dotenv import load_dotenv
except ImportError:
    def load_dotenv(*args, **kwargs):
        return False

from pydantic_settings import BaseSettings, SettingsConfigDict

load_dotenv(override=False)
os.environ.setdefault("LANGGRAPH_STRICT_MSGPACK", "true")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "crm-ai-runtime"
    host: str = "0.0.0.0"
    port: int = 8001
    internal_token: str = ""
    log_level: str = "INFO"

    llm_timeout_seconds: int = 60

    web_search_enabled: bool = True
    web_search_provider: str = "searxng"
    web_search_endpoint: str = "http://180.76.225.232:8888"
    web_search_api_key: str = ""
    web_search_timeout_seconds: int = 8
    web_search_max_results: int = 5

    checkpoint_enabled: bool = False
    checkpoint_backend: str = "memory"
    checkpoint_postgres_uri: str = ""
    checkpoint_auto_setup: bool = True
    trace_capture_payload: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings(_env_prefix="CRM_AI_")


settings = get_settings()
