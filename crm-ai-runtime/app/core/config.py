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

    database_enabled: bool = False
    database_uri: str = ""
    agent_config_source: str = "request"
    agent_config_fail_fast: bool = False

    nacos_enabled: bool = False
    nacos_server_addr: str = "localhost:8848"
    nacos_namespace: str = ""
    nacos_group: str = "DEFAULT_GROUP"
    nacos_username: str = ""
    nacos_password: str = ""
    nacos_service_name: str = "crm-ai-runtime"
    nacos_ip: str = ""
    nacos_port: int = 8001
    nacos_cluster_name: str = "DEFAULT"
    nacos_ephemeral: bool = True
    nacos_heartbeat_seconds: int = 5


@lru_cache
def get_settings() -> Settings:
    return Settings(_env_prefix="CRM_AI_")


settings = get_settings()
