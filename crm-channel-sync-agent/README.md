# CRM企微渠道同步服务

该服务部署在Linux服务器，通过服务端虚拟浏览器定时导出企微智能表格，并把新增或变化的数据同步到CRM公海池。

日常运行不需要打开本机浏览器。企微没有开放智能表格读取接口，因此首次部署或登录失效后仍需通过noVNC扫码一次，登录状态会持久化到 `data/browser-profile`。

## 同步流程

1. 定时打开已配置的企微智能表格。
2. 自动导出本地Excel文件。
3. 计算Excel业务内容哈希，无变化时跳过上传。
4. 使用内部令牌调用CRM渠道导入接口。
5. CRM根据渠道配置关联产品，并按手机号、邮箱、公司和联系人进行业务去重。
6. 上传失败时不更新本地状态，下次任务继续重试。

## 一、CRM配置

在Nacos的 `crm.yaml` 中配置：

```yaml
crm:
  channel:
    upload-dir: /app/uploads/channel
    sync:
      internal-token: <渠道同步内部令牌>
      tenant-id: <租户编号>
      operator-id: <操作人编号>
```

重启CRM后端使配置生效。同步服务 `.env` 中的 `CRM_CHANNEL_SYNC_INTERNAL_TOKEN` 必须与 `internal-token` 完全一致。

内部导入接口：

```text
POST /api/internal/channel/source/import-excel
X-CRM-Channel-Sync-Token: <渠道同步内部令牌>
```

CRM渠道场景中必须提前创建对应的企微表格来源，并关联意向产品。来源链接中的文档标识和 `tab` 必须与 `config.json` 一致。

## 二、服务配置

```bash
cp config.example.json config.json
cp .env.example .env
```

修改 `config.json`：

- `sources`：需要同步的企微智能表格，可配置多张。
- `schedules`：Cron定时表达式列表。
- `headless`：保持 `false`，浏览器运行在服务器虚拟桌面中。
- `runOnStart`：是否在服务启动后立即执行一次。

修改 `.env`：

- `VNC_PASSWORD`：首次登录企微时使用的noVNC密码。
- `CRM_CHANNEL_SYNC_INTERNAL_TOKEN`：CRM内部同步令牌。
- `CRM_API_BASE_URL`：CRM访问地址。

以后新增表格只需在CRM新增渠道来源并在 `config.json.sources` 增加一项，不需要修改代码。

## 三、构建和启动

```bash
docker compose build
docker compose up -d
docker compose logs -f --tail=200 crm-channel-sync
```

## 四、首次登录企微

建立SSH隧道：

```bash
ssh -L 6081:127.0.0.1:6081 root@服务器地址
```

浏览器打开：

```text
http://127.0.0.1:6081/vnc.html
```

执行登录命令：

```bash
docker compose exec crm-channel-sync npm run login
```

在noVNC页面完成企微扫码，回到终端按回车保存登录状态。

## 五、验证

```bash
docker compose exec -T crm-channel-sync npm run doctor
docker compose exec -T crm-channel-sync npm run run
docker compose logs --tail=200 crm-channel-sync
```

进入CRM检查：

1. 渠道场景卡片的最近同步时间已更新。
2. 同步日志中的读取、新增、更新和跳过数量正确。
3. 新数据进入公海池并关联正确的意向产品。
4. 重复执行同步不会产生重复数据。

## 常用命令

```bash
# 查看配置和同步状态
docker compose exec -T crm-channel-sync npm run doctor

# 立即同步全部启用的表格
docker compose exec -T crm-channel-sync npm run run

# 重新扫码登录企微
docker compose exec crm-channel-sync npm run login

# 查看日志
docker compose logs -f --tail=200 crm-channel-sync

# 更新配置后重启
docker compose restart crm-channel-sync
```

## 数据备份

必须备份以下内容：

```text
.env
config.json
data/state.json
data/browser-profile/
```

`data/browser-profile` 是企微登录状态，`data/state.json` 是各表格最近一次成功同步的内容哈希。不要在服务运行期间同时启动第二个实例使用同一份 `data` 目录。
