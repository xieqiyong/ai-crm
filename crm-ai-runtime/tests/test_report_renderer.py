import io
import json
import unittest
import zipfile
from unittest.mock import patch

from app.reports.renderer import MarkdownReportParser, report_renderer
from app.reports.service import ReportService, reports_from_metadata
from app.runtime.stream_adapter import AgentStreamAccumulator


REPORT_CONTENT = """## 跟进总结

客户已经确认需要产品演示，**建议本周安排**。

| 跟进对象 | 当前状态 |
| --- | --- |
| 杭州示例客户 | 高意向 |

### 下一步动作

1. 确认预算范围
2. 邀请决策人参加演示

- 风险：采购时间未确认

> 所有结论均来自真实跟进记录。
"""


class ReportRendererTest(unittest.TestCase):
    def test_parse_markdown_blocks(self):
        blocks = MarkdownReportParser().parse(REPORT_CONTENT)
        kinds = [item.kind for item in blocks]
        self.assertIn("heading", kinds)
        self.assertIn("table", kinds)
        self.assertIn("list", kinds)
        self.assertIn("quote", kinds)

    def test_render_docx(self):
        data, content_type, extension = report_renderer.render("跟进记录分析报告", REPORT_CONTENT, "docx")
        self.assertEqual(".docx", extension)
        self.assertIn("wordprocessingml", content_type)
        self.assertTrue(zipfile.is_zipfile(io.BytesIO(data)))

    def test_render_pdf(self):
        data, content_type, extension = report_renderer.render("跟进记录分析报告", REPORT_CONTENT, "pdf")
        self.assertEqual(".pdf", extension)
        self.assertEqual("application/pdf", content_type)
        self.assertTrue(data.startswith(b"%PDF-"))

    def test_render_html(self):
        data, content_type, extension = report_renderer.render("跟进记录分析报告", REPORT_CONTENT, "html")
        text = data.decode("utf-8")
        self.assertEqual(".html", extension)
        self.assertIn("text/html", content_type)
        self.assertIn("跟进记录分析报告", text)
        self.assertIn("<table>", text)

    def test_parse_report_event_metadata(self):
        value = json.dumps({
            "reports": [{
                "artifactId": "artifact-1",
                "fileName": "跟进记录分析报告.pdf",
            }],
        }, ensure_ascii=False)
        reports = reports_from_metadata(value)
        self.assertEqual(1, len(reports))
        self.assertEqual("artifact-1", reports[0]["artifactId"])

    def test_normalize_report_formats(self):
        service = ReportService()
        self.assertEqual(["docx", "pdf", "html"], service._formats(["word", "pdf", "html", "pdf"]))


class ReportServiceTest(unittest.IsolatedAsyncioTestCase):
    async def test_generate_and_read_local_reports(self):
        service = ReportService()
        with patch.object(service, "_store", return_value="LOCAL"), patch.object(service, "_read", return_value=b"report"):
                reports = await service.generate(
                    tenant_id="1001",
                    user_id="2001",
                    run_id="3001",
                    conversation_id="4001",
                    title="跟进记录分析报告",
                    content=REPORT_CONTENT,
                    formats=["docx", "pdf", "html"],
                )
                self.assertEqual(3, len(reports))
                for report in reports:
                    artifact = await service.find_artifact("1001", "2001", report["artifactId"])
                    data = await service.read_artifact(artifact)
                    self.assertEqual(b"report", data)

    async def test_report_ready_event_is_recorded(self):
        accumulator = AgentStreamAccumulator()
        await accumulator._consume_custom_part({
            "type": "REPORT_READY",
            "content": "报告文件已生成",
            "toolName": "generate_report",
            "metadata": {
                "reports": [{
                    "artifactId": "artifact-1",
                    "fileName": "跟进记录分析报告.pdf",
                }],
            },
        })
        self.assertEqual(1, len(accumulator.events))
        self.assertEqual("REPORT_READY", accumulator.events[0]["type"])


if __name__ == "__main__":
    unittest.main()
