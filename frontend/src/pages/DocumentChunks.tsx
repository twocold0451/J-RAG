import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Edit2, Merge, Split, Trash2, Save, ChevronDown, ChevronUp, Loader2, ArrowLeft, ArrowUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { ChunkDto, DocumentDto } from '@/types'
import { cn } from '@/lib/utils'

export default function DocumentChunks() {
  const { documentId } = useParams<{ documentId: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const containerRef = useRef<HTMLDivElement>(null)

  const [document, setDocument] = useState<DocumentDto | null>(null)
  const [chunks, setChunks] = useState<ChunkDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingDoc, setIsLoadingDoc] = useState(true)
  const [editingChunkId, setEditingChunkId] = useState<string | null>(null)
  const [editContent, setEditContent] = useState('')
  const [savingChunkId, setSavingChunkId] = useState<string | null>(null)

  // Split dialog state
  const [splitDialogOpen, setSplitDialogOpen] = useState(false)
  const [splittingChunkId, setSplittingChunkId] = useState<string | null>(null)
  const [splitPart1, setSplitPart1] = useState('')
  const [splitPart2, setSplitPart2] = useState('')
  const [isSplitting, setIsSplitting] = useState(false)

  // Merge dialog state
  const [mergeDialogOpen, setMergeDialogOpen] = useState(false)
  const [mergingChunk1Id, setMergingChunk1Id] = useState<string | null>(null)
  const [mergingChunk2Id, setMergingChunk2Id] = useState<string | null>(null)
  const [isMerging, setIsMerging] = useState(false)

  // Delete confirmation state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deletingChunkId, setDeletingChunkId] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  // Expanded chunks state
  const [expandedChunks, setExpandedChunks] = useState<Set<string>>(new Set())

  // Scroll to top button state
  const [showScrollToTop, setShowScrollToTop] = useState(false)

  // Handle scroll event
  useEffect(() => {
    const handleScroll = (e: Event) => {
      const target = e.target as HTMLElement
      if (target.scrollTop !== undefined) {
        setShowScrollToTop(target.scrollTop > 300)
      }
    }

    // Use capture: true to catch scroll events from the parent scrollable div
    window.addEventListener('scroll', handleScroll, true)
    return () => window.removeEventListener('scroll', handleScroll, true)
  }, [])

  const scrollToTop = () => {
    // Find the closest scrollable parent (the div in Layout.tsx)
    if (containerRef.current) {
      const scrollableParent = containerRef.current.closest('.overflow-y-auto')
      if (scrollableParent) {
        scrollableParent.scrollTo({ top: 0, behavior: 'smooth' })
      }
    }
  }

  // Load document info
  useEffect(() => {
    const loadDocument = async () => {
      if (!documentId) return
      setIsLoadingDoc(true)
      try {
        const documents = await api.getDocuments()
        const doc = documents.find(d => d.id === documentId)
        if (doc) {
          setDocument(doc)
        }
      } catch (err: any) {
        console.error('Failed to load document:', err)
      } finally {
        setIsLoadingDoc(false)
      }
    }
    loadDocument()
  }, [documentId])

  const loadChunks = useCallback(async () => {
    if (!documentId) return
    setIsLoading(true)
    try {
      const data = await api.getChunksByDocumentId(documentId)
      setChunks(data)
    } catch (err: any) {
      console.error('Failed to load chunks:', err)
      showToast(`加载切块失败: ${err.message}`, 'error')
    } finally {
      setIsLoading(false)
    }
  }, [documentId, showToast])

  useEffect(() => {
    if (documentId) {
      loadChunks()
    }
  }, [documentId, loadChunks])

  const handleBack = () => {
    navigate('/documents')
  }

  const handleEditClick = (chunk: ChunkDto) => {
    setEditingChunkId(chunk.id)
    setEditContent(chunk.content)
  }

  const handleSaveEdit = async (chunkId: string) => {
    if (!editContent.trim()) {
      showToast('切块内容不能为空', 'error')
      return
    }
    setSavingChunkId(chunkId)
    try {
      await api.updateChunk(chunkId, { content: editContent.trim() })
      showToast('切块更新成功', 'success')
      setEditingChunkId(null)
      loadChunks()
    } catch (err: any) {
      console.error('Failed to update chunk:', err)
      showToast(`更新失败: ${err.message}`, 'error')
    } finally {
      setSavingChunkId(null)
    }
  }

  const handleCancelEdit = () => {
    setEditingChunkId(null)
    setEditContent('')
  }

  const handleMergeClick = (chunk: ChunkDto, index: number) => {
    if (index >= chunks.length - 1) {
      showToast('没有下一个切块可以合并', 'error')
      return
    }
    const nextChunk = chunks[index + 1]
    setMergingChunk1Id(chunk.id)
    setMergingChunk2Id(nextChunk.id)
    setMergeDialogOpen(true)
  }

  const handleConfirmMerge = async () => {
    if (!mergingChunk1Id || !mergingChunk2Id) return
    setIsMerging(true)
    try {
      await api.mergeChunks({ chunk1Id: mergingChunk1Id, chunk2Id: mergingChunk2Id })
      showToast('切块合并成功', 'success')
      setMergeDialogOpen(false)
      setMergingChunk1Id(null)
      setMergingChunk2Id(null)
      loadChunks()
    } catch (err: any) {
      console.error('Failed to merge chunks:', err)
      showToast(`合并失败: ${err.message}`, 'error')
    } finally {
      setIsMerging(false)
    }
  }

  const handleSplitClick = (chunk: ChunkDto) => {
    setSplittingChunkId(chunk.id)
    // 预填充：尝试从原文中间分割
    const mid = Math.floor(chunk.content.length / 2)
    setSplitPart1(chunk.content.slice(0, mid))
    setSplitPart2(chunk.content.slice(mid))
    setSplitDialogOpen(true)
  }

  const handleConfirmSplit = async () => {
    if (!splittingChunkId) return
    if (!splitPart1.trim() || !splitPart2.trim()) {
      showToast('两部分内容都不能为空', 'error')
      return
    }
    setIsSplitting(true)
    try {
      await api.splitChunk(splittingChunkId, { part1: splitPart1.trim(), part2: splitPart2.trim() })
      showToast('切块拆分成功', 'success')
      setSplitDialogOpen(false)
      setSplittingChunkId(null)
      setSplitPart1('')
      setSplitPart2('')
      loadChunks()
    } catch (err: any) {
      console.error('Failed to split chunk:', err)
      showToast(`拆分失败: ${err.message}`, 'error')
    } finally {
      setIsSplitting(false)
    }
  }

  const handleDeleteClick = (chunk: ChunkDto) => {
    setDeletingChunkId(chunk.id)
    setDeleteDialogOpen(true)
  }

  const handleConfirmDelete = async () => {
    if (!deletingChunkId) return
    setIsDeleting(true)
    try {
      await api.deleteChunk(deletingChunkId)
      showToast('切块删除成功', 'success')
      setDeleteDialogOpen(false)
      setDeletingChunkId(null)
      loadChunks()
    } catch (err: any) {
      console.error('Failed to delete chunk:', err)
      showToast(`删除失败: ${err.message}`, 'error')
    } finally {
      setIsDeleting(false)
    }
  }

  const toggleExpand = (chunkId: string) => {
    setExpandedChunks(prev => {
      const newSet = new Set(prev)
      if (newSet.has(chunkId)) {
        newSet.delete(chunkId)
      } else {
        newSet.add(chunkId)
      }
      return newSet
    })
  }

  return (
    <div className="space-y-6" ref={containerRef}>
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={handleBack}
          className="rounded-full"
        >
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold">
            {isLoadingDoc ? '加载中...' : `切块详情: ${document?.name || '未知文档'}`}
          </h1>
          <p className="text-muted-foreground">
            共 {chunks.length} 个切块
          </p>
        </div>
      </div>

      {/* Chunks List */}
      <Card>
        <CardContent className="p-6">
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
              <span className="ml-2 text-muted-foreground">加载中...</span>
            </div>
          ) : (
            <div className="space-y-4">
              {chunks.map((chunk, index) => {
                const isEditing = editingChunkId === chunk.id
                const isExpanded = expandedChunks.has(chunk.id)
                const shouldTruncate = chunk.content.length > 300 && !isExpanded

                return (
                  <Card
                    key={chunk.id}
                    className={cn(
                      "border-border/50 transition-all",
                      isEditing && "border-primary ring-1 ring-primary"
                    )}
                  >
                    <CardHeader className="pb-3 flex flex-row items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-muted-foreground">
                          #{chunk.chunkIndex + 1}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {chunk.content.length} 字符
                        </span>
                      </div>
                      <div className="flex items-center gap-1">
                        {!isEditing && (
                          <>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleEditClick(chunk)}
                              title="编辑"
                            >
                              <Edit2 className="w-4 h-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleMergeClick(chunk, index)}
                              disabled={index >= chunks.length - 1}
                              title="向下合并"
                            >
                              <Merge className="w-4 h-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleSplitClick(chunk)}
                              title="拆分"
                            >
                              <Split className="w-4 h-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7 text-destructive hover:text-destructive"
                              onClick={() => handleDeleteClick(chunk)}
                              title="删除"
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          </>
                        )}
                      </div>
                    </CardHeader>
                    <CardContent className="pt-0">
                      {isEditing ? (
                        <div className="space-y-3">
                          <Textarea
                            value={editContent}
                            onChange={(e) => setEditContent(e.target.value)}
                            className="min-h-[150px] resize-y"
                            placeholder="输入切块内容..."
                          />
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={handleCancelEdit}
                            >
                              取消
                            </Button>
                            <Button
                              size="sm"
                              onClick={() => handleSaveEdit(chunk.id)}
                              disabled={savingChunkId === chunk.id}
                            >
                              {savingChunkId === chunk.id ? (
                                <Loader2 className="w-4 h-4 mr-1 animate-spin" />
                              ) : (
                                <Save className="w-4 h-4 mr-1" />
                              )}
                              保存
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <div className="text-sm text-foreground whitespace-pre-wrap">
                            {shouldTruncate
                              ? chunk.content.slice(0, 300) + '...'
                              : chunk.content}
                          </div>
                          {chunk.content.length > 300 && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-6 px-2 text-xs"
                              onClick={() => toggleExpand(chunk.id)}
                            >
                              {isExpanded ? (
                                <>
                                  <ChevronUp className="w-3 h-3 mr-1" />
                                  收起
                                </>
                              ) : (
                                <>
                                  <ChevronDown className="w-3 h-3 mr-1" />
                                  展开全部
                                </>
                              )}
                            </Button>
                          )}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )
              })}

              {chunks.length === 0 && !isLoading && (
                <div className="text-center py-12 text-muted-foreground">
                  <p>暂无切块数据</p>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Merge Confirmation Dialog */}
      <Dialog open={mergeDialogOpen} onOpenChange={setMergeDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认合并切块</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            确定要将这两个相邻的切块合并为一个吗？合并后会自动重新生成向量和关键词。
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setMergeDialogOpen(false)}
              disabled={isMerging}
            >
              取消
            </Button>
            <Button
              onClick={handleConfirmMerge}
              disabled={isMerging}
            >
              {isMerging && <Loader2 className="w-4 h-4 mr-1 animate-spin" />}
              确认合并
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Split Dialog */}
      <Dialog open={splitDialogOpen} onOpenChange={setSplitDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>拆分切块</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">第一部分</label>
              <Textarea
                value={splitPart1}
                onChange={(e) => setSplitPart1(e.target.value)}
                className="min-h-[120px]"
                placeholder="第一部分内容..."
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">第二部分</label>
              <Textarea
                value={splitPart2}
                onChange={(e) => setSplitPart2(e.target.value)}
                className="min-h-[120px]"
                placeholder="第二部分内容..."
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setSplitDialogOpen(false)}
              disabled={isSplitting}
            >
              取消
            </Button>
            <Button
              onClick={handleConfirmSplit}
              disabled={isSplitting || !splitPart1.trim() || !splitPart2.trim()}
            >
              {isSplitting && <Loader2 className="w-4 h-4 mr-1 animate-spin" />}
              确认拆分
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认删除切块</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            确定要删除这个切块吗？此操作不可撤销，删除后会自动重排后续切块的索引。
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={isDeleting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleConfirmDelete}
              disabled={isDeleting}
            >
              {isDeleting && <Loader2 className="w-4 h-4 mr-1 animate-spin" />}
              删除
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Scroll to Top Button */}
      {showScrollToTop && (
        <Button
          variant="secondary"
          size="icon"
          className="fixed bottom-8 right-8 h-12 w-12 rounded-full shadow-lg hover:shadow-xl hover:-translate-y-1 transition-all duration-300 z-50"
          onClick={scrollToTop}
          title="返回顶部"
        >
          <ArrowUp className="w-5 h-5" />
        </Button>
      )}
    </div>
  )
}
