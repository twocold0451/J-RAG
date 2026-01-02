import { useState, useRef, useEffect, useCallback } from 'react'
import { Send, Plus, MessageSquare, Trash2, Bot, User, FileText } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { ConfirmModal } from '@/components/ConfirmModal'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { ConversationResponse, ChatMessageDto, TemplateDto } from '@/types'

interface SourceInfo {
  id: string
  documentId: string
  score: number
  metadata?: {
    source?: string
    page?: string | number
    elements?: string
  }
}

interface DisplaySource {
  fileName: string
  page: string
}

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  sources?: DisplaySource[]
}

interface Conversation extends ConversationResponse {
  lastMessage?: string
  updatedAt?: string
}

export default function Chat() {
  const { showToast } = useToast()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [templates, setTemplates] = useState<TemplateDto[]>([])
  const [inputMessage, setInputMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isNewChatOpen, setIsNewChatOpen] = useState(false)
  const [newChatTitle, setNewChatTitle] = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState<string>('')
  const [isDeepThinking, setIsDeepThinking] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Delete Confirmation State
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [chatToDelete, setChatToDelete] = useState<number | null>(null)

  // Load initial data
  useEffect(() => {
    loadConversations()
    loadTemplates()
  }, [])

  const loadConversations = async () => {
    try {
      const data = await api.getConversations()
      setConversations(data.map(conv => ({
        ...conv,
        lastMessage: '',
        updatedAt: conv.createdAt || new Date().toISOString()
      })))
    } catch (err) {
      console.error('Failed to load conversations:', err)
    }
  }

  const loadTemplates = async () => {
    try {
      const data = await api.getTemplates()
      setTemplates(data)
    } catch (err) {
      console.error('Failed to load templates:', err)
    }
  }

  const loadMessages = useCallback(async (conversationId: number) => {
    try {
      const data = await api.getConversationMessages(conversationId)
      setMessages(data.map((msg: ChatMessageDto) => ({
        id: msg.id,
        role: msg.role.toLowerCase() as 'user' | 'assistant',
        content: msg.content,
        timestamp: new Date(msg.createdAt).toLocaleTimeString('zh-CN', {
          hour: '2-digit',
          minute: '2-digit'
        })
      })))
    } catch (err) {
      console.error('Failed to load messages:', err)
    }
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const handleSendMessage = async () => {
    if (isLoading) return
    // 检查是否只包含空白字符和符号
    if (!isValidInput(inputMessage)) {
      showToast('请输入有效内容', 'error')
      return
    }
    if (!selectedConversation) {
      // Need to create conversation first
      await handleCreateConversationAndSend()
      return
    }

    const userMessage: Message = {
      id: Date.now(),
      role: 'user',
      content: inputMessage,
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    }

    setMessages(prev => [...prev, userMessage])
    setInputMessage('')
    setIsLoading(true)

    const token = localStorage.getItem('token')
    const apiUrl = (import.meta.env?.VITE_API_BASE_URL as string) || '/api'

    // 检查是否已经有助手消息（欢迎消息），用于更新而不是添加新的
    const hasWelcomeMessage = messages[messages.length - 1]?.content?.includes('已为您创建')

    if (!hasWelcomeMessage) {
      // 添加空的 AI 消息用于流式更新
      const placeholderMessage: Message = {
        id: Date.now() + 1,
        role: 'assistant',
        content: '',
        timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      }
      setMessages(prev => [...prev, placeholderMessage])
    }

    try {
      await fetchEventSource(`${apiUrl}/conversations/${selectedConversation.id}/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        retryOnError: false,
        body: JSON.stringify({
          message: inputMessage,
          useDeepThinking: isDeepThinking
        }),
        async onmessage(msg) {
          if (msg.event === 'delta') {
            // 处理文本增量
            const textDelta = msg.data
            setMessages(prev => {
              const lastMsg = prev[prev.length - 1]
              if (lastMsg && lastMsg.role === 'assistant') {
                const updated = [...prev]
                updated[updated.length - 1] = {
                  ...lastMsg,
                  content: lastMsg.content + textDelta
                }
                return updated
              }
              return prev
            })
          } else if (msg.event === 'sources') {
            // 处理引用来源
            try {
              const sourcesData = JSON.parse(msg.data) as SourceInfo[]
              const displaySources: DisplaySource[] = sourcesData.map(s => ({
                fileName: s.metadata?.source || '未知文档',
                page: s.metadata?.page?.toString() || '-'
              }))
              // 去重
              const uniqueSources = displaySources.filter((item, index, self) =>
                index === self.findIndex(s => s.fileName === item.fileName && s.page === item.page)
              ).slice(0, 5) // 最多显示5个
              setMessages(prev => {
                const lastMsg = prev[prev.length - 1]
                if (lastMsg && lastMsg.role === 'assistant') {
                  const updated = [...prev]
                  updated[updated.length - 1] = {
                    ...lastMsg,
                    sources: uniqueSources
                  }
                  return updated
                }
                return prev
              })
            } catch (e) {
              console.error('解析来源数据失败', e)
            }
          }
        },
        onerror(err) {
          console.error('SSE Error:', err)
          // 超时或网络错误时更新 UI
          if (err instanceof Error && (err.name === 'TimeoutError' || err.message.includes('timeout'))) {
            setMessages(prev => {
              const lastMsg = prev[prev.length - 1]
              if (lastMsg && lastMsg.role === 'assistant' && !lastMsg.content) {
                return [...prev.slice(0, -1), {
                  ...lastMsg,
                  content: '处理时间较长，请稍后查看回复。'
                }]
              }
              return prev
            })
          }
          throw err
        }
      })
    } catch (err: any) {
      // 检查是否已经有 AI 回复（部分成功）
      setMessages(prev => {
        const lastMsg = prev[prev.length - 1]
        if (lastMsg && lastMsg.role === 'assistant' && lastMsg.content) {
          return prev
        }
        // 没有回复，显示错误
        return [...prev.slice(0, -1), {
          id: Date.now() + 1,
          role: 'assistant',
          content: `错误: ${err.message || '未知错误'}`,
          timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        }]
      })
    } finally {
      setIsLoading(false)
    }
  }

  const handleCreateConversationAndSend = async () => {
    if (!newChatTitle.trim()) return

    setIsLoading(true)
    try {
      const conv = await api.createConversation({
        title: newChatTitle,
        templateId: selectedTemplate ? Number(selectedTemplate) : undefined,
        isPublic: true,
      })

      const newConversation: Conversation = {
        ...conv,
        updatedAt: new Date().toISOString(),
      }

      setConversations(prev => [newConversation, ...prev])
      setSelectedConversation(newConversation)
      setMessages([
        {
          id: 1,
          role: 'assistant',
          content: `您好！已为您创建「${newChatTitle}」对话。`,
          timestamp: '现在'
        }
      ])

      setIsNewChatOpen(false)
      setNewChatTitle('')
      setSelectedTemplate('')
    } catch (err: any) {
      console.error('Failed to create conversation:', err)
      showToast(`创建对话失败: ${err.message}`, 'error')
    } finally {
      setIsLoading(false)
    }
  }

  const handleSelectConversation = async (conv: Conversation) => {
    setSelectedConversation(conv)
    await loadMessages(conv.id)
  }

  const handleDeleteConversation = (id: number, e: React.MouseEvent) => {
    e.stopPropagation()
    setChatToDelete(id)
    setDeleteConfirmOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (chatToDelete) {
      try {
        await api.deleteConversation(chatToDelete)
        setConversations(prev => prev.filter(c => c.id !== chatToDelete))
        if (selectedConversation?.id === chatToDelete) {
          setSelectedConversation(null)
          setMessages([])
        }
      } catch (err: any) {
        console.error('Failed to delete conversation:', err)
        showToast(`删除失败: ${err.message}`, 'error')
      }
      setChatToDelete(null)
    }
  }

  // 验证输入是否包含有效内容（非仅空白或符号）
  const isValidInput = (text: string): boolean => {
    if (!text.trim()) return false
    // 检查是否只包含空白字符和符号
    const meaningfulContent = text.replace(/[\s\p{P}]/gu, '')
    return meaningfulContent.length > 0
  }

  const formatTime = (dateStr?: string) => {
    if (!dateStr) return '刚刚'
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

  return (
    <div className="flex h-[calc(100vh-8rem)] gap-6 animate-in fade-in duration-500">
      {/* History Sidebar */}
      <div className="w-72 flex flex-col gap-2 shrink-0">
        <Button onClick={() => setIsNewChatOpen(true)} className="w-full justify-start gap-2 h-12 shadow-sm">
          <Plus className="w-5 h-5" />
          <span className="font-medium">新建对话</span>
        </Button>

        <div className="flex-1 overflow-y-auto pr-2 space-y-2 mt-2">
          {conversations.map((conv) => (
            <div
              key={conv.id}
              onClick={() => handleSelectConversation(conv)}
              className={cn(
                "group flex flex-col gap-1 p-3 rounded-xl cursor-pointer transition-all border",
                selectedConversation?.id === conv.id
                  ? "bg-card border-primary/20 shadow-md shadow-primary/5 ring-1 ring-primary/20"
                  : "bg-transparent border-transparent hover:bg-muted/50 hover:border-border/50"
              )}
            >
              <div className="flex items-start justify-between gap-2">
                <span className={cn(
                  "font-medium text-sm truncate",
                  selectedConversation?.id === conv.id ? "text-primary" : "text-foreground"
                )}>
                  {conv.title}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 -mr-1 opacity-0 group-hover:opacity-100 transition-opacity"
                  onClick={(e) => handleDeleteConversation(conv.id, e)}
                >
                  <Trash2 className="w-3.5 h-3.5 text-muted-foreground hover:text-destructive" />
                </Button>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                {conv.templateId && (
                  <span className="flex items-center gap-1 bg-muted px-1.5 py-0.5 rounded-md">
                    <FileText className="w-3 h-3" />
                    {templates.find(t => t.id === conv.templateId)?.name || '模板'}
                  </span>
                )}
                <span className="ml-auto">{formatTime(conv.updatedAt)}</span>
              </div>
            </div>
          ))}
          {conversations.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">
              暂无对话记录
            </div>
          )}
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col bg-card rounded-2xl border shadow-sm relative overflow-hidden">
        {selectedConversation ? (
          <>
            {/* Chat Header */}
            <div className="h-14 border-b flex items-center justify-between px-6 bg-card/50 backdrop-blur-sm z-10">
              <div className="flex items-center gap-3">
                <div className="font-semibold">{selectedConversation.title}</div>
                {selectedConversation.templateId && (
                  <span className="text-xs px-2 py-1 rounded-full bg-primary/10 text-primary font-medium">
                    {templates.find(t => t.id === selectedConversation.templateId)?.name || '模板'}
                  </span>
                )}
              </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 scroll-smooth">
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={cn(
                    "flex gap-4 max-w-3xl mx-auto",
                    message.role === 'user' ? "flex-row-reverse" : ""
                  )}
                >
                  <Avatar className={cn(
                    "w-8 h-8 mt-1 border",
                    message.role === 'assistant' ? "bg-primary/10 border-primary/20" : "bg-muted border-transparent"
                  )}>
                    <AvatarFallback className={cn(
                      "text-xs",
                      message.role === 'assistant' ? "text-primary bg-transparent" : "text-muted-foreground bg-transparent"
                    )}>
                      {message.role === 'assistant' ? <Bot className="w-5 h-5" /> : <User className="w-5 h-5" />}
                    </AvatarFallback>
                  </Avatar>

                  <div className={cn(
                    "flex flex-col gap-1 min-w-0",
                    message.role === 'user' ? "items-end" : "items-start"
                  )}>
                    <div className={cn(
                      "px-5 py-3 rounded-2xl text-sm leading-relaxed shadow-sm max-w-full",
                      message.role === 'user'
                        ? "bg-primary text-primary-foreground rounded-tr-sm"
                        : "bg-muted/50 text-foreground rounded-tl-sm border"
                    )}>
                      {message.role === 'user' ? (
                        <pre className="whitespace-pre-wrap font-sans bg-transparent p-0 m-0 border-none">{message.content}</pre>
                      ) : (
                        <div className="prose prose-sm max-w-none dark:prose-invert">
                          <ReactMarkdown>{message.content}</ReactMarkdown>
                        </div>
                      )}
                    </div>
                    {/* 来源展示 */}
                    {message.role === 'assistant' && message.sources && message.sources.length > 0 && (
                      <div className="flex flex-wrap gap-2 mt-3 ml-1 pt-3 border-t border-dashed border-muted-foreground/20">
                        <div className="w-full flex items-center gap-1.5 mb-1">
                          <span className="text-xs text-muted-foreground font-medium">引用来源</span>
                        </div>
                        {message.sources.map((source, index) => (
                          <div
                            key={index}
                            className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-muted/60 text-xs hover:bg-muted transition-colors"
                          >
                            <span className="text-foreground truncate max-w-[120px]">{source.fileName}</span>
                            <span className="text-muted-foreground">·</span>
                            <span className="text-muted-foreground">第{source.page}页</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {isLoading && (
                <div className="flex gap-4 max-w-3xl mx-auto">
                  <Avatar className="w-8 h-8 bg-primary/10 border border-primary/20">
                    <AvatarFallback className="text-primary bg-transparent"><Bot className="w-5 h-5" /></AvatarFallback>
                  </Avatar>
                  <div className="bg-muted/50 px-5 py-4 rounded-2xl rounded-tl-sm border flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 bg-primary/40 rounded-full animate-bounce" />
                    <span className="w-1.5 h-1.5 bg-primary/40 rounded-full animate-bounce delay-150" />
                    <span className="w-1.5 h-1.5 bg-primary/40 rounded-full animate-bounce delay-300" />
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} className="h-4" />
            </div>

            {/* Input Area */}
            <div className="p-4 max-w-3xl mx-auto w-full">
              <div className="relative flex items-end gap-2 p-2 rounded-xl border bg-background shadow-lg shadow-black/5 ring-1 ring-black/5 focus-within:ring-primary/30 transition-all">
                <Textarea
                  placeholder="输入您的问题..."
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      handleSendMessage()
                    }
                  }}
                  className="min-h-[40px] max-h-32 border-0 shadow-none focus-visible:ring-0 resize-none py-2.5 bg-transparent"
                />
                <Button
                  onClick={handleSendMessage}
                  disabled={isLoading}
                  className={cn(
                    "h-10 w-10 shrink-0 rounded-lg transition-all",
                    !isLoading ? "bg-primary text-primary-foreground shadow-md hover:bg-primary/90" : "bg-muted text-muted-foreground hover:bg-muted"
                  )}
                  size="icon"
                >
                  <Send className="w-4 h-4" />
                </Button>
              </div>
              <div className="text-center mt-2 text-xs text-muted-foreground/60 flex items-center justify-center gap-4">
                <span>AI 内容可能并不完全准确，请核对重要信息。</span>
                <label className="flex items-center gap-1.5 cursor-pointer hover:text-primary transition-colors">
                  <input
                    type="checkbox"
                    checked={isDeepThinking}
                    onChange={(e) => setIsDeepThinking(e.target.checked)}
                    className="rounded border-border"
                  />
                  深度思考
                </label>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-center p-8">
            <div className="w-16 h-16 bg-primary/10 rounded-2xl flex items-center justify-center mb-6 text-primary animate-in zoom-in-50 duration-500">
              <MessageSquare className="w-8 h-8" />
            </div>
            <h2 className="text-2xl font-bold tracking-tight mb-2">开始新的对话</h2>
            <p className="text-muted-foreground max-w-md mb-8">
              选择一个模板或直接开始，AI 助手将根据知识库文档为您提供精准的解答。
            </p>
            <Button size="lg" onClick={() => setIsNewChatOpen(true)} className="gap-2 shadow-lg shadow-primary/20 hover:shadow-primary/30 transition-all">
              <Plus className="w-5 h-5" />
              创建新对话
            </Button>
          </div>
        )}
      </div>

      {/* New Chat Dialog */}
      <Dialog open={isNewChatOpen} onOpenChange={setIsNewChatOpen}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>新建对话</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="title">对话标题</Label>
              <Input
                id="title"
                placeholder="例如：年假政策咨询"
                value={newChatTitle}
                onChange={(e) => setNewChatTitle(e.target.value)}
              />
            </div>
            <div className="grid gap-3">
              <Label>选择模板</Label>
              <div className="border rounded-xl divide-y max-h-[250px] overflow-y-auto">
                {templates.map((template) => {
                  const isSelected = selectedTemplate === template.id.toString()
                  return (
                    <div
                      key={template.id}
                      onClick={() => {
                        setSelectedTemplate(template.id.toString())
                        setNewChatTitle(`${template.name}咨询`)
                      }}
                      className={cn(
                        "flex items-center gap-3 p-3 cursor-pointer transition-colors",
                        isSelected ? "bg-primary/5" : "hover:bg-muted/50"
                      )}
                    >
                      <div className={cn(
                        "w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors",
                        isSelected
                          ? "border-primary bg-primary text-primary-foreground"
                          : "border-muted-foreground/30"
                      )}>
                        {isSelected && <div className="w-2 h-2 rounded-full bg-current" />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="font-medium truncate">{template.name}</div>
                        <div className="text-xs text-muted-foreground">
                          {template.documentCount || 0} 篇文档
                        </div>
                      </div>
                    </div>
                  )
                })}
                {templates.length === 0 && (
                  <div className="p-4 text-sm text-muted-foreground text-center">
                    暂无可用模板
                  </div>
                )}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsNewChatOpen(false)}>取消</Button>
            <Button
              onClick={handleCreateConversationAndSend}
              disabled={!newChatTitle.trim() || !selectedTemplate || isLoading}
            >
              {isLoading ? '创建中...' : '开始对话'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmModal
        isOpen={deleteConfirmOpen}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={handleConfirmDelete}
        title="删除对话？"
        description="此操作将永久删除该对话记录，无法找回。"
      />
    </div>
  )
}
