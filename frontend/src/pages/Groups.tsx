import { useState, useEffect } from 'react'
import { Plus, Users, Trash2, UserPlus, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Checkbox } from '@/components/ui/checkbox'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { UserGroupDto, AdminUserResponse } from '@/types'

export default function Groups() {
  const { showToast } = useToast()
  const [groups, setGroups] = useState<UserGroupDto[]>([])
  const [users, setUsers] = useState<AdminUserResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isMembersOpen, setIsMembersOpen] = useState(false)
  const [selectedGroup, setSelectedGroup] = useState<UserGroupDto | null>(null)
  const [newGroupName, setNewGroupName] = useState('')
  const [newGroupDescription, setNewGroupDescription] = useState('')
  const [selectedMembers, setSelectedMembers] = useState<number[]>([])
  const [isSaving, setIsSaving] = useState(false)

  // Load data on mount
  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setIsLoading(true)
    try {
      const [groupsData, usersData] = await Promise.all([
        api.getGroups(),
        api.getUsers().catch(() => []), // Users might not be available
      ])
      setGroups(groupsData)
      setUsers(usersData)
    } catch (err: any) {
      console.error('Failed to load data:', err)
    } finally {
      setIsLoading(false)
    }
  }

  const handleCreateGroup = async () => {
    if (!newGroupName.trim()) return

    setIsSaving(true)
    try {
      const created = await api.createGroup({
        name: newGroupName,
        description: newGroupDescription,
        userIds: selectedMembers,
      })
      setGroups(prev => [...prev, created])
      setIsCreateOpen(false)
      setNewGroupName('')
      setNewGroupDescription('')
      setSelectedMembers([])
    } catch (err: any) {
      console.error('Failed to create group:', err)
      showToast(`创建失败: ${err.message}`, 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDeleteGroup = async (id: number) => {
    try {
      await api.deleteGroup(id)
      setGroups(prev => prev.filter(g => g.id !== id))
    } catch (err: any) {
      console.error('Failed to delete group:', err)
      showToast(`删除失败: ${err.message}`, 'error')
    }
  }

  const handleOpenMembers = async (group: UserGroupDto) => {
    setSelectedGroup(group)
    setIsMembersOpen(true)
    try {
      const memberIds = await api.getGroupMembers(group.id)
      setSelectedMembers(memberIds)
    } catch (err) {
      console.error('Failed to load members:', err)
      setSelectedMembers([])
    }
  }

  const handleSaveMembers = async () => {
    if (!selectedGroup) return

    setIsSaving(true)
    try {
      await api.updateGroup(selectedGroup.id, {
        name: selectedGroup.name,
        userIds: selectedMembers,
      })
      await loadData()
      setIsMembersOpen(false)
    } catch (err: any) {
      console.error('Failed to update members:', err)
      showToast(`保存失败: ${err.message}`, 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const toggleMember = (userId: number) => {
    setSelectedMembers(prev =>
      prev.includes(userId)
        ? prev.filter(id => id !== userId)
        : [...prev, userId]
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">用户分组</h1>
          <p className="text-muted-foreground">管理用户分组，控制模板访问权限</p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)}>
          <Plus className="w-4 h-4 mr-2" />
          新建分组
        </Button>
      </div>

      {/* Group List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
          <span className="ml-2 text-muted-foreground">加载中...</span>
        </div>
      ) : (
        <div className="grid grid-cols-4 gap-6">
          {groups.map((group) => (
            <Card key={group.id}>
              <CardHeader>
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
                      <Users className="w-5 h-5 text-primary" />
                    </div>
                    <div>
                      <CardTitle className="text-lg">{group.name}</CardTitle>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      onClick={() => handleOpenMembers(group)}
                    >
                      <UserPlus className="w-4 h-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 hover:bg-destructive/10 hover:text-destructive"
                      onClick={() => handleDeleteGroup(group.id)}
                    >
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">成员数量</span>
                    <span className="font-medium">{group.memberCount || 0} 人</span>
                  </div>

                  {group.description && (
                    <p className="text-xs text-muted-foreground pt-2 border-t">
                      {group.description}
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}

          {/* Empty State */}
          {groups.length === 0 && (
            <div className="col-span-4 text-center py-12">
              <Users className="w-12 h-12 mx-auto text-muted-foreground/30 mb-3" />
              <p className="text-muted-foreground">暂无用户分组</p>
              <Button className="mt-4" onClick={() => setIsCreateOpen(true)}>
                <Plus className="w-4 h-4 mr-2" />
                创建第一个分组
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Create Group Dialog */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建用户分组</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="name">分组名称</Label>
              <Input
                id="name"
                placeholder="输入分组名称，如：技术部"
                value={newGroupName}
                onChange={(e) => setNewGroupName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">分组描述（可选）</Label>
              <Input
                id="description"
                placeholder="描述该分组的用途"
                value={newGroupDescription}
                onChange={(e) => setNewGroupDescription(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>初始成员（可选）</Label>
              <div className="border rounded-lg divide-y max-h-60 overflow-y-auto">
                {users.map(user => (
                  <div
                    key={user.id}
                    className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer"
                    onClick={() => toggleMember(user.id)}
                  >
                    <Checkbox
                      checked={selectedMembers.includes(user.id)}
                      onCheckedChange={() => toggleMember(user.id)}
                    />
                    <div className="flex-1 min-w-0">
                      <div className="font-medium truncate">{user.username}</div>
                      <div className="text-xs text-muted-foreground truncate">{user.email}</div>
                    </div>
                  </div>
                ))}
                {users.length === 0 && (
                  <div className="p-4 text-center text-sm text-muted-foreground">
                    暂无可用用户
                  </div>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                已选择 {selectedMembers.length} 个成员
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateOpen(false)}>取消</Button>
            <Button
              onClick={handleCreateGroup}
              disabled={!newGroupName.trim() || isSaving}
            >
              {isSaving ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  创建中...
                </>
              ) : (
                '创建'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Members Dialog */}
      <Dialog open={isMembersOpen} onOpenChange={setIsMembersOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>编辑成员 - {selectedGroup?.name}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="border rounded-lg divide-y max-h-80 overflow-y-auto">
              {users.map(user => (
                <div
                  key={user.id}
                  className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer"
                  onClick={() => toggleMember(user.id)}
                >
                  <Checkbox
                    checked={selectedMembers.includes(user.id)}
                    onCheckedChange={() => toggleMember(user.id)}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="font-medium truncate">{user.username}</div>
                    <div className="text-xs text-muted-foreground truncate">{user.email}</div>
                  </div>
                </div>
              ))}
              {users.length === 0 && (
                <div className="p-4 text-center text-sm text-muted-foreground">
                  暂无可用用户
                </div>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              已选择 {selectedMembers.length} 个成员
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsMembersOpen(false)}>取消</Button>
            <Button
              onClick={handleSaveMembers}
              disabled={isSaving}
            >
              {isSaving ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  保存中...
                </>
              ) : (
                '保存'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
