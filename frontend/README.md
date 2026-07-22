# 智能营销管理系统前端

## 目录约定

- `src/api`：接口统一入口，页面禁止直接写 `fetch` 和接口路径。
- `src/models`：请求参数模型。
- `src/router`：路由、菜单和权限映射。
- `src/layouts`：应用布局、登录布局等页面骨架。
- `src/components`：通用 UI 组件。
- `src/features`：业务功能页或功能组件。
- `src/config`：应用常量和环境配置。
- `src/store`：本地状态与缓存封装。
- `src/hooks`：通用 React hooks。

## 后台地址配置

开发环境默认通过 Vite 代理转发到：

```text
http://localhost:8080
```

可在 `frontend/.env.development` 调整：

```text
VITE_DEV_PROXY_TARGET=http://localhost:8080
```

生产环境优先读取运行时配置 `public/config.js`：

```js
window.__CRM_CONFIG__ = {
  API_BASE_URL: '',
  API_TIMEOUT: 30000,
  APP_ENV: '',
}
```

`API_BASE_URL` 为空时使用同源 `/api`，部署后可直接改 `dist/config.js`，不需要重新打包。
