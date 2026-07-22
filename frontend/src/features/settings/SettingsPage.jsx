import { useRef, useState } from 'react'
import {
  Bell,
  Building2,
  Check,
  CheckCircle2,
  Palette,
  Settings2,
  ShieldCheck,
  Upload,
} from 'lucide-react'
import { APP_NAME } from '../../config/appConfig'
import { Badge, Button, Card, Field, PageHeader } from '../../components'

export function SettingsPage({ preferences, onUpdate, notify }) {
  const [draft, setDraft] = useState(preferences)
  const [saved, setSaved] = useState(true)
  const fileRef = useRef(null)
  const accents = ['#f45b0b', '#2563eb', '#7c3aed', '#0891b2', '#16a34a']

  const update = (patch) => {
    const next = { ...draft, ...patch }
    setDraft(next)
    onUpdate(next)
    setSaved(false)
  }

  const uploadLogo = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      notify('请选择图片格式的 Logo', 'info')
      return
    }
    if (file.size > 1024 * 1024) {
      notify('Logo 图片请控制在 1MB 以内', 'info')
      return
    }
    const reader = new FileReader()
    reader.onload = () => update({ logo: reader.result })
    reader.readAsDataURL(file)
  }

  const save = () => {
    onUpdate(draft)
    setSaved(true)
    notify('品牌与外观设置已保存')
  }

  return (
    <div className="page settings-page">
      <PageHeader
        title="系统设置"
        description="管理品牌、外观、登录安全和系统偏好"
        actions={<Button icon={Check} disabled={saved} onClick={save}>{saved ? '已保存' : '保存更改'}</Button>}
      />
      <div className="settings-layout">
        <aside className="settings-nav">
          <button className="active"><Palette size={17} />品牌与外观</button>
          <button><ShieldCheck size={17} />登录与安全</button>
          <button><Bell size={17} />消息通知</button>
          <button><Settings2 size={17} />系统参数</button>
        </aside>
        <div className="settings-content">
          <Card className="settings-section">
            <div className="settings-section-head">
              <div>
                <h2>企业品牌</h2>
                <p>自定义 Logo 会应用于登录页、侧栏和移动端导航。</p>
              </div>
              <Badge tone="success">已启用</Badge>
            </div>
            <div className="logo-setting">
              <div className="logo-preview">{draft.logo ? <img src={draft.logo} alt="当前 Logo" /> : <Building2 size={31} />}</div>
              <div>
                <b>企业 Logo</b>
                <p>建议使用透明背景 PNG 或 SVG，尺寸不小于 128 × 128px，最大 1MB。</p>
                <div>
                  <Button variant="secondary" icon={Upload} onClick={() => fileRef.current?.click()}>上传 Logo</Button>
                  {draft.logo && <Button variant="ghost" onClick={() => update({ logo: '' })}>恢复默认</Button>}
                </div>
                <input ref={fileRef} hidden type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" onChange={uploadLogo} />
              </div>
            </div>
            <Field label="系统名称">
              <input defaultValue={APP_NAME} disabled />
            </Field>
          </Card>

          <Card className="settings-section">
            <div className="settings-section-head">
              <div>
                <h2>界面主题</h2>
                <p>主题选项会即时预览，保存后应用于当前浏览器。</p>
              </div>
            </div>
            <div className="theme-options">
              {[
                ['light', '浅色模式'],
                ['dark', '深色模式'],
                ['system', '跟随系统'],
              ].map(([key, label]) => (
                <button className={draft.theme === key ? 'active' : ''} onClick={() => update({ theme: key })} key={key}>
                  <span className={`theme-thumb ${key}`}><i /><b /><em /></span>
                  <div>
                    <b>{label}</b>
                    <small>{key === 'light' ? '明亮清晰' : key === 'dark' ? '低光舒适' : '自动切换'}</small>
                  </div>
                  {draft.theme === key && <Check size={16} />}
                </button>
              ))}
            </div>
          </Card>

          <Card className="settings-section">
            <div className="settings-section-head">
              <div>
                <h2>品牌强调色</h2>
                <p>用于主要按钮、选中状态和 AI 能力标识。</p>
              </div>
            </div>
            <div className="accent-options">
              {accents.map((color) => (
                <button className={draft.accent === color ? 'active' : ''} style={{ background: color }} key={color} onClick={() => update({ accent: color })}>
                  {draft.accent === color && <Check size={17} />}
                </button>
              ))}
              <label>
                <span>自定义</span>
                <input type="color" value={draft.accent} onChange={(event) => update({ accent: event.target.value })} />
              </label>
            </div>
          </Card>

          <Card className="settings-section">
            <div className="settings-section-head">
              <div>
                <h2>内容密度</h2>
                <p>根据屏幕尺寸和工作习惯调整信息密度。</p>
              </div>
            </div>
            <div className="density-options">
              {[
                ['comfortable', '舒适', '更大的留白，适合日常办公'],
                ['compact', '紧凑', '同屏展示更多表格数据'],
              ].map(([key, label, desc]) => (
                <label className={draft.density === key ? 'active' : ''} key={key}>
                  <input type="radio" checked={draft.density === key} onChange={() => update({ density: key })} />
                  <span><b>{label}</b><small>{desc}</small></span>
                </label>
              ))}
            </div>
          </Card>

          <div className="settings-save-bar">
            <span>{saved ? <><CheckCircle2 size={16} />所有更改均已保存</> : '您有未保存的外观更改'}</span>
            <Button onClick={save} disabled={saved}>保存设置</Button>
          </div>
        </div>
      </div>
    </div>
  )
}
