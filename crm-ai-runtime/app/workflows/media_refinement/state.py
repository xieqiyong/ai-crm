import operator
from typing import Annotated, Any, TypedDict

from langchain.messages import AnyMessage
from langgraph.graph.message import add_messages

from app.schemas.media_refinement import MediaRefinementResult


class MediaRefinementState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    transcript_text: str
    normalized_transcript: str
    chunks: list[str]
    chunk: str
    chunk_index: int
    chunk_results: Annotated[list[dict[str, Any]], operator.add]
    structured_response: MediaRefinementResult

