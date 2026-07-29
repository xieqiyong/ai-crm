# 知识库版本化与蓝绿索引设计

## 一、建设目标

本次改造解决以下问题：

1. 同一份文档重复上传时，不再重复解析、切片、Embedding 和写入索引。
2. 文档更新时，旧版本继续提供检索，新版本完整构建成功后才切换。
3. 更换 Embedding 模型、切片策略或索引配置时，可以全量重建新索引，不影响线上查询。
4. 全量重建期间产生的文档新增、更新和删除，通过 Outbox、Kafka 和数据库对账补偿到新索引。
5. 构建失败只影响候选版本或绿色索引，不覆盖当前活动版本。
6. 所有业务数据读写继续使用 MyBatis-Plus，JPA 仅负责实体 DDL 描述。

## 二、核心数据模型

### 2.1 `kb_document`

`kb_document` 表示文档当前编辑态及活动指针，新增字段如下：

| 字段 | 说明 |
| --- | --- |
| `source_key` | 文档稳定身份，同一租户下用于识别同一来源 |
| `raw_file_hash` | 原始上传字节的 SHA-256 |
| `normalized_content_hash` | 文本标准化后的 SHA-256 |
| `active_version_id` | 当前对外提供检索的文档版本 |
| `pending_version_id` | 正在构建的候选版本 |

`sourceKey` 的默认生成顺序是：

1. 接口明确传入的 `sourceKey`；
2. `URL:` 加标准化后的来源链接；
3. `OBJECT:` 加标准化后的对象键或文件名；
4. `MANUAL:` 加文档编号。

文件导入接口已经支持可选参数 `sourceKey`。如果不同目录下存在同名文件，建议调用方传入稳定的业务来源键，避免把它们识别成同一份文档。

### 2.2 `kb_document_version`

每次需要重新构建时创建不可变版本快照，保存标题、分类、标签、来源、正文快照、内容 Hash、构建指纹和构建结果。

版本状态如下：

```text
CREATED
  -> CHUNKING
  -> INDEXING
  -> READY
  -> ACTIVE
  -> RETIRED
```

异常状态：

- `FAILED`：候选版本构建失败，旧活动版本继续服务。
- `SUPERSEDED`：构建期间文档再次被修改，当前候选版本不再允许切换。

### 2.3 `kb_index_generation`

一个索引代次对应一套可独立使用的 Elasticsearch 索引和 Milvus Collection。

代次状态如下：

```text
BUILDING
  -> CATCHING_UP
  -> READY
  -> ACTIVE
  -> RETIRED
```

物理名称默认使用：

```text
Elasticsearch: {基础索引名}_g_{generationId}
Milvus:         {基础集合名}_g_{generationId}
```

`snapshot_outbox_id` 是全量重建开始时的变更水位，`replayed_outbox_id` 是新代次已经补偿完成的水位。

### 2.4 `kb_chunk`

知识分片同时关联：

- `document_version_id`：分片属于哪个文档版本；
- `index_generation_id`：分片属于哪个索引代次。

混合检索只接受同时满足以下条件的分片：

1. 分片属于当前租户；
2. 分片属于当前活动索引代次；
3. 分片版本等于文档的 `active_version_id`；
4. 文档和分片都没有被删除。

这样即使旧版本或旧代次尚未完成异步清理，也不会进入查询结果。

### 2.5 `kb_change_outbox`

文档版本切换和文档删除与 Outbox 事件在同一个数据库事务内完成。

事件类型：

- `UPSERT`：某个文档版本已经成为活动版本；
- `DELETE`：文档已删除。

事件额外记录 `source_index_generation_id`。如果正常入库已经直接写入某个索引代次，补偿程序不会对同一代次重复构建；如果构建开始时写的是旧代次，事件会补偿到新代次。

## 三、重复训练判断

重复判断分为两层。

### 3.1 原始文件快速判断

导入文件后先读取一次字节并计算 SHA-256，然后用 `sourceKey + rawFileHash` 查找现有活动文档。

完全相同时：

1. 复用现有正文；
2. 不再执行 HTML、Markdown、TXT 或 DOCX 解析；
3. 进入任务协调器做最终构建指纹判断。

### 3.2 标准化内容和构建指纹判断

原始字节不同时才提取文本，文本会执行以下标准化：

- Unicode NFC 归一化；
- 换行统一；
- BOM 和不间断空格处理；
- 行内空白压缩；
- 多余空行压缩。

构建指纹包含：

- 标准化内容 Hash；
- 标题、分类、标签、来源等检索元数据；
- 切片器配置；
- Embedding 是否启用、模型和维度；
- Elasticsearch、Milvus 是否启用。

构建指纹与当前活动版本一致时，任务状态直接写为 `SKIPPED`，不会进行切片、Embedding 和索引写入。

原始文件 Hash 不直接进入构建指纹。这样文件仅发生换行或无意义空白变化，而标准化内容相同的情况下，仍然可以跳过重复训练。

同一文档已有 `PENDING` 或 `RUNNING` 任务时，接口复用现有任务，不会重复投递。任务超过 `CRM_KB_INGEST_STALE_TASK_MINUTES` 未更新时会标记为失败并允许重新提交；旧工作线程即使恢复，也会因为候选版本指针已经变化而无法覆盖新版本。

## 四、文档增量更新流程

```text
保存文档
  -> 锁定 kb_document
  -> 检查 PENDING/RUNNING 幂等任务
  -> 计算构建指纹
  -> 相同：创建 SKIPPED 任务
  -> 不同：创建不可变候选版本和 PENDING 任务
  -> 后台切片、Embedding、写 PG/ES/Milvus
  -> 候选版本标记 READY
  -> 事务内校验 pendingVersionId 和内容 Hash
  -> 原子切换 activeVersionId
  -> 同事务写入 Outbox
  -> 异步清理旧版本分片
```

候选版本构建期间，`activeVersionId` 不变，所以查询继续使用旧版本。

切换前会再次校验：

- 候选版本仍然是当前 `pendingVersionId`；
- 候选版本内容 Hash 仍然等于文档当前内容 Hash。

任何一项不满足，候选版本都会进入 `SUPERSEDED`，不能覆盖更新后的文档。

## 五、蓝绿全量重建

全量重建用于以下情况：

- 更换 Embedding 模型；
- 调整向量维度；
- 调整切片策略；
- 重建 Elasticsearch Mapping；
- 重建 Milvus Collection；
- 需要对全部知识执行重新索引。

执行流程：

```text
记录 Outbox 快照水位
  -> 创建 BUILDING 绿色代次
  -> 读取每个文档的 activeVersionId
  -> 写入绿色 PG 分片、ES 索引和 Milvus Collection
  -> 状态进入 CATCHING_UP
  -> 按 Outbox 顺序补偿快照后的 UPSERT/DELETE
  -> 水位追平后进入 READY
  -> 再次追平最新水位
  -> 数据库事务内切换 ACTIVE 代次
  -> 继续对账切换窗口内的事件
  -> 异步清理 RETIRED 代次
```

旧代次在新代次 `READY` 前始终保持 `ACTIVE`，因此全量重建失败不会影响线上检索。

旧物理索引只有在没有其他有效代次引用时才会删除。这个判断兼容历史多租户共用基础索引的情况，避免某个租户重建时误删其他租户仍在使用的索引。

应用重启后会扫描未完成的 `BUILDING`、`CATCHING_UP` 和 `READY` 代次。`BUILDING` 会按文档幂等重建，已经进入增量追平阶段的代次则从持久化水位继续执行，不需要开发人员手工修改任务状态。

## 六、Kafka 和最终一致性

正确性以 PostgreSQL Outbox 为准，Kafka 用于降低增量补偿延迟。

完整链路如下：

1. 文档切换事务写入 `kb_change_outbox`；
2. `KnowledgeOutboxPublisher` 批量读取未发布事件；
3. 以租户编号作为 Kafka Key，保证同租户事件顺序；
4. 消费者收到事件后，不直接相信单条消息水位，而是从数据库补齐到该事件编号；
5. `replayed_outbox_id` 保证同一索引代次重复消费时幂等；
6. 数据库定时对账继续扫描活动或待切换代次，处理 Kafka 延迟、重复消息和切换瞬间的竞态。

Kafka 默认关闭，数据库 Outbox 对账默认开启。生产环境建议开启 Kafka：

```properties
CRM_KAFKA_BOOTSTRAP_SERVERS=10.0.0.10:9092
CRM_KB_KAFKA_ENABLED=true
CRM_KB_KAFKA_TOPIC=crm-knowledge-change
CRM_KB_KAFKA_GROUP_ID=crm-knowledge-indexer
```

Kafka 发布成功但数据库更新发布状态失败时，事件可能再次发布。消费者按 Outbox 水位处理，因此允许至少一次投递，不要求消息只投递一次。

## 七、接口

所有接口均为 POST。

### 7.1 文件导入

```text
POST /api/knowledge/document/import
Content-Type: multipart/form-data
```

可选参数：

- `title`
- `sourceType`
- `category`
- `tags`
- `sourceUrl`
- `sourceKey`

### 7.2 文档入库

```text
POST /api/knowledge/document/ingest
```

请求示例：

```json
{
  "id": "339000000000000001",
  "force": false
}
```

`force=true` 会创建新版本并强制重建，适合人工验证，不参与重复训练跳过判断。

### 7.3 查询入库任务

```text
POST /api/knowledge/document/ingest/task
```

请求示例：

```json
{
  "id": "339000000000000002"
}
```

响应中增加 `documentVersionId` 和 `indexGenerationId`，可以确认任务实际构建的候选版本及写入的索引代次。

### 7.4 启动全量重建

```text
POST /api/knowledge/document/rebuild/start
```

同一租户只允许存在一个 `BUILDING`、`CATCHING_UP` 或 `READY` 的重建任务。

### 7.5 查询重建进度

```text
POST /api/knowledge/document/rebuild/detail
```

请求示例：

```json
{
  "id": "339000000000000003"
}
```

查询当前租户最近一次重建：

```text
POST /api/knowledge/document/rebuild/current
```

重点响应字段：

- `status`
- `progress`
- `documentCount`
- `completedDocumentCount`
- `snapshotOutboxId`
- `replayedOutboxId`
- `elasticsearchIndex`
- `milvusCollection`
- `errorMessage`

## 八、已有数据库迁移

应用没有加入历史表结构兼容、启动 ALTER、字段截断或双写逻辑。

已有数据库必须在后端启动前由开发人员手工执行：

```text
docs/sql/20260729-knowledge-versioning.sql
```

建议顺序：

1. 备份 PostgreSQL；
2. 停止后端服务；
3. 如果实际基础索引名不是 `crm_knowledge_chunk`，先修改迁移 SQL 中的 Elasticsearch 和 Milvus 物理名称；
4. 执行迁移 SQL；
5. 确认事务成功提交；
6. 启动后端；
7. 再执行接口回归。

示例命令：

```bash
psql -h 127.0.0.1 -p 5432 -U app_user -d crm \
  -f docs/sql/20260729-knowledge-versioning.sql
```

迁移脚本发现重复 `sourceKey` 或无法关联文档的孤立分片时会主动报错并回滚，不会自动猜测、删除或修正业务数据。开发人员需要先确认异常数据，再手工处理。

## 九、建议测试步骤

### 9.1 完全相同文件

1. 导入文件并等待任务 `SUCCESS`；
2. 再次以相同 `sourceKey` 导入同一文件；
3. 第二次任务应为 `SKIPPED`；
4. `kb_document_version` 不应新增候选版本；
5. ES 和 Milvus 不应新增分片。

### 9.2 原始字节变化但正文不变

1. 仅修改换行、BOM 或多余空格；
2. 使用相同 `sourceKey` 再次导入；
3. 文件会重新提取文本；
4. 标准化内容 Hash 相同；
5. 入库任务应为 `SKIPPED`。

### 9.3 文档内容更新

1. 修改正文并重新导入；
2. 构建期间检查 `activeVersionId` 保持旧值，`pendingVersionId` 指向新版本；
3. 构建成功后两者完成切换；
4. 查询结果只返回新活动版本；
5. 旧分片随后被异步标记删除。

### 9.4 构建期间再次更新

1. 提交较大文档开始构建；
2. 构建未完成时再次更新同一文档；
3. 先完成的旧候选版本应进入 `SUPERSEDED`；
4. 它不能覆盖最新文档；
5. 最新任务完成后才能成为 `ACTIVE`。

### 9.5 蓝绿重建

1. 调用 `/rebuild/start`；
2. 轮询 `/rebuild/detail`；
3. 在 `BUILDING` 阶段继续新增、更新和删除知识文档；
4. 检查状态依次进入 `CATCHING_UP`、`READY`、`ACTIVE`；
5. 检查 `replayedOutboxId` 已追到最新事件；
6. 验证活动代次切换前后混合检索均可用；
7. 检查旧代次异步进入清理流程。

### 9.6 失败回滚

1. 临时配置错误的 Embedding 或 Milvus 地址；
2. 启动全量重建；
3. 新代次应进入 `FAILED`；
4. 原活动代次不能变为 `RETIRED`；
5. 混合检索继续读取原活动代次。

## 十、实现位置

| 能力 | 主要实现 |
| --- | --- |
| Hash、内容标准化、构建指纹 | `KnowledgeFingerprintService` |
| 幂等任务和候选版本创建 | `KnowledgeIngestTaskCoordinator` |
| 文档版本原子切换 | `KnowledgeVersionSwitchService` |
| 活动代次管理 | `KnowledgeIndexGenerationService` |
| 蓝绿全量重建 | `KnowledgeIndexRebuildService` |
| Outbox 增量重放 | `KnowledgeChangeReplayService` |
| Kafka 发布与消费 | `KnowledgeOutboxPublisher`、`KnowledgeChangeKafkaConsumer` |
| 数据库定时对账 | `KnowledgeChangeReconciliationService` |
| 旧代次异步清理 | `KnowledgeGenerationCleanupService` |
| 代次隔离检索 | `KnowledgeDocumentService`、ES/Milvus 客户端 |
