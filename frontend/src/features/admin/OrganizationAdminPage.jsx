import { useEffect, useMemo, useState } from 'react'
import {
  Building2, Database, Edit2, Eye, KeyRound, ListTree, Menu, Plus,
  RefreshCw, RotateCcw, ShieldCheck, Trash2,
} from 'lucide-react'
import { api } from '../../api'
import { Badge, Button, Card, Drawer, Field, Modal, PageHeader } from '../../components'

const DATA_SCOPE_LABELS = {
  ALL: '全部数据',
  DEPARTMENT_AND_CHILD: '本部门及下级数据',
  DEPARTMENT: '本部门数据',
  SELF: '本人数据',
}

const DATA_SCOPE_CODE_BY_SCOPE = {
  ALL: 'data:all',
  DEPARTMENT_AND_CHILD: 'data:department_child',
  DEPARTMENT: 'data:department',
  SELF: 'data:self',
}

const SUPER_ADMIN_ROLE_CODE = 'SUPER_ADMIN'

const PERMISSION_TYPE_LABELS = {
  MENU: '菜单权限',
  ACTION: '操作权限',
  DATA: '数据权限',
}

const emptyOverview = {
  departments: [],
  users: [],
  roles: [],
  permissions: [],
}

const MODULE_DEFINITIONS = [
  { key: 'global', label: '全局权限', actionCodes: ['*'] },
  { key: 'dashboard', label: '工作台', menuCodes: ['menu.dashboard'], actionPrefixes: ['crm:dashboard:'] },
  { key: 'leads', label: '线索管理', menuCodes: ['menu.leads'], actionPrefixes: ['crm:lead:'] },
  { key: 'channels', label: '渠道管理', menuCodes: ['menu.channels'], actionPrefixes: ['crm:channel:'] },
  { key: 'customers', label: '客户管理', menuCodes: ['menu.customers'], actionPrefixes: ['crm:customer:'] },
  { key: 'opportunities', label: '商机管理', menuCodes: ['menu.opportunities'], actionPrefixes: ['crm:opportunity:'] },
  { key: 'followups', label: '跟进记录', menuCodes: ['menu.followups'] },
  { key: 'tasks', label: '销售任务', menuCodes: ['menu.tasks'] },
  { key: 'assistant', label: 'AI营销助手', menuCodes: ['menu.assistant'], actionPrefixes: ['crm:assistant:', 'crm:agent:'] },
  { key: 'knowledge', label: '知识库', menuCodes: ['menu.knowledge'], actionPrefixes: ['crm:knowledge:'] },
  { key: 'model_configs', label: '大模型配置', menuCodes: ['menu.model_configs'], actionPrefixes: ['crm:model:'] },
  { key: 'organization', label: '组织与权限', menuCodes: ['menu.organization'], actionPrefixes: ['crm:org:'] },
  {
    key: 'settings',
    label: '系统与运维',
    menuCodes: ['menu.settings'],
    actionPrefixes: ['crm:settings:', 'crm:workflow:', 'crm:observability:'],
  },
]

function buildPermissionModules(permissions) {
  const groups = MODULE_DEFINITIONS.map((module) => ({
    ...module,
    menuPermissions: [],
    actionPermissions: [],
  }))
  const otherGroup = {
    key: 'other',
    label: '其他权限',
    menuPermissions: [],
    actionPermissions: [],
  }
  const targetPermissions = permissions
    .filter((permission) => permission.permissionType === 'MENU' || permission.permissionType === 'ACTION')
    .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))

  targetPermissions.forEach((permission) => {
    const group = groups.find((item) => matchModule(permission, item)) || otherGroup
    if (permission.permissionType === 'MENU') {
      group.menuPermissions.push(permission)
    } else {
      group.actionPermissions.push(permission)
    }
  })

  return [...groups, otherGroup].filter((group) => (
    group.menuPermissions.length > 0 || group.actionPermissions.length > 0
  ))
}

function matchModule(permission, module) {
  const code = permission.code || ''
  const routePath = permission.routePath || ''
  return (module.menuCodes || []).includes(code)
    || (module.menuCodes || []).includes(`menu.${routePath}`)
    || (module.actionCodes || []).includes(code)
    || (module.actionPrefixes || []).some((prefix) => code.startsWith(prefix))
}

function uniqueCodes(codes) {
  return Array.from(new Set((codes || []).filter(Boolean)))
}

function isSuperAdminRole(role) {
  return role?.code === SUPER_ADMIN_ROLE_CODE
}

function userHasSuperAdminRole(user, roles) {
  const superAdminRole = roles.find((role) => isSuperAdminRole(role))
  return Boolean(superAdminRole && (user.roleIds || []).includes(superAdminRole.id))
}

function syncDataScopePermissionCodes(permissionCodes, dataScope, dataPermissions) {
  const dataCodes = dataPermissions.map((permission) => permission.code)
  const nextCodes = (permissionCodes || []).filter((code) => !dataCodes.includes(code))
  const dataPermissionCode = DATA_SCOPE_CODE_BY_SCOPE[dataScope]
  const existsPermission = dataPermissions.some((permission) => permission.code === dataPermissionCode)
  if (existsPermission) {
    nextCodes.push(dataPermissionCode)
  }
  return uniqueCodes(nextCodes)
}

export function OrganizationAdminPage({ can, notify }) {
  const canManage = can('crm:org:manage')
  const [tab, setTab] = useState('users')
  const [overview, setOverview] = useState(emptyOverview)
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState({ type: '', data: null })

  const load = async () => {
    setLoading(true)
    try {
      setOverview(await api.admin.overview())
    } catch (err) {
      notify(err.message || '加载后台权限数据失败', 'info')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const permissionsByType = useMemo(() => ({
    MENU: overview.permissions.filter((item) => item.permissionType === 'MENU'),
    DATA: overview.permissions.filter((item) => item.permissionType === 'DATA'),
    ACTION: overview.permissions.filter((item) => item.permissionType === 'ACTION'),
  }), [overview.permissions])

  const closeModal = () => setModal({ type: '', data: null })

  return (
    <div className="page organization-page">
      <PageHeader
        title="组织与权限"
        description="从数据库读取组织、用户、角色、菜单权限和数据权限配置"
        actions={<><Button variant="secondary" icon={RefreshCw} onClick={load}>刷新</Button>{canManage && <Button icon={Plus} onClick={() => setModal({ type: 'user', data: null })}>添加用户</Button>}</>}
      />
      <div className="org-tabs">
        <button className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>用户管理</button>
        <button className={tab === 'roles' ? 'active' : ''} onClick={() => setTab('roles')}>角色管理</button>
        <button className={tab === 'menus' ? 'active' : ''} onClick={() => setTab('menus')}>菜单权限</button>
        <button className={tab === 'data' ? 'active' : ''} onClick={() => setTab('data')}>数据权限</button>
        <button className={tab === 'departments' ? 'active' : ''} onClick={() => setTab('departments')}>组织架构</button>
      </div>

      {loading && <Card>正在加载真实权限数据…</Card>}
      {!loading && tab === 'users' && <UsersPanel overview={overview} canManage={canManage} reload={load} openModal={setModal} notify={notify} />}
      {!loading && tab === 'roles' && <RolesPanel overview={overview} canManage={canManage} reload={load} openModal={setModal} notify={notify} />}
      {!loading && tab === 'menus' && <PermissionsPanel title="菜单权限" icon={Menu} permissions={permissionsByType.MENU} type="MENU" canManage={canManage} reload={load} openModal={setModal} notify={notify} />}
      {!loading && tab === 'data' && <PermissionsPanel title="数据权限" icon={Database} permissions={permissionsByType.DATA} type="DATA" canManage={canManage} reload={load} openModal={setModal} notify={notify} />}
      {!loading && tab === 'departments' && <DepartmentsPanel departments={overview.departments} canManage={canManage} reload={load} openModal={setModal} notify={notify} />}

      <DepartmentModal
        open={modal.type === 'department'}
        data={modal.data}
        departments={overview.departments}
        onClose={closeModal}
        reload={load}
      />
      <PermissionModal open={modal.type === 'permission'} data={modal.data} onClose={closeModal} reload={load} />
      <RoleModal open={modal.type === 'role'} data={modal.data} overview={overview} onClose={closeModal} reload={load} notify={notify} />
      <UserModal open={modal.type === 'user'} data={modal.data} overview={overview} onClose={closeModal} reload={load} />
    </div>
  )
}

function UsersPanel({ overview, canManage, reload, openModal, notify }) {
  const resetPassword = async (id) => {
    const response = await api.admin.resetUserPassword(id)
    notify(`临时密码：${response.temporaryPassword}`, 'info')
  }

  return (
    <Card className="table-card org-table-card">
      <div className="table-toolbar">
        <div className="card-heading"><div><h2>用户管理</h2><p>共 {overview.users.length} 个用户</p></div></div>
        {canManage && <Button icon={Plus} onClick={() => openModal({ type: 'user', data: null })}>新建用户</Button>}
      </div>
      <div className="data-table-wrap">
        <table className="data-table">
          <thead><tr><th>用户</th><th>部门</th><th>角色</th><th>数据权限</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{overview.users.map((user) => (
            <tr key={user.id}>
              <td><div className="user-cell"><span className="avatar">{(user.displayName || user.username).slice(0, 1)}</span><div><strong>{user.displayName || user.username}</strong><small>{user.username}</small></div></div></td>
              <td>{user.departmentName || '未分配'}</td>
              <td>{user.roleNames.length ? user.roleNames.map((role) => <Badge key={role} tone="info">{role}</Badge>) : '未分配'}</td>
              <td><span className="scope-cell"><Eye size={14} />{DATA_SCOPE_LABELS[user.dataScope] || user.dataScope}</span></td>
              <td><Badge dot tone={user.enabled ? 'success' : 'danger'}>{user.enabled ? '正常' : '停用'}</Badge></td>
              <td>
                <button className="icon-button" disabled={!canManage} onClick={() => openModal({ type: 'user', data: user })}><Edit2 size={17} /></button>
                <button className="icon-button" disabled={!canManage} onClick={() => resetPassword(user.id)}><RotateCcw size={17} /></button>
                <button className="icon-button" disabled={!canManage || userHasSuperAdminRole(user, overview.roles)} onClick={async () => { await api.admin.updateUserStatus(user.id, !user.enabled); reload() }}><ShieldCheck size={17} /></button>
              </td>
            </tr>
          ))}</tbody>
        </table>
        {!overview.users.length && <EmptyState text="暂无用户数据" />}
      </div>
    </Card>
  )
}

function RolesPanel({ overview, canManage, reload, openModal, notify }) {
  const remove = async (role) => {
    await api.admin.deleteRole(role.id)
    notify('角色已删除')
    reload()
  }

  return (
    <Card className="role-definitions">
      <div className="card-heading">
        <div><h2>角色管理</h2><p>角色绑定菜单权限、操作权限和数据权限</p></div>
        {canManage && <Button icon={Plus} onClick={() => openModal({ type: 'role', data: null })}>新建角色</Button>}
      </div>
      <div className="role-grid">
        {overview.roles.map((role) => (
          <div className="role-card" key={role.id}>
            <span className="role-icon"><KeyRound size={18} /></span>
            <div>
              <h3>{role.name}</h3>
              <p>{role.code}</p>
              <div><Badge>{role.userCount} 位用户</Badge><Badge tone="info">{DATA_SCOPE_LABELS[role.dataScope] || role.dataScope}</Badge>{isSuperAdminRole(role) && <Badge tone="danger">系统唯一</Badge>}</div>
              <div><Badge>菜单 {role.menuPermissionCodes.length}</Badge><Badge>数据 {role.dataPermissionCodes.length}</Badge><Badge>总权限 {role.permissionCodes.length}</Badge></div>
            </div>
            <button className="icon-button" disabled={!canManage || isSuperAdminRole(role)} onClick={() => openModal({ type: 'role', data: role })}><Edit2 size={17} /></button>
            <button className="icon-button" disabled={!canManage || role.userCount > 0 || isSuperAdminRole(role)} onClick={() => remove(role)}><Trash2 size={17} /></button>
          </div>
        ))}
      </div>
      {!overview.roles.length && <EmptyState text="暂无角色数据" />}
    </Card>
  )
}

function PermissionsPanel({ title, icon: Icon, permissions, type, canManage, reload, openModal, notify }) {
  const toggle = async (permission) => {
    await api.admin.updatePermissionStatus(permission.id, !permission.enabled)
    notify('权限状态已更新')
    reload()
  }

  const remove = async (permission) => {
    await api.admin.deletePermission(permission.id)
    notify('权限已删除')
    reload()
  }

  return (
    <Card className="table-card org-table-card">
      <div className="table-toolbar">
        <div className="card-heading"><div><h2>{title}</h2><p>共 {permissions.length} 条，来源于 sys_permission</p></div></div>
        {canManage && <Button icon={Plus} onClick={() => openModal({ type: 'permission', data: { permissionType: type } })}>新建{PERMISSION_TYPE_LABELS[type]}</Button>}
      </div>
      <div className="data-table-wrap">
        <table className="data-table">
          <thead><tr><th>权限名称</th><th>系统编码</th><th>路由</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{permissions.map((permission) => (
            <tr key={permission.id}>
              <td><strong><Icon size={15} /> {permission.name}</strong></td>
              <td>{permission.code}</td>
              <td>{permission.routePath || '-'}</td>
              <td>{permission.sortNo}</td>
              <td><Badge dot tone={permission.enabled ? 'success' : 'danger'}>{permission.enabled ? '启用' : '停用'}</Badge></td>
              <td>
                <button className="icon-button" disabled={!canManage} onClick={() => openModal({ type: 'permission', data: permission })}><Edit2 size={17} /></button>
                <button className="icon-button" disabled={!canManage} onClick={() => toggle(permission)}><ShieldCheck size={17} /></button>
                <button className="icon-button" disabled={!canManage} onClick={() => remove(permission)}><Trash2 size={17} /></button>
              </td>
            </tr>
          ))}</tbody>
        </table>
        {!permissions.length && <EmptyState text={`暂无${title}`} />}
      </div>
    </Card>
  )
}

function DepartmentsPanel({ departments, canManage, reload, openModal, notify }) {
  const remove = async (department) => {
    await api.admin.deleteDepartment(department.id)
    notify('部门已删除')
    reload()
  }

  return (
    <Card className="table-card org-table-card">
      <div className="table-toolbar">
        <div className="card-heading"><div><h2>组织架构</h2><p>共 {departments.length} 个部门</p></div></div>
        {canManage && <Button icon={Plus} onClick={() => openModal({ type: 'department', data: null })}>新建部门</Button>}
      </div>
      <div className="data-table-wrap">
        <table className="data-table">
          <thead><tr><th>部门</th><th>系统编码</th><th>上级部门</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{departments.map((department) => (
            <tr key={department.id}>
              <td><strong><Building2 size={15} /> {department.name}</strong></td>
              <td>{department.code}</td>
              <td>{departments.find((item) => item.id === department.parentId)?.name || '-'}</td>
              <td>{department.sortNo}</td>
              <td><Badge dot tone={department.enabled ? 'success' : 'danger'}>{department.enabled ? '启用' : '停用'}</Badge></td>
              <td>
                <button className="icon-button" disabled={!canManage} onClick={() => openModal({ type: 'department', data: department })}><Edit2 size={17} /></button>
                <button className="icon-button" disabled={!canManage} onClick={() => remove(department)}><Trash2 size={17} /></button>
              </td>
            </tr>
          ))}</tbody>
        </table>
        {!departments.length && <EmptyState text="暂无组织部门" />}
      </div>
    </Card>
  )
}

function DepartmentModal({ open, data, departments = [], onClose, reload }) {
  const [form, setForm] = useState(data || { code: '', name: '', parentId: '', sortNo: 0, enabled: true })
  useEffect(() => setForm(data || { code: '', name: '', parentId: '', sortNo: 0, enabled: true }), [data, open])
  const save = async () => {
    await api.admin.saveDepartment({
      ...form,
      parentId: form.parentId || null,
      sortNo: Number(form.sortNo) || 0,
    })
    onClose()
    reload()
  }
  return <Modal open={open} title={data?.id ? '编辑部门' : '新建部门'} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>取消</Button><Button onClick={save}>保存</Button></>}>
    <Field label="部门名称" required><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
    <Field label="上级部门">
      <select value={form.parentId || ''} onChange={(e) => setForm({ ...form, parentId: e.target.value })}>
        <option value="">作为顶级部门</option>
        {departments.filter((department) => department.id !== data?.id).map((department) => (
          <option value={department.id} key={department.id}>{department.name}</option>
        ))}
      </select>
    </Field>
    <Field label="排序"><input type="number" value={form.sortNo || 0} onChange={(e) => setForm({ ...form, sortNo: e.target.value })} /></Field>
  </Modal>
}

function PermissionModal({ open, data, onClose, reload }) {
  const [form, setForm] = useState(data || { code: '', name: '', permissionType: 'MENU', routePath: '', sortNo: 0, enabled: true })
  useEffect(() => setForm(data || { code: '', name: '', permissionType: 'MENU', routePath: '', sortNo: 0, enabled: true }), [data, open])
  const save = async () => {
    await api.admin.savePermission({ ...form, sortNo: Number(form.sortNo) || 0 })
    onClose()
    reload()
  }
  return <Modal open={open} title={data?.id ? '编辑权限' : '新建权限'} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>取消</Button><Button onClick={save}>保存</Button></>}>
    <Field label="权限名称" required><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
    <Field label="权限类型" required><select value={form.permissionType} onChange={(e) => setForm({ ...form, permissionType: e.target.value })}><option value="MENU">菜单权限</option><option value="ACTION">操作权限</option><option value="DATA">数据权限</option></select></Field>
    <Field label="路由路径" hint="菜单权限可填写前端路由，权限编码会自动生成">
      <input value={form.routePath || ''} onChange={(e) => setForm({ ...form, routePath: e.target.value })} />
    </Field>
    <Field label="排序"><input type="number" value={form.sortNo || 0} onChange={(e) => setForm({ ...form, sortNo: e.target.value })} /></Field>
  </Modal>
}

function RoleModal({ open, data, overview, onClose, reload, notify }) {
  const [form, setForm] = useState(data || { code: '', name: '', dataScope: 'SELF', enabled: true, permissionCodes: [] })
  useEffect(() => setForm(data || { code: '', name: '', dataScope: 'SELF', enabled: true, permissionCodes: [] }), [data, open])

  const permissionModules = useMemo(() => buildPermissionModules(overview.permissions), [overview.permissions])
  const menuActionCodes = useMemo(() => permissionModules.flatMap((group) => (
    [...group.menuPermissions, ...group.actionPermissions].map((permission) => permission.code)
  )), [permissionModules])
  const selectedMenuActionCount = (form.permissionCodes || []).filter((code) => menuActionCodes.includes(code)).length
  const dataPermissions = useMemo(
    () => overview.permissions.filter((permission) => permission.permissionType === 'DATA'),
    [overview.permissions],
  )

  const togglePermission = (code) => {
    const exists = (form.permissionCodes || []).includes(code)
    const nextCodes = exists
      ? form.permissionCodes.filter((item) => item !== code)
      : [...(form.permissionCodes || []), code]
    setForm({ ...form, permissionCodes: uniqueCodes(nextCodes) })
  }

  const toggleModule = (group, checked) => {
    const groupCodes = [...group.menuPermissions, ...group.actionPermissions]
      .filter((permission) => permission.enabled)
      .map((permission) => permission.code)
    const currentCodes = form.permissionCodes || []
    const nextCodes = checked
      ? uniqueCodes([...currentCodes, ...groupCodes])
      : currentCodes.filter((code) => !groupCodes.includes(code))
    setForm({ ...form, permissionCodes: nextCodes })
  }

  const changeDataScope = (dataScope) => {
    setForm({
      ...form,
      dataScope,
      permissionCodes: syncDataScopePermissionCodes(form.permissionCodes, dataScope, dataPermissions),
    })
  }

  const save = async () => {
    try {
      await api.admin.saveRole({
        ...form,
        permissionCodes: syncDataScopePermissionCodes(form.permissionCodes, form.dataScope, dataPermissions),
      })
      notify('角色权限已保存')
      onClose()
      reload()
    } catch (err) {
      notify(err.message || '角色保存失败', 'info')
    }
  }

  const footer = (
    <>
      <Button variant="secondary" onClick={onClose}>取消</Button>
      <Button onClick={save}>保存</Button>
    </>
  )

  return (
    <Drawer open={open} size="wide" title={data?.id ? '编辑角色' : '新建角色'} onClose={onClose} footer={footer}>
      <div className="role-drawer-layout">
        <aside className="role-config-column">
          <Card className="role-basic-card">
            <div className="card-heading">
              <div>
                <h2>角色信息</h2>
              </div>
            </div>
            <Field label="角色名称" required>
              <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </Field>
          </Card>

          <Card className="role-scope-card">
            <div className="card-heading">
              <div>
                <h2><Database size={17} />数据权限</h2>
              </div>
              <Badge tone="info">{DATA_SCOPE_LABELS[form.dataScope] || '未选择'}</Badge>
            </div>
            <div className="data-scope-grid">
              {Object.entries(DATA_SCOPE_LABELS).map(([value, label]) => (
                <label className={`data-scope-option ${form.dataScope === value ? 'active' : ''}`} key={value}>
                  <input
                    type="radio"
                    name="dataScope"
                    checked={form.dataScope === value}
                    onChange={() => changeDataScope(value)}
                  />
                  <span>
                    <b>{label}</b>
                  </span>
                </label>
              ))}
            </div>
          </Card>
        </aside>

        <section className="role-permission-column">
          <div className="module-permission-head">
            <div>
              <h3><Menu size={17} />菜单与功能权限</h3>
            </div>
            <Badge tone="info">已选 {selectedMenuActionCount} 项</Badge>
          </div>

          <div className="module-permission-grid">
            {permissionModules.map((group) => (
              <PermissionModuleCard
                group={group}
                selectedCodes={form.permissionCodes || []}
                onToggleModule={toggleModule}
                onTogglePermission={togglePermission}
                key={group.key}
              />
            ))}
          </div>
        </section>
      </div>
    </Drawer>
  )
}

function PermissionModuleCard({ group, selectedCodes, onToggleModule, onTogglePermission }) {
  const permissions = [...group.menuPermissions, ...group.actionPermissions]
  const enabledCodes = permissions.filter((permission) => permission.enabled).map((permission) => permission.code)
  const checkedCount = enabledCodes.filter((code) => selectedCodes.includes(code)).length
  const allChecked = enabledCodes.length > 0 && checkedCount === enabledCodes.length

  return (
    <Card className={`module-permission-card ${allChecked ? 'checked' : ''}`}>
      <div className="module-check-row">
        <label className="module-check-line">
          <input
            type="checkbox"
            checked={allChecked}
            disabled={enabledCodes.length === 0}
            onChange={(event) => onToggleModule(group, event.target.checked)}
          />
          <span>
            <b>{group.label}</b>
          </span>
        </label>
        <Badge tone={checkedCount ? 'info' : 'neutral'}>{checkedCount}/{enabledCodes.length}</Badge>
      </div>

      <PermissionBlock
        title="菜单入口"
        permissions={group.menuPermissions}
        selectedCodes={selectedCodes}
        onTogglePermission={onTogglePermission}
      />
      <PermissionBlock
        title="操作功能"
        permissions={group.actionPermissions}
        selectedCodes={selectedCodes}
        onTogglePermission={onTogglePermission}
      />
    </Card>
  )
}

function PermissionBlock({ title, permissions, selectedCodes, onTogglePermission }) {
  if (!permissions.length) {
    return null
  }
  return (
    <div className="permission-block">
      <span>{title}</span>
      <div>
        {permissions.map((permission) => (
          <label className={`permission-check ${!permission.enabled ? 'disabled' : ''}`} key={permission.code}>
            <input
              type="checkbox"
              checked={selectedCodes.includes(permission.code)}
              disabled={!permission.enabled}
              onChange={() => onTogglePermission(permission.code)}
            />
            <span>
              <b>{permission.name}</b>
            </span>
          </label>
        ))}
      </div>
    </div>
  )
}

function UserModal({ open, data, overview, onClose, reload }) {
  const [form, setForm] = useState(data || { username: '', displayName: '', departmentId: '', password: '', enabled: true, roleIds: [] })
  useEffect(() => setForm(data || { username: '', displayName: '', departmentId: '', password: '', enabled: true, roleIds: [] }), [data, open])
  const superAdminRole = overview.roles.find((role) => isSuperAdminRole(role))
  const superAdminUser = superAdminRole
    ? overview.users.find((user) => (user.roleIds || []).includes(superAdminRole.id))
    : null
  const toggleRole = (id) => {
    const role = overview.roles.find((item) => item.id === id)
    const exists = (form.roleIds || []).includes(id)
    if (isSuperAdminRole(role) && superAdminUser?.id && superAdminUser.id !== data?.id) {
      return
    }
    if (isSuperAdminRole(role) && superAdminUser?.id === data?.id && exists) {
      return
    }
    setForm({ ...form, roleIds: exists ? form.roleIds.filter((item) => item !== id) : [...(form.roleIds || []), id] })
  }
  const save = async () => {
    await api.admin.saveUser({ ...form, departmentId: form.departmentId || null })
    onClose()
    reload()
  }
  return <Modal open={open} title={data?.id ? '编辑用户' : '新建用户'} onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>取消</Button><Button onClick={save}>保存</Button></>}>
    <Field label="用户名" required><input disabled={Boolean(data?.id)} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></Field>
    <Field label="显示名称"><input value={form.displayName || ''} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></Field>
    <Field label="所属部门"><select value={form.departmentId || ''} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}><option value="">不分配部门</option>{overview.departments.map((department) => <option value={department.id} key={department.id}>{department.name}</option>)}</select></Field>
    <Field label={data?.id ? '修改密码' : '初始密码'} hint={data?.id ? '不填写则不修改密码' : '至少8位'}><input type="password" value={form.password || ''} onChange={(e) => setForm({ ...form, password: e.target.value })} /></Field>
    <div className="role-grid">
      {overview.roles.map((role) => (
        <label className={`role-card ${isSuperAdminRole(role) ? 'locked' : ''}`} key={role.id}>
          <input
            type="checkbox"
            checked={(form.roleIds || []).includes(role.id)}
            disabled={isSuperAdminRole(role) && Boolean(superAdminUser?.id)}
            onChange={() => toggleRole(role.id)}
          />
          <span><b>{role.name}</b><small>{isSuperAdminRole(role) ? '系统唯一超级管理员角色' : role.code}</small></span>
        </label>
      ))}
    </div>
    {superAdminUser?.id && <div className="form-hint-line">超级管理员角色已绑定到唯一用户，不能再分配给其他用户。</div>}
  </Modal>
}

function EmptyState({ text }) {
  return <div className="empty-table"><ListTree size={26} /><b>{text}</b><span>当前没有可展示的数据库记录</span></div>
}
