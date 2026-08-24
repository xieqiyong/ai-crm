from functools import lru_cache
import os
from pathlib import Path

try:
    from dotenv import load_dotenv
except ImportError:
    def load_dotenv(*args, **kwargs):
        return False

from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_FILE = Path(__file__).resolve().parents[2] / ".env"

load_dotenv(ENV_FILE, override=False)
os.environ.setdefault("LANGGRAPH_STRICT_MSGPACK", "true")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=str(ENV_FILE), extra="ignore")

    app_name: str = "crm-ai-runtime"
    host: str = "0.0.0.0"
    port: int = 8001
    internal_token: str = ""
    jwt_secret: str = ""
    log_level: str = "INFO"

    llm_timeout_seconds: int = 60
    llm_stream_include_usage: bool = True
    assistant_stream_chunk_size: int = 8
    assistant_stream_delay_ms: int = 25

    web_search_enabled: bool = True
    web_search_provider: str = "searxng"
    web_search_endpoint: str = "http://180.76.225.232:8888"
    web_search_api_key: str = ""
    web_search_timeout_seconds: int = 8
    web_search_max_results: int = 5

    crm_api_base_url: str = "http://crm-gateway:8090"
    crm_api_timeout_seconds: int = 30
    mcp_fail_fast: bool = True

    checkpoint_enabled: bool = False
    checkpoint_backend: str = "memory"
    checkpoint_postgres_uri: str = ""
    checkpoint_pool_min_size: int = 1
    checkpoint_pool_max_size: int = 10
    checkpoint_pool_timeout_seconds: int = 30
    trace_capture_payload: bool = False

    database_enabled: bool = False
    database_uri: str = ""
    agent_config_source: str = "database"
    agent_config_fail_fast: bool = True
    token_daily_limit: int = 0
    token_reserve_output_tokens: int = 4000

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
