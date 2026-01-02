import { useState, useRef, useEffect } from 'react'
import { Upload, FileText, Search, Trash2, Loader2, Link, File } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import { ConfirmModal } from '@/components/ConfirmModal'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import { useDocumentProgress } from '@/hooks/useDocumentProgress'
import type { DocumentDto } from '@/types'

const statusMap: Record<string, { label: string; color: string }> = {
  PENDING: { label: '等待处理', color: 'bg-gray-100 text-gray-600' },
  PROCESSING: { label: '处理中', color: 'bg-yellow-100 text-yellow-600' },
  COMPLETED: { label: '已入库', color: 'bg-green-100 text-green-600' },
  FAILED: { label: '失败', color: 'bg-red-100 text-red-600' },
}

const categoryColors: Record<string, string> = {
  '人事': 'bg-green-100 text-green-600',
  '技术': 'bg-purple-100 text-purple-600',
  '销售': 'bg-orange-100 text-orange-600',
  '财务': 'bg-blue-100 text-blue-600',
  '法务': 'bg-red-100 text-red-600',
}

export default function Documents() {
  const { showToast } = useToast()
  const [documents, setDocuments] = useState<DocumentDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState('all')
  const [isUploadOpen, setIsUploadOpen] = useState(false)
  const [selectedFiles, setSelectedFiles] = useState<File[]>([])
  const [uploadCategory, setUploadCategory] = useState('人事')
  const [isUploading, setIsUploading] = useState(false)
  const [urlInput, setUrlInput] = useState('')
  const [urlCategory, setUrlCategory] = useState('人事')
  const [isUrlLoading, setIsUrlLoading] = useState(false)
  const [uploadingDocId, setUploadingDocId] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // WebSocket progress tracking
  const { getProgress, clearProgress } = useDocumentProgress(null)

  // Delete Confirmation State
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [documentToDelete, setDocumentToDelete] = useState<string | null>(null)

  const categories = ['人事', '技术', '销售', '财务', '法务']

  // Load documents on mount
  useEffect(() => {
    loadDocuments()
  }, [])

  const loadDocuments = async () => {
    setIsLoading(true)
    try {
      const data = await api.getDocuments()
      setDocuments(data)
    } catch (err: any) {
      console.error('Failed to load documents:', err)
    } finally {
      setIsLoading(false)
    }
  }

  const filteredDocuments = documents.filter(doc => {
    const matchesSearch = (doc.name || '').toLowerCase().includes(searchQuery.toLowerCase())
    const matchesCategory = categoryFilter === 'all' || doc.category === categoryFilter
    const matchesStatus = statusFilter === 'all' || doc.status === statusFilter
    return matchesSearch && matchesCategory && matchesStatus
  })

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      setSelectedFiles(prev => [...prev, ...Array.from(e.target.files!)])
    }
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    if (e.dataTransfer.files) {
      setSelectedFiles(prev => [...prev, ...Array.from(e.dataTransfer.files)])
    }
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
  }

  const removeFile = (index: number) => {
    setSelectedFiles(prev => prev.filter((_, i) => i !== index))
  }

  const handleUpload = async () => {
    if (selectedFiles.length === 0) return

    setIsUploading(true)
    try {
      for (const file of selectedFiles) {
        const result = await api.uploadDocument(file, uploadCategory, false)
        if (result.id) {
          setUploadingDocId(result.id)
        }
      }
      // 延迟关闭对话框，让用户看到进度
      setTimeout(() => {
        setIsUploadOpen(false)
        setSelectedFiles([])
        showToast('文档正在后台处理中', 'success')
      }, 500)
    } catch (err: any) {
      console.error('Upload failed:', err)
      showToast(`上传失败: ${err.message}`, 'error')
    } finally {
      setIsUploading(false)
    }
  }

  const handleUrlSubmit = async () => {
    if (!urlInput.trim()) return

    setIsUrlLoading(true)
    try {
      const result = await api.ingestUrl({ url: urlInput, isPublic: false, category: urlCategory })
      if (result.id) {
        setUploadingDocId(result.id)
      }
      setTimeout(() => {
        setIsUploadOpen(false)
        setUrlInput('')
        showToast('文档正在后台处理中', 'success')
      }, 500)
    } catch (err: any) {
      console.error('URL ingest failed:', err)
      showToast(`获取失败: ${err.message}`, 'error')
    } finally {
      setIsUrlLoading(false)
    }
  }

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  }

  const handleDeleteClick = (id: string) => {
    setDocumentToDelete(id)
    setDeleteConfirmOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (documentToDelete) {
      try {
        await api.deleteDocument(documentToDelete)
        setDocuments(prev => prev.filter(d => d.id !== documentToDelete))
      } catch (err: any) {
        console.error('Failed to delete document:', err)
        showToast(`删除失败: ${err.message}`, 'error')
      }
      setDocumentToDelete(null)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">文档管理</h1>
          <p className="text-muted-foreground">管理和维护企业知识库文档</p>
        </div>
        <Button onClick={() => setIsUploadOpen(true)}>
          <Upload className="w-4 h-4 mr-2" />
          上传文档
        </Button>
      </div>

      {/* Filters & Search */}
      <Card className="border-border/50 shadow-sm">
        <CardContent className="p-5 space-y-5">
          {/* Search Row */}
          <div>
            <div className="relative w-64">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="搜索文档名称..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 h-10 bg-muted/30 focus:bg-background transition-colors"
              />
            </div>
          </div>

          <div className="h-px bg-border/50 w-full" />

          {/* Filter Rows */}
          <div className="space-y-4">
            {/* Categories */}
            <div className="flex items-center gap-4">
              <span className="text-sm font-medium text-muted-foreground min-w-[3rem]">分类：</span>
              <div className="flex flex-wrap gap-2">
                {['all', ...categories].map(cat => {
                  const label = cat === 'all' ? '全部' : cat;
                  const isActive = categoryFilter === cat;
                  return (
                    <button
                      key={cat}
                      onClick={() => setCategoryFilter(cat)}
                      className={cn(
                        "px-3 py-1.5 rounded-lg text-sm font-medium transition-all duration-200 border",
                        isActive
                          ? "bg-primary text-primary-foreground border-primary shadow-sm"
                          : "bg-transparent text-muted-foreground border-transparent hover:bg-muted hover:text-foreground"
                      )}
                    >
                      {label}
                    </button>
                  )
                })}
              </div>
            </div>

            {/* Status */}
            <div className="flex items-center gap-4">
              <span className="text-sm font-medium text-muted-foreground min-w-[3rem]">状态：</span>
              <div className="flex flex-wrap gap-2">
                {['all', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'].map(status => {
                  const statusInfo = statusMap[status]
                  const label = status === 'all' ? '全部' : statusInfo?.label || status;
                  const isActive = statusFilter === status;
                  return (
                    <button
                      key={status}
                      onClick={() => setStatusFilter(status)}
                      className={cn(
                        "px-3 py-1.5 rounded-lg text-sm font-medium transition-all duration-200 border",
                        isActive
                          ? "bg-primary text-primary-foreground border-primary shadow-sm"
                          : "bg-transparent text-muted-foreground border-transparent hover:bg-muted hover:text-foreground"
                      )}
                    >
                      {label}
                    </button>
                  )
                })}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Document List */}
      <Card>
        <CardHeader>
          <CardTitle>文档列表 ({filteredDocuments.length})</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
              <span className="ml-2 text-muted-foreground">加载中...</span>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredDocuments.map((doc) => (
                <div
                  key={doc.id}
                  className="flex items-center gap-4 p-4 rounded-lg border hover:border-primary/50 hover:bg-muted/50 transition-colors"
                >
                  <div className="w-12 h-12 rounded-xl bg-muted flex items-center justify-center text-2xl">
                    📄
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-medium truncate">{doc.name}</div>
                    <div className="text-sm text-muted-foreground flex gap-4 mt-1">
                      <span>上传于 {formatDate(doc.uploadedAt)}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`px-2.5 py-1 rounded-full text-xs ${categoryColors[doc.category] || 'bg-muted text-muted-foreground'}`}>
                      {doc.category}
                    </span>
                    <span className={`px-2.5 py-1 rounded-full text-xs ${statusMap[doc.status]?.color || 'bg-muted text-muted-foreground'}`}>
                      {statusMap[doc.status]?.label || doc.status}
                    </span>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-9 w-9 rounded-xl text-muted-foreground/70 hover:bg-destructive/10 hover:text-destructive transition-colors"
                      onClick={() => handleDeleteClick(doc.id)}
                    >
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              ))}
              {filteredDocuments.length === 0 && (
                <div className="text-center py-12 text-muted-foreground">
                  <FileText className="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p>没有找到相关文档</p>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmModal
        isOpen={deleteConfirmOpen}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={handleConfirmDelete}
        title="确认删除文档？"
        description="此操作将永久删除该文档及其所有索引数据，无法撤销。"
      />

      {/* Upload Dialog */}
      <Dialog open={isUploadOpen} onOpenChange={setIsUploadOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>上传文档</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <Tabs defaultValue="file" className="w-full">
              <TabsList className="grid w-full grid-cols-2 mb-4">
                <TabsTrigger value="file" className="flex items-center gap-2">
                  <File className="w-4 h-4" />
                  本地文件
                </TabsTrigger>
                <TabsTrigger value="url" className="flex items-center gap-2">
                  <Link className="w-4 h-4" />
                  网络地址
                </TabsTrigger>
              </TabsList>

              <TabsContent value="file" className="space-y-4">
                <div
                  className="border-2 border-dashed rounded-lg p-8 text-center hover:border-primary/50 transition-colors cursor-pointer"
                  onClick={() => fileInputRef.current?.click()}
                  onDrop={handleDrop}
                  onDragOver={handleDragOver}
                >
                  <Upload className="w-10 h-10 mx-auto text-muted-foreground mb-3" />
                  <p className="text-sm text-muted-foreground mb-2">
                    点击或拖拽文件到此处上传
                  </p>
                  <p className="text-xs text-muted-foreground">
                    支持 PDF、Word、Excel、Markdown、TXT 格式
                  </p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.md,.txt"
                    onChange={handleFileSelect}
                    className="hidden"
                  />
                </div>

                {selectedFiles.length > 0 && (
                  <div className="space-y-2">
                    <Label>已选择的文件 ({selectedFiles.length})</Label>
                    <div className="space-y-2 max-h-40 overflow-y-auto">
                      {selectedFiles.map((file, index) => (
                        <div key={index} className="flex items-center justify-between p-2 rounded bg-muted">
                          <div className="flex items-center gap-2">
                            <FileText className="w-4 h-4" />
                            <span className="text-sm truncate max-w-md">{file.name}</span>
                            <span className="text-xs text-muted-foreground">
                              ({formatFileSize(file.size)})
                            </span>
                          </div>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={() => removeFile(index)}
                          >
                            ×
                          </Button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="space-y-3">
                  <Label className="text-sm font-semibold">选择文档分类</Label>
                  <div className="flex flex-wrap gap-2">
                    {categories.map(cat => (
                      <button
                        key={cat}
                        type="button"
                        onClick={() => setUploadCategory(cat)}
                        className={cn(
                          "px-4 py-2 rounded-xl text-sm font-medium border transition-all duration-200",
                          uploadCategory === cat
                            ? "bg-primary text-primary-foreground border-primary shadow-md shadow-primary/20 scale-[1.02]"
                            : "bg-muted/50 text-muted-foreground border-transparent hover:bg-muted hover:border-border"
                        )}
                      >
                        {cat}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Progress Bar */}
                {uploadingDocId && (() => {
                  const progress = getProgress(uploadingDocId)
                  if (!progress) return null
                  return (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-2">
                          <Loader2 className={cn("w-4 h-4 animate-spin", progress.status === 'COMPLETED' ? 'text-green-500' : 'text-yellow-500')} />
                          {progress.status === 'PROCESSING' && '文档处理中...'}
                          {progress.status === 'COMPLETED' && '处理完成'}
                          {progress.status === 'FAILED' && '处理失败'}
                        </span>
                        <span className="font-medium">{progress.progress}%</span>
                      </div>
                      <div className="h-2 bg-muted rounded-full overflow-hidden">
                        <div
                          className={cn(
                            "h-full transition-all duration-300",
                            progress.status === 'FAILED' ? 'bg-red-500' : 'bg-primary'
                          )}
                          style={{ width: `${progress.progress}%` }}
                        />
                      </div>
                      {progress.errorMessage && (
                        <p className="text-xs text-red-500">{progress.errorMessage}</p>
                      )}
                    </div>
                  )
                })()}

                <DialogFooter className="p-6 bg-muted/5 border-t mt-4">
                  <Button variant="outline" onClick={() => {
                    setIsUploadOpen(false)
                    clearProgress()
                    setUploadingDocId(null)
                  }} className="rounded-xl">取消</Button>
                  <Button
                    onClick={handleUpload}
                    disabled={selectedFiles.length === 0 || isUploading}
                    className="rounded-xl px-8 bg-primary hover:bg-primary/90 shadow-lg shadow-primary/20"
                  >
                    {isUploading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        上传中...
                      </>
                    ) : (
                      '开始上传'
                    )}
                  </Button>
                </DialogFooter>
              </TabsContent>

              <TabsContent value="url" className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="urlInput" className="text-sm font-semibold">
                    文档地址
                  </Label>
                  <Input
                    id="urlInput"
                    placeholder="https://example.com/document.pdf"
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    className="h-11"
                  />
                  <p className="text-xs text-muted-foreground">
                    输入可访问的文档链接，系统将自动获取并处理文档内容
                  </p>
                </div>

                <div className="space-y-3">
                  <Label className="text-sm font-semibold">选择文档分类</Label>
                  <div className="flex flex-wrap gap-2">
                    {categories.map(cat => (
                      <button
                        key={cat}
                        type="button"
                        onClick={() => setUrlCategory(cat)}
                        className={cn(
                          "px-4 py-2 rounded-xl text-sm font-medium border transition-all duration-200",
                          urlCategory === cat
                            ? "bg-primary text-primary-foreground border-primary shadow-md shadow-primary/20 scale-[1.02]"
                            : "bg-muted/50 text-muted-foreground border-transparent hover:bg-muted hover:border-border"
                        )}
                      >
                        {cat}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Progress Bar */}
                {uploadingDocId && (() => {
                  const progress = getProgress(uploadingDocId)
                  if (!progress) return null
                  return (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-2">
                          <Loader2 className={cn("w-4 h-4 animate-spin", progress.status === 'COMPLETED' ? 'text-green-500' : 'text-yellow-500')} />
                          {progress.status === 'PROCESSING' && '文档处理中...'}
                          {progress.status === 'COMPLETED' && '处理完成'}
                          {progress.status === 'FAILED' && '处理失败'}
                        </span>
                        <span className="font-medium">{progress.progress}%</span>
                      </div>
                      <div className="h-2 bg-muted rounded-full overflow-hidden">
                        <div
                          className={cn(
                            "h-full transition-all duration-300",
                            progress.status === 'FAILED' ? 'bg-red-500' : 'bg-primary'
                          )}
                          style={{ width: `${progress.progress}%` }}
                        />
                      </div>
                      {progress.errorMessage && (
                        <p className="text-xs text-red-500">{progress.errorMessage}</p>
                      )}
                    </div>
                  )
                })()}

                <DialogFooter className="p-6 bg-muted/5 border-t mt-4">
                  <Button variant="outline" onClick={() => {
                    setIsUploadOpen(false)
                    clearProgress()
                    setUploadingDocId(null)
                  }} className="rounded-xl">取消</Button>
                  <Button
                    onClick={handleUrlSubmit}
                    disabled={!urlInput.trim() || isUrlLoading}
                    className="rounded-xl px-8 bg-primary hover:bg-primary/90 shadow-lg shadow-primary/20"
                  >
                    {isUrlLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        获取中...
                      </>
                    ) : (
                      '获取文档'
                    )}
                  </Button>
                </DialogFooter>
              </TabsContent>
            </Tabs>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
