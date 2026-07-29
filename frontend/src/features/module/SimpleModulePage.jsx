import { Bot, CheckCircle2, FileText, MessageCircleMore, Plus, Sparkles } from 'lucide-react'
import { Button, Card, PageHeader } from '../../components'

const moduleConfig = {
  followups: {
    title: '跟进记录',
    desc: '集中查看电话、邮件、会议和拜访记录',
    icon: MessageCircleMore,
  },
  tasks: {
    title: '销售任务',
    desc: '管理个人和团队任务，确保关键动作按时完成',
    icon: CheckCircle2,
  },
  assistant: {
    title: 'AI 智能体助手',
    desc: '围绕客户、线索与商机数据进行智能问答与内容生成',
    icon: Bot,
  },
  knowledge: {
    title: '知识库',
    desc: '统一管理产品、行业、案例和销售方法论',
    icon: FileText,
  },
}

export function SimpleModulePage({ type, notify }) {
  const config = moduleConfig[type] || moduleConfig.followups
  const Icon = config.icon
  return (
    <div className="page simple-module">
      <PageHeader
        title={config.title}
        description={config.desc}
        actions={<Button icon={Plus} onClick={() => notify(`${config.title}后续接入真实接口`)}>新建</Button>}
      />
      <Card className="module-main-card">
        <div className="module-illustration"><Icon size={32} /><Sparkles size={20} /></div>
        <h2>{config.title}待接入</h2>
        <p>当前模块已纳入统一导航、权限和主题体系，后续补齐真实业务接口后再展示数据。</p>
      </Card>
    </div>
  )
}
