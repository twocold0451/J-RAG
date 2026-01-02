import { useState, useEffect } from 'react'
import { Plus, Trash2, Settings2, FileText, Users, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { ConfirmModal } from '@/components/ConfirmModal'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { TemplateDto, DocumentDto, UserGroupDto } from '@/types'

interface TemplateFormData {
  name: string
  description: string
  documentIds: string[]
  visibleGroupIds: number[]
  isPublic: boolean
}

export default function Templates() {
  const { showToast } = useToast()
  const [templates, setTemplates] = useState<TemplateDto[]>([])
  const [documents, setDocuments] = useState<DocumentDto[]>([])
  const [groups, setGroups] = useState<UserGroupDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  // Delete Confirmation State
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [templateToDelete, setTemplateToDelete] = useState<number | null>(null)

  const [newTemplate, setNewTemplate] = useState<TemplateFormData>({
    name: '',
    description: '',
    documentIds: [],
    visibleGroupIds: [],
    isPublic: true,
  })

  // Load data on mount
  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setIsLoading(true)
    try {
      const [templatesData, documentsData, groupsData] = await Promise.all([
        api.getTemplates(),
        api.getDocuments(),
        api.getGroups().catch(() => []), // Groups might not be available to all users
      ])
      setTemplates(templatesData)
      setDocuments(documentsData)
      setGroups(groupsData)
    } catch (err: any) {
      console.error('Failed to load data:', err)
    } finally {
      setIsLoading(false)
    }
  }

  const handleOpenCreate = () => {
    setEditingId(null)
    setNewTemplate({
      name: '',
      description: '',
      documentIds: [],
      visibleGroupIds: [],
      isPublic: true,
    })
    setIsCreateOpen(true)
  }

  const handleOpenEdit = (template: TemplateDto) => {
    setEditingId(template.id)
    setNewTemplate({
      name: template.name,
      description: template.description || '',
      documentIds: template.documentIds || [],
      visibleGroupIds: template.visibleGroups || [],
      isPublic: template.visibleGroups?.length === 0,
    })
    setIsCreateOpen(true)
  }

  const handleSaveTemplate = async () => {
    if (!newTemplate.name.trim()) return

    // 全部用户时 isPublic 为 true，否则为 false
    const isPublic = newTemplate.visibleGroupIds.length === 0

    setIsSaving(true)
    try {
      if (editingId) {
        const updated = await api.updateTemplate(editingId, {
          name: newTemplate.name,
          description: newTemplate.description,
          documentIds: newTemplate.documentIds,
          visibleGroupIds: newTemplate.visibleGroupIds,
          isPublic,
        })
        setTemplates(prev => prev.map(t => t.id === editingId ? updated : t))
      } else {
        const created = await api.createTemplate({
          name: newTemplate.name,
          description: newTemplate.description,
          documentIds: newTemplate.documentIds,
          visibleGroupIds: newTemplate.visibleGroupIds,
          isPublic,
        })
        setTemplates(prev => [created, ...prev])
      }
      setIsCreateOpen(false)
    } catch (err: any) {
      console.error('Failed to save template:', err)
      showToast(`保存失败: ${err.message}`, 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDeleteTemplate = (id: number) => {
    setTemplateToDelete(id)
    setDeleteConfirmOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (templateToDelete) {
      try {
        await api.deleteTemplate(templateToDelete)
        setTemplates(prev => prev.filter(t => t.id !== templateToDelete))
      } catch (err: any) {
        console.error('Failed to delete template:', err)
        showToast(`删除失败: ${err.message}`, 'error')
      }
      setTemplateToDelete(null)
    }
  }

  const toggleDocument = (docId: string) => {
    setNewTemplate(prev => ({
      ...prev,
      documentIds: prev.documentIds.includes(docId)
        ? prev.documentIds.filter(id => id !== docId)
        : [...prev.documentIds, docId],
    }))
  }

  const toggleGroup = (groupId: number) => {
    setNewTemplate(prev => ({
      ...prev,
      visibleGroupIds: prev.visibleGroupIds.includes(groupId)
        ? prev.visibleGroupIds.filter(id => id !== groupId)
        : [...prev.visibleGroupIds, groupId],
    }))
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('zh-CN')
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">对话模板</h1>
          <p className="text-muted-foreground">创建和管理对话模板，关联知识库文档</p>
        </div>
        <Button onClick={handleOpenCreate}>
          <Plus className="w-4 h-4 mr-2" />
          创建模板
        </Button>
      </div>

      {/* Template List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
          <span className="ml-2 text-muted-foreground">加载中...</span>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {templates.map((template) => (
            <Card
              key={template.id}
              className="group hover:border-primary/50 hover:shadow-lg transition-all duration-300 cursor-pointer overflow-hidden relative"
              onClick={() => handleOpenEdit(template)}
            >
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center text-primary group-hover:scale-110 transition-transform">
                      <Settings2 className="w-6 h-6" />
                    </div>
                    <div>
                      <CardTitle className="text-lg group-hover:text-primary transition-colors">{template.name}</CardTitle>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 hover:bg-destructive/10 hover:text-destructive"
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDeleteTemplate(template.id)
                      }}
                    >
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground mb-6 line-clamp-2 min-h-[2.5rem]">
                  {template.description || '暂无描述'}
                </p>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm">
                    <FileText className="w-4 h-4 text-muted-foreground" />
                    <span className="font-medium">{template.documentCount || 0}</span>
                    <span className="text-muted-foreground">个关联文档</span>
                  </div>

                  <div className="flex items-center gap-2 text-sm">
                    <Users className="w-4 h-4 text-muted-foreground" />
                    <div className="flex flex-wrap gap-1">
                      {template.visibleGroups && template.visibleGroups.length > 0 ? (
                        template.visibleGroups.slice(0, 2).map(groupId => (
                          <Badge key={groupId} variant="outline" className="font-normal">
                            {groups.find(g => g.id === groupId)?.name || `组${groupId}`}
                          </Badge>
                        ))
                      ) : (
                        <Badge variant="secondary" className="bg-muted text-muted-foreground font-normal">全部用户</Badge>
                      )}
                      {template.visibleGroups && template.visibleGroups.length > 2 && (
                        <Badge variant="outline" className="font-normal">+{template.visibleGroups.length - 2}</Badge>
                      )}
                    </div>
                  </div>
                </div>

                <div className="mt-6 pt-4 border-t flex items-center justify-between">
                  <span className="text-xs text-muted-foreground italic">
                    创建于 {formatDate(template.createdAt)}
                  </span>
                  <span className="text-xs font-semibold text-primary group-hover:translate-x-1 transition-transform">
                    编辑模板 →
                  </span>
                </div>
              </CardContent>
            </Card>
          ))}

          {/* Empty State */}
          {templates.length === 0 && (
            <div className="col-span-3 text-center py-12">
              <Settings2 className="w-12 h-12 mx-auto text-muted-foreground/30 mb-3" />
              <p className="text-muted-foreground">暂无对话模板</p>
              <Button className="mt-4" onClick={handleOpenCreate}>
                <Plus className="w-4 h-4 mr-2" />
                创建第一个模板
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Create/Edit Template Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-4xl max-h-[85vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>{editingId ? '编辑对话模板' : '创建对话模板'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4 overflow-y-auto flex-1 pr-2">
            <div className="space-y-2">
              <Label htmlFor="name">模板名称</Label>
              <Input
                id="name"
                placeholder="输入模板名称"
                value={newTemplate.name}
                onChange={(e) => setNewTemplate(prev => ({ ...prev, name: e.target.value }))}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">模板描述</Label>
              <Textarea
                id="description"
                placeholder="描述模板用途和适用范围"
                value={newTemplate.description}
                onChange={(e) => setNewTemplate(prev => ({ ...prev, description: e.target.value }))}
              />
            </div>

            <div className="space-y-3">
              <Label className="text-sm font-semibold">可见用户组</Label>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => setNewTemplate(prev => ({ ...prev, visibleGroupIds: [] }))}
                  className={cn(
                    "px-4 py-2 rounded-xl text-sm font-medium border transition-all duration-200",
                    newTemplate.visibleGroupIds.length === 0
                      ? "bg-primary text-primary-foreground border-primary shadow-md shadow-primary/20"
                      : "bg-muted/50 text-muted-foreground border-transparent hover:bg-muted hover:border-border"
                  )}
                >
                  全部用户
                </button>
                {groups.map(group => (
                  <button
                    key={group.id}
                    type="button"
                    onClick={() => toggleGroup(group.id)}
                    className={cn(
                      "px-4 py-2 rounded-xl text-sm font-medium border transition-all duration-200",
                      newTemplate.visibleGroupIds.includes(group.id)
                        ? "bg-primary text-primary-foreground border-primary shadow-md shadow-primary/20"
                        : "bg-muted/50 text-muted-foreground border-transparent hover:bg-muted hover:border-border"
                    )}
                  >
                    {group.name}
                  </button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">选择该模板对哪个用户组开放</p>
            </div>

            <div className="space-y-3">
              <Label className="text-sm font-semibold">关联文档</Label>
              <div className="border rounded-2xl divide-y overflow-hidden max-h-60 overflow-y-auto bg-muted/10">
                {documents.map(doc => {
                  const isSelected = newTemplate.documentIds.includes(doc.id)
                  return (
                    <div
                      key={doc.id}
                      className={cn(
                        "flex items-center gap-3 p-3 transition-all cursor-pointer group",
                        isSelected
                          ? "bg-primary/5 text-primary"
                          : "hover:bg-muted/50"
                      )}
                      onClick={() => toggleDocument(doc.id)}
                    >
                      <div className={cn(
                        "w-5 h-5 rounded-md border flex items-center justify-center transition-all",
                        isSelected
                          ? "bg-primary border-primary text-white"
                          : "border-muted-foreground/30 group-hover:border-primary/50"
                      )}>
                        {isSelected && <Plus className="w-3.5 h-3.5" style={{ transform: 'rotate(0deg)' }} />}
                      </div>
                      <FileText className={cn("w-4 h-4", isSelected ? "text-primary" : "text-muted-foreground")} />
                      <span className="flex-1 text-sm font-medium truncate">{doc.name}</span>
                      <Badge variant="outline" className={cn(isSelected ? "border-primary/30 text-primary" : "")}>
                        {doc.category}
                      </Badge>
                    </div>
                  )
                })}
                {documents.length === 0 && (
                  <div className="p-4 text-center text-sm text-muted-foreground">
                    暂无可用文档，请先上传文档
                  </div>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                已选择 {newTemplate.documentIds.length} 个文档进行关联
              </p>
            </div>
          </div>
          <DialogFooter className="shrink-0">
            <Button variant="outline" onClick={() => setIsCreateOpen(false)}>取消</Button>
            <Button
              onClick={handleSaveTemplate}
              disabled={!newTemplate.name.trim() || isSaving}
            >
              {isSaving ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  保存中...
                </>
              ) : (
                editingId ? '保存修改' : '创建模板'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmModal
        isOpen={deleteConfirmOpen}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={handleConfirmDelete}
        title="删除对话模板？"
        description="删除模板后，基于该模板的历史对话不会丢失，但无法再创建新对话。确认要删除吗？"
      />
    </div>
  )
}
