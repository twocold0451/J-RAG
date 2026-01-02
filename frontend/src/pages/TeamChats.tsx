import { useState, useEffect } from 'react'
import { MessageCircle, Search, Eye, Filter, AlertTriangle, User, Calendar, FileText, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { ConversationResponse, ChatMessageDto, TemplateDto } from '@/types'
import ReactMarkdown from 'react-markdown'

interface TeamChatItem extends ConversationResponse {
  messageCount?: number
}

interface ChatMessage {
  id: number
  role: string
  content: string
  createdAt?: string
}

export default function TeamChats() {
  const { showToast } = useToast()
  const [conversations, setConversations] = useState<TeamChatItem[]>([])
  const [templates, setTemplates] = useState<TemplateDto[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [userFilter, setUserFilter] = useState('all')
  const [templateFilter, setTemplateFilter] = useState('all')
  const [selectedChat, setSelectedChat] = useState<TeamChatItem | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false)

  // 加载数据
  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setIsLoading(true)
    try {
      const [convsData, templatesData] = await Promise.all([
        api.getTeamConversations(),
        api.getTemplates()
      ])
      setConversations(convsData)
      setTemplates(templatesData)
    } catch (err: any) {
      console.error('Failed to load data:', err)
      showToast('加载数据失败', 'error')
    } finally {
      setIsLoading(false)
    }
  }

  const loadChatMessages = async (conversationId: number) => {
    setIsDetailLoading(true)
    try {
      const msgsData = await api.getTeamConversationMessages(conversationId)
      setMessages(msgsData.map((msg: ChatMessageDto) => ({
        id: msg.id,
        role: msg.role.toLowerCase(),
        content: msg.content,
        createdAt: msg.createdAt
      })))
    } catch (err: any) {
      console.error('Failed to load messages:', err)
      showToast('加载消息失败', 'error')
    } finally {
      setIsDetailLoading(false)
    }
  }

  const openChatDetail = (chat: TeamChatItem) => {
    setSelectedChat(chat)
    setIsDetailOpen(true)
    loadChatMessages(chat.id)
  }

  const getUserNames = () => {
    const names = new Set(conversations.map(c => c.username).filter(Boolean))
    return Array.from(names) as string[]
  }

  const filteredChats = conversations.filter(chat => {
    const matchesSearch = (chat.title || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
                          (chat.username || '').toLowerCase().includes(searchQuery.toLowerCase())
    const matchesUser = userFilter === 'all' || chat.username === userFilter
    const matchesTemplate = templateFilter === 'all' ||
      (templateFilter === '未分类' && !chat.templateId) ||
      templates.find(t => t.id === chat.templateId)?.name === templateFilter
    return matchesSearch && matchesUser && matchesTemplate
  })

  const formatTime = (dateStr?: string) => {
    if (!dateStr) return '未知'
    const date = new Date(dateStr)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const minutes = Math.floor(diff / 60000)
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)

    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days < 7) return `${days}天前`
    return date.toLocaleDateString('zh-CN')
  }

  const getTemplateName = (templateId?: number) => {
    if (!templateId) return '未分类'
    return templates.find(t => t.id === templateId)?.name || '未知模板'
  }

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">团队对话</h1>
          <p className="text-muted-foreground mt-1">
            监控和分析团队成员的 AI 使用情况
          </p>
        </div>
        <Badge variant="outline" className="bg-orange-50 text-orange-600 border-orange-200 px-3 py-1">
          <AlertTriangle className="w-3.5 h-3.5 mr-1.5" />
          管理员可见
        </Badge>
      </div>

      {/* Admin Warning Banner */}
      <div className="bg-orange-50/50 border border-orange-100 rounded-xl p-4 flex items-start gap-3">
        <div className="p-2 bg-orange-100 rounded-lg text-orange-600 shrink-0">
          <Eye className="w-5 h-5" />
        </div>
        <div>
          <h4 className="text-sm font-semibold text-orange-900">管理员视图</h4>
          <p className="text-sm text-orange-700/80 mt-1 leading-relaxed">
            您可以查看团队成员的公开对话，用于监督知识库回答质量和优化 Prompt。
            <br />请注意保护员工隐私，私密对话内容不会在此显示。
          </p>
        </div>
      </div>

      {/* Filters */}
      <Card className="shadow-sm border-border/50">
        <CardContent className="p-4">
          <div className="flex flex-col md:flex-row gap-4">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="搜索对话标题或用户..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 bg-muted/30 focus:bg-background transition-colors"
              />
            </div>
            <div className="flex gap-3">
              <Select value={userFilter} onValueChange={setUserFilter}>
                <SelectTrigger className="w-[140px]">
                  <User className="w-4 h-4 mr-2 text-muted-foreground" />
                  <SelectValue placeholder="全部用户" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部用户</SelectItem>
                  {getUserNames().map(name => (
                    <SelectItem key={name} value={name}>{name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={templateFilter} onValueChange={setTemplateFilter}>
                <SelectTrigger className="w-[160px]">
                  <Filter className="w-4 h-4 mr-2 text-muted-foreground" />
                  <SelectValue placeholder="全部模板" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部模板</SelectItem>
                  <SelectItem value="未分类">未分类</SelectItem>
                  {templates.map(template => (
                    <SelectItem key={template.id} value={template.name}>{template.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Chat List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
        </div>
      ) : filteredChats.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          暂无团队对话
        </div>
      ) : (
        <div className="grid gap-4">
          {filteredChats.map((chat) => (
            <div
              key={chat.id}
              className="group flex items-center justify-between p-4 rounded-xl border bg-card hover:border-primary/30 hover:shadow-md transition-all cursor-pointer"
              onClick={() => openChatDetail(chat)}
            >
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center text-primary group-hover:scale-105 transition-transform">
                  <MessageCircle className="w-6 h-6" />
                </div>
                <div>
                  <div className="font-semibold text-lg mb-1 group-hover:text-primary transition-colors">
                    {chat.title}
                  </div>
                  <div className="flex items-center gap-3 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <User className="w-3.5 h-3.5" />
                      {chat.username || '未知用户'}
                    </span>
                    <span className="w-1 h-1 rounded-full bg-border" />
                    <span className="flex items-center gap-1">
                      <FileText className="w-3.5 h-3.5" />
                      {getTemplateName(chat.templateId)}
                    </span>
                    <span className="w-1 h-1 rounded-full bg-border" />
                    <span className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5" />
                      {formatTime(chat.updatedAt)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <Button variant="ghost" size="sm" className="text-primary hover:text-primary hover:bg-primary/10">
                  查看详情
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Chat Detail Modal */}
      <Dialog open={isDetailOpen} onOpenChange={setIsDetailOpen}>
        <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col p-0 gap-0 overflow-hidden">
          <DialogHeader className="p-6 border-b bg-muted/10">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Avatar className="h-10 w-10 border-2 border-background shadow-sm">
                  <AvatarFallback className="bg-primary/10 text-primary">
                    {selectedChat?.username?.[0] || 'U'}
                  </AvatarFallback>
                </Avatar>
                <div>
                  <DialogTitle className="text-lg">{selectedChat?.title}</DialogTitle>
                  <DialogDescription className="mt-1 flex items-center gap-2">
                    <span className="text-primary font-medium">{selectedChat?.username || '未知用户'}</span>
                    <span>·</span>
                    <span>{getTemplateName(selectedChat?.templateId)}</span>
                  </DialogDescription>
                </div>
              </div>
            </div>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-muted/5">
            {isDetailLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="w-6 h-6 animate-spin text-primary" />
              </div>
            ) : messages.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                暂无消息
              </div>
            ) : (
              messages.map((msg) => (
                <div
                  key={msg.id}
                  className={cn(
                    "flex gap-4 max-w-[90%]",
                    msg.role === 'user' ? "ml-auto flex-row-reverse" : ""
                  )}
                >
                  <Avatar className={cn(
                    "w-8 h-8 shrink-0",
                    msg.role === 'assistant' ? "bg-primary text-primary-foreground" : "bg-muted"
                  )}>
                    <AvatarFallback className={msg.role === 'assistant' ? "bg-primary text-white" : "text-muted-foreground"}>
                      {msg.role === 'assistant' ? 'AI' : 'U'}
                    </AvatarFallback>
                  </Avatar>
                  <div className={cn(
                    "px-4 py-3 rounded-2xl text-sm leading-relaxed",
                    msg.role === 'user'
                      ? "bg-primary text-primary-foreground rounded-tr-sm shadow-sm whitespace-pre-wrap"
                      : "bg-white border shadow-sm rounded-tl-sm"
                  )}>
                    {msg.role === 'assistant' ? (
                      <div className="prose prose-sm max-w-none dark:prose-invert prose-p:my-1 prose-headings:my-2 prose-ul:my-1 prose-ol:my-1">
                        <ReactMarkdown>{msg.content}</ReactMarkdown>
                      </div>
                    ) : (
                      <div className="whitespace-pre-wrap">{msg.content}</div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>

          <DialogFooter className="p-4 border-t bg-muted/10">
            <Button variant="outline" onClick={() => setIsDetailOpen(false)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
