import { useState, useEffect } from 'react'
import { Plus, User, Trash2, Shield, Mail, Key, Loader2, KeyRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { api } from '@/api/client'
import { useToast } from '@/components/Toast'
import type { AdminUserResponse, UserGroupDto } from '@/types'

export default function Users() {
  const { showToast } = useToast()
  const [users, setUsers] = useState<AdminUserResponse[]>([])
  const [groups, setGroups] = useState<UserGroupDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isAddOpen, setIsAddOpen] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [newUser, setNewUser] = useState({
    username: '',
    email: '',
    role: 'USER' as 'USER' | 'ADMIN',
    groupIds: [] as number[],
  })
  const [createdUser, setCreatedUser] = useState<{ username: string; email: string; password: string } | null>(null)

  // Reset password state
  const [isResetOpen, setIsResetOpen] = useState(false)
  const [resettingUser, setResettingUser] = useState<AdminUserResponse | null>(null)
  const [isResetting, setIsResetting] = useState(false)
  const [resetResult, setResetResult] = useState<{ username: string; newPassword: string } | null>(null)

  // Load data on mount
  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setIsLoading(true)
    try {
      const [usersData, groupsData] = await Promise.all([
        api.getUsers(),
        api.getGroups().catch(() => []),
      ])
      setUsers(usersData)
      setGroups(groupsData)
    } catch (err: any) {
      console.error('Failed to load data:', err)
    } finally {
      setIsLoading(false)
    }
  }

  const handleAddUser = async () => {
    if (!newUser.username.trim() || !newUser.email.trim()) return

    setIsSaving(true)
    try {
      const created = await api.createUser({
        username: newUser.username,
        email: newUser.email,
        role: newUser.role,
        groupIds: newUser.groupIds,
      })
      setCreatedUser({
        username: newUser.username,
        email: newUser.email,
        password: created.initialPassword || '',
      })
      await loadData()
    } catch (err: any) {
      console.error('Failed to create user:', err)
      showToast(`创建失败: ${err.message}`, 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const handleCloseAddDialog = () => {
    setIsAddOpen(false)
    setNewUser({ username: '', email: '', role: 'USER', groupIds: [] })
    setCreatedUser(null)
  }

  const handleDeleteUser = async (id: number) => {
    try {
      await api.deleteUser(id)
      setUsers(prev => prev.filter(u => u.id !== id))
    } catch (err: any) {
      console.error('Failed to delete user:', err)
      showToast(`删除失败: ${err.message}`, 'error')
    }
  }

  const openResetDialog = (user: AdminUserResponse) => {
    setResettingUser(user)
    setResetResult(null)
    setIsResetOpen(true)
  }

  const handleResetPassword = async () => {
    if (!resettingUser) return

    setIsResetting(true)
    try {
      const result = await api.resetPassword(resettingUser.id)
      setResetResult({
        username: resettingUser.username,
        newPassword: result.newPassword,
      })
    } catch (err: any) {
      console.error('Failed to reset password:', err)
      showToast(`重置失败: ${err.message}`, 'error')
      setIsResetOpen(false)
    } finally {
      setIsResetting(false)
    }
  }

  const closeResetDialog = () => {
    setIsResetOpen(false)
    setResettingUser(null)
    setResetResult(null)
  }

  const toggleGroup = (groupId: number) => {
    setNewUser(prev => ({
      ...prev,
      groupIds: prev.groupIds.includes(groupId)
        ? prev.groupIds.filter(id => id !== groupId)
        : [...prev.groupIds, groupId],
    }))
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('zh-CN')
  }

  const getGroupNames = (groupIds: number[] | undefined) => {
    if (!groupIds || groupIds.length === 0) return []
    return groupIds.map(id => groups.find(g => g.id === id)?.name || `组${id}`)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">用户管理</h1>
          <p className="text-muted-foreground">管理系统用户和权限</p>
        </div>
        <Button onClick={() => setIsAddOpen(true)}>
          <Plus className="w-4 h-4 mr-2" />
          添加用户
        </Button>
      </div>

      {/* User Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-4">
            <div className="text-2xl font-bold">{users.length}</div>
            <div className="text-sm text-muted-foreground">用户总数</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-2xl font-bold">{users.filter(u => u.role === 'ADMIN').length}</div>
            <div className="text-sm text-muted-foreground">管理员</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-2xl font-bold">{users.filter(u => u.role === 'USER').length}</div>
            <div className="text-sm text-muted-foreground">普通用户</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <div className="text-2xl font-bold">{users.reduce((acc, u) => acc + (u.groupIds?.length || 0), 0)}</div>
            <div className="text-sm text-muted-foreground">用户组关联</div>
          </CardContent>
        </Card>
      </div>

      {/* User List */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
          <span className="ml-2 text-muted-foreground">加载中...</span>
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>用户列表</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {users.map((user) => (
                <div
                  key={user.id}
                  className="flex items-center gap-4 p-4 rounded-lg border hover:border-primary/50 hover:bg-muted/50 transition-colors"
                >
                  <Avatar className="w-11 h-11">
                    <AvatarFallback className="bg-primary text-white">
                      {user.username[0]?.toUpperCase() || 'U'}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{user.username}</span>
                      {user.role === 'ADMIN' && (
                        <Badge variant="default" className="bg-primary">
                          <Shield className="w-3 h-3 mr-1" />
                          管理员
                        </Badge>
                      )}
                    </div>
                    <div className="text-sm text-muted-foreground flex items-center gap-1 mt-1">
                      <Mail className="w-3.5 h-3.5" />
                      {user.email}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {getGroupNames(user.groupIds).map(group => (
                      <Badge key={group} variant="outline">{group}</Badge>
                    ))}
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {formatDate(user.createdAt)}
                  </div>
                  {user.id !== 1 && (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 hover:bg-amber-100 hover:text-amber-600"
                        onClick={() => openResetDialog(user)}
                        title="重置密码"
                      >
                        <KeyRound className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => handleDeleteUser(user.id)}
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </>
                  )}
                </div>
              ))}
              {users.length === 0 && (
                <div className="text-center py-12 text-muted-foreground">
                  <User className="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p>暂无用户</p>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Add User Dialog */}
      <Dialog open={isAddOpen} onOpenChange={(open) => {
        if (!open) handleCloseAddDialog()
        else setIsAddOpen(true)
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加用户</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            {!createdUser ? (
              <>
                <div className="space-y-2">
                  <Label htmlFor="username">用户名</Label>
                  <Input
                    id="username"
                    placeholder="输入用户名"
                    value={newUser.username}
                    onChange={(e) => setNewUser(prev => ({ ...prev, username: e.target.value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="email">邮箱</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="输入邮箱地址"
                    value={newUser.email}
                    onChange={(e) => setNewUser(prev => ({ ...prev, email: e.target.value }))}
                  />
                </div>
                <div className="space-y-2">
                  <Label>角色</Label>
                  <Select
                    value={newUser.role}
                    onValueChange={(value) => setNewUser(prev => ({ ...prev, role: value as 'ADMIN' | 'USER' }))}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="USER">普通用户</SelectItem>
                      <SelectItem value="ADMIN">管理员</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>用户组</Label>
                  <div className="flex flex-wrap gap-2">
                    {groups.map(group => (
                      <div key={group.id} className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant={newUser.groupIds.includes(group.id) ? "default" : "outline"}
                          size="sm"
                          onClick={() => toggleGroup(group.id)}
                        >
                          {group.name}
                        </Button>
                      </div>
                    ))}
                    {groups.length === 0 && (
                      <span className="text-sm text-muted-foreground">暂无可用用户组</span>
                    )}
                  </div>
                </div>
              </>
            ) : (
              <div className="space-y-4">
                <div className="p-4 bg-muted rounded-lg space-y-3">
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">用户名</div>
                    <div className="font-mono font-medium select-all">{createdUser?.username}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">邮箱</div>
                    <div className="font-mono font-medium select-all">{createdUser?.email}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">密码</div>
                    <div className="font-mono font-bold text-lg select-all">{createdUser?.password}</div>
                  </div>
                </div>
                <p className="text-sm text-muted-foreground">
                  请复制以上信息并妥善保存，密码将不会再次显示。用户可在设置中修改密码。
                </p>
                <Button
                  variant="outline"
                  className="w-full"
                  onClick={() => {
                    if (createdUser) {
                      const text = `用户名: ${createdUser.username}\n邮箱: ${createdUser.email}\n密码: ${createdUser.password}`
                      navigator.clipboard.writeText(text)
                    }
                  }}
                >
                  <Key className="w-4 h-4 mr-2" />
                  复制全部信息
                </Button>
              </div>
            )}
          </div>
          <DialogFooter>
            {!createdUser ? (
              <>
                <Button variant="outline" onClick={handleCloseAddDialog}>取消</Button>
                <Button
                  onClick={handleAddUser}
                  disabled={!newUser.username.trim() || !newUser.email.trim() || isSaving}
                >
                  {isSaving ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      创建中...
                    </>
                  ) : (
                    '添加并生成密码'
                  )}
                </Button>
              </>
            ) : (
              <Button onClick={handleCloseAddDialog}>完成</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reset Password Dialog */}
      <Dialog open={isResetOpen} onOpenChange={(open) => {
        if (!open) closeResetDialog()
        else setIsResetOpen(true)
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>重置用户密码</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            {!resetResult ? (
              <div className="space-y-3">
                <p className="text-sm text-muted-foreground">
                  确定要重置用户 <strong className="text-foreground">{resettingUser?.username}</strong> 的密码吗？
                </p>
                <p className="text-sm text-muted-foreground">
                  重置后系统将生成一个 8 位随机密码并显示给您。
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg space-y-3">
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">用户名</div>
                    <div className="font-mono font-medium select-all">{resetResult.username}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">新密码</div>
                    <div className="font-mono font-bold text-lg select-all text-amber-600">{resetResult.newPassword}</div>
                  </div>
                </div>
                <p className="text-sm text-muted-foreground">
                  请复制以上新密码并妥善保存，密码将不会再次显示。
                </p>
                <Button
                  variant="outline"
                  className="w-full"
                  onClick={() => {
                    navigator.clipboard.writeText(resetResult.newPassword)
                    showToast('密码已复制到剪贴板', 'success')
                  }}
                >
                  <Key className="w-4 h-4 mr-2" />
                  复制密码
                </Button>
              </div>
            )}
          </div>
          <DialogFooter>
            {!resetResult ? (
              <>
                <Button variant="outline" onClick={closeResetDialog}>取消</Button>
                <Button
                  onClick={handleResetPassword}
                  disabled={isResetting}
                >
                  {isResetting ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      重置中...
                    </>
                  ) : (
                    '确认重置'
                  )}
                </Button>
              </>
            ) : (
              <Button onClick={closeResetDialog}>完成</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
