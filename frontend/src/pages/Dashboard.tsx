import { MessageCircle, FileText, ClipboardList, Users, ArrowUpRight, ArrowDownRight } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'

export default function Dashboard() {
  const navigate = useNavigate()

  const stats = [
    { 
      label: '文档总数', 
      value: '156', 
      change: '+12%', 
      trend: 'up',
      icon: FileText,
      desc: '较上月增长' 
    },
    { 
      label: '本周对话', 
      value: '1,234', 
      change: '+25%', 
      trend: 'up',
      icon: MessageCircle,
      desc: '活跃度飙升' 
    },
    { 
      label: '知识覆盖率', 
      value: '92%', 
      change: '+4%', 
      trend: 'up',
      icon: ClipboardList,
      desc: '模板使用率' 
    },
    { 
      label: '活跃成员', 
      value: '24', 
      change: '-2%', 
      trend: 'down',
      icon: Users,
      desc: '较上周' 
    },
  ]

  const recentDocs = [
    { name: '员工手册2024.pdf', size: '12.5 MB', time: '2小时前', category: '人事', status: '已入库' },
    { name: '产品技术架构说明.docx', size: '2.3 MB', time: '昨天', category: '技术', status: '已入库' },
    { name: 'Q4季度销售数据报告.xlsx', size: '856 KB', time: '3天前', category: '销售', status: '处理中' },
    { name: '新员工入职指引.pdf', size: '4.1 MB', time: '5天前', category: '人事', status: '已入库' },
  ]

  return (
    <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">概览</h2>
        <p className="text-muted-foreground mt-1">欢迎回来，这里是您的知识库运行状态。</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat) => (
          <Card key={stat.label} className="hover:shadow-lg transition-shadow duration-300">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {stat.label}
              </CardTitle>
              <stat.icon className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{stat.value}</div>
              <p className="text-xs text-muted-foreground mt-1 flex items-center">
                <span className={stat.trend === 'up' ? 'text-emerald-500 flex items-center' : 'text-rose-500 flex items-center'}>
                  {stat.trend === 'up' ? <ArrowUpRight className="w-3 h-3 mr-1" /> : <ArrowDownRight className="w-3 h-3 mr-1" />}
                  {stat.change}
                </span>
                <span className="ml-2">{stat.desc}</span>
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Documents - Main Content */}
        <Card className="col-span-1 lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle>最近文档</CardTitle>
              <CardDescription>最近更新的知识库内容</CardDescription>
            </div>
            <Button variant="outline" size="sm" onClick={() => navigate('/documents')}>
              查看全部
            </Button>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {recentDocs.map((doc) => (
                <div
                  key={doc.name}
                  className="flex items-center justify-between p-4 rounded-lg border bg-card hover:bg-accent/50 transition-colors cursor-pointer group"
                >
                  <div className="flex items-center gap-4">
                    <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center text-primary group-hover:scale-110 transition-transform">
                      <FileText className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="font-medium group-hover:text-primary transition-colors">{doc.name}</div>
                      <div className="text-xs text-muted-foreground flex gap-3 mt-1">
                        <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-muted-foreground/30"></span>{doc.size}</span>
                        <span className="flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-muted-foreground/30"></span>{doc.time}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <Badge variant="secondary" className="font-normal">{doc.category}</Badge>
                    <Badge variant={doc.status === '已入库' ? 'default' : 'outline'} className={doc.status === '已入库' ? 'bg-emerald-500/15 text-emerald-600 hover:bg-emerald-500/25 border-0' : 'text-yellow-600 bg-yellow-50 border-yellow-200'}>
                      {doc.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Quick Actions - Sidebar */}
        <Card className="col-span-1 h-full">
          <CardHeader>
            <CardTitle>快捷操作</CardTitle>
            <CardDescription>常用功能入口</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Button 
              className="w-full justify-start h-12 text-base font-normal shadow-sm hover:shadow-md transition-all" 
              onClick={() => navigate('/chat')}
            >
              <div className="bg-primary/20 p-1.5 rounded mr-3 text-primary">
                <MessageCircle className="w-4 h-4" />
              </div>
              开始新对话
            </Button>
            <Button 
              variant="outline" 
              className="w-full justify-start h-12 text-base font-normal hover:bg-muted" 
              onClick={() => navigate('/documents')}
            >
              <div className="bg-orange-500/10 p-1.5 rounded mr-3 text-orange-600">
                <FileText className="w-4 h-4" />
              </div>
              上传新文档
            </Button>
            <Button 
              variant="outline" 
              className="w-full justify-start h-12 text-base font-normal hover:bg-muted" 
              onClick={() => navigate('/templates')}
            >
              <div className="bg-blue-500/10 p-1.5 rounded mr-3 text-blue-600">
                <ClipboardList className="w-4 h-4" />
              </div>
              创建对话模板
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
