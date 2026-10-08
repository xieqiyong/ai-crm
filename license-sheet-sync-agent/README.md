# 企微 AMC License 表格同步服务

这是一个独立部署的 Linux 服务，负责把企微智能表格中的新增 AMC License 申请自动提交到 License 平台。

完整流程：

1. Playwright 使用已登录的企微浏览器会话打开智能表格。
2. 自动导出“本地 Excel 表格（.xlsx）”。
3. 解析 `AMC用户信息收集表单`，校验手机号、邮箱、App Id 和版本号。
4. 以“填写时间 + 手机号 + App Id”生成稳定数据身份，只识别真实新增或变更。
5. 新增数据先进入 SQLite 事务发件箱，再调用 License 内部接口。
6. 使用固定外部请求编号保证重试幂等，保存申请结果供追溯。

## 当前字段对应关系

| Excel 字段 | License 请求字段 | 说明 |
|---|---|---|
| 公司名称 | `company` | 必填 |
| 姓名 | `name` | 必填 |
| 手机号-手机号 | `phone` | 必填，校验大陆手机号 |
| 企业邮箱-电子邮箱 | `email` | 必填，支持企业邮箱和个人邮箱 |
| App Id | `machineCode` | 必填，按 32 位十六进制机器码校验 |
| 版本号 | `productVersion` | 必填 |
| 申请类型 | `usage` | 申请用途 |
| 填写人、所在部门、填写时间、用户类型、申请类型 | `baseInfo` | 作为 JSON 来源信息保存 |
| 固定值 | `productName=OpenOcta`、`channelCode=000`、`authType=正式` | 固定产品、官方渠道和申请类型，防止表格篡改 |

`App Id -> machineCode` 是依据 License 平台现有 OpenOcta 申请模型确定的映射。如果后续表单中的 App Id 含义变化，应先调整配置和校验规则，再启用提交。

## 目录

```text
license-sheet-sync-agent/
├─ AMC_许可申请表单.xlsx     本地字段校验样例
├─ config.example.json      配置模板
├─ config.json              本机配置，不提交 Git
├─ docker-compose.yml       Linux 部署编排
├─ migrations/              SQLite 手工版本迁移
├─ src/browser/             企微页面打开与 Excel 导出
├─ src/excel/               表头识别、字段校验和请求转换
├─ src/jobs/                同步任务与事务发件箱
├─ src/license/             License 内部接口客户端
└─ src/persistence/         SQLite 数据访问
```

## 一、先改造并启动 License 后端

`dc-manager` 新增了内部申请接口：

```text
POST /internal/license/openocta/applications
X-License-Internal-Token: <内部令牌>
```

首次使用前由开发人员手工执行：

```text
D:\work\dc-manager\sql\manual\20260921-license-external-request-id.sql
```

该 SQL 为 `license_apply` 添加 `external_request_id` 唯一索引，用于服务端最终幂等。应用不会在启动时自动修改历史数据库结构。

启动 `dc-manager` 时设置：

```bash
LICENSE_INTERNAL_TOKEN=请替换为足够长的随机令牌
```

同步服务 `.env` 中配置相同的 `LICENSE_INTERNAL_TOKEN`。两个服务之间只传原始令牌，不加 `Bearer` 前缀。

本地手工验证接口时可使用以下示例；示例内容是虚构数据，不要直接写入生产环境：

```bash
curl -X POST 'http://127.0.0.1:8089/internal/license/openocta/applications' \
  -H 'Content-Type: application/json' \
  -H 'X-License-Internal-Token: 请替换为真实内部令牌' \
  -d '{
    "externalRequestId":"local-test-001",
    "company":"示例科技有限公司",
    "name":"测试人员",
    "phone":"13800000000",
    "email":"test@example-company.com",
    "machineCode":"0123456789abcdef0123456789abcdef",
    "productName":"OpenOcta",
    "productVersion":"1.0.0"
  }'
```

重复发送相同 `externalRequestId` 应返回同一份申请，不会重复创建。

## 二、本地校验现有 Excel

安装依赖并校验字段，不需要打开浏览器，也不会调用 License 接口：

```bash
npm install
npm run validate -- "AMC_许可申请表单.xlsx"
```

日志只输出行号和失败原因，不打印姓名、手机号、邮箱等个人信息。当前样例中不符合邮箱或 App Id 规则的数据会标记为无效，必须人工修正后才会进入申请队列。

## 三、首次基线必须执行

已有表格中的历史记录不应在服务首次上线时全部申请 License，因此上线顺序固定为：

1. 保持 `config.json` 中 `licenseApi.enabled=false`。
2. 初始化 SQLite。
3. 登录企微。
4. 执行一次基线，把当时已有记录标记为 `BASELINED`。
5. 核对数量后，才允许打开 License 接口。

命令如下：

```bash
npm run migrate
npm run login
npm run baseline
npm run doctor
```

`baseline` 在接口已启用时会直接拒绝执行。历史基线不会进入发件箱；基线之后出现的新记录才会提交。

企微导出偶发关闭下载页面或浏览器上下文时，任务会按照 `export.maxAttempts` 自动重建浏览器并重试；登录失效、表头不匹配、缺少导出入口等业务错误不会重试。

如果之前仅执行过普通 `npm run run` 且接口一直关闭，重新执行 `npm run baseline` 会把尚未入队的 `DISCOVERED` 历史数据安全转为 `BASELINED`。

## 四、本地联调

确认 License 后端可用并已执行数据库 SQL 后：

1. 如果同步服务直接通过 npm 运行，在当前 PowerShell 设置 `$env:LICENSE_INTERNAL_TOKEN='与后端一致的令牌'`；`.env` 仅由 Docker Compose 自动读取。
2. 配置 `LICENSE_API_BASE_URL`。本机运行可使用 `http://127.0.0.1:8089`，容器互通可使用 `http://dc-manager:8089`，License 平台上云后改为实际云端地址。
3. 配置 `LICENSE_API_APPLY_PATH=/internal/license/openocta/applications`。
4. 设置 `LICENSE_API_ENABLED=true`。环境变量优先于 `config.json`，切换地址不需要修改代码或重建镜像。
5. 在表格中新加一条专用测试记录，再执行 `npm run run`。

AMC表格固定申请 `OpenOcta` 产品。启用自动提交前，必须先在License平台为 `OpenOcta` 配置表格中使用的版本、渠道及授权菜单；当前表格使用 `v2.0.0`。

```bash
npm run run
npm run doctor
```

不要删除 SQLite 后直接重跑，否则系统会失去历史基线和去重依据。

## 五、Linux 容器部署

服务器需要 Docker 和 Docker Compose。首次准备：

```bash
cp config.example.json config.json
cp .env.example .env
docker compose build
docker compose run --rm license-sheet-sync npm run migrate
docker compose up -d
```

通过 SSH 隧道访问 noVNC，避免将 6080 暴露到公网：

```bash
ssh -L 6080:127.0.0.1:6080 root@服务器地址
```

浏览器打开 `http://127.0.0.1:6080/vnc.html`，然后执行：

```bash
docker compose exec license-sheet-sync npm run login
```

扫码后回到终端按回车，企微 Cookie 会保存在 `data/browser-profile`。

容器内首次基线：

```bash
docker compose exec license-sheet-sync npm run baseline
```

确认无误后再启用 API 并重启：

```bash
sed -i 's/^LICENSE_API_ENABLED=.*/LICENSE_API_ENABLED=true/' .env
docker compose restart license-sheet-sync
docker compose logs -f --tail=200 license-sheet-sync
```

License 平台迁移或上云后，只需修改 `.env` 中的 `LICENSE_API_BASE_URL` 并执行 `docker compose up -d`。SQLite、浏览器登录态、历史基线和幂等记录均保存在 `data` 目录，不受接口地址切换影响。

## 增量状态说明

- `BASELINED`：首次上线前已存在的历史记录，不自动申请。
- `DISCOVERED`：基线后新发现的合法记录，可以进入申请队列。
- `INVALID`：字段不完整或格式不合规，不会自动申请。
- `PENDING`：已进入事务发件箱，等待提交。
- `APPLIED`：License 平台已经成功创建申请。
- `CHANGED`：进入申请流程后源数据又发生变化，需要人工核对。
- `RETRY_WAIT`：接口调用失败，等待指数退避重试。
- `FAILED`：超过最大重试次数，需要人工处理。

每条申请的外部请求编号固定为：

```text
wecom-license-{sourceId}-{rowKey}
```

同步服务本地发件箱负责可靠重试，License 后端的唯一索引负责最终防重，两层缺一不可。

## 常用命令

```bash
# 手工执行数据库迁移
npm run migrate

# 只校验本地 Excel，不访问企微和 License
npm run validate -- "AMC_许可申请表单.xlsx"

# 首次建立历史数据基线
npm run baseline

# 立即下载并同步一次
npm run run

# 只重试到期的 License 申请
npm run retry

# 查看配置、数据库和最近一次同步
npm run doctor

# 启动定时服务，默认每5分钟检查一次
npm run start
```

## 安全与运维

- `config.json`、`.env`、`data/browser-profile` 含链接、令牌或企微登录状态，禁止提交 Git。
- `data` 必须备份，其中 SQLite 是增量识别和防重复申请的依据。
- 数据库迁移不会在应用启动阶段自动执行，新迁移必须由开发人员确认后运行。
- 页面结构变化时任务会失败并保存现场，不会猜测数据后继续提交。
- 日志禁止输出完整请求正文，避免泄露手机号、邮箱和机器码。
