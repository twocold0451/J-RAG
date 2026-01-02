import { useState, useEffect } from 'react'
import { User, Mail, Shield, Lock, RefreshCw, Eye, EyeOff, CheckCircle, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Separator } from '@/components/ui/separator'
import { useAuthStore } from '@/store/auth'
import { api } from '@/api/client'
import type { UserGroupDto } from '@/types'

export default function Settings() {
  const { user } = useAuthStore()
  const [groups, setGroups] = useState<UserGroupDto[]>([])
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const [showPasswords, setShowPasswords] = useState({
    current: false,
    new: false,
    confirm: false,
  })
  const [isLoading, setIsLoading] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  useEffect(() => {
    const loadUserGroups = async () => {
      try {
        const data = await api.getUserGroups()
        setGroups(data)
      } catch (err) {
        console.error('Failed to load user groups:', err)
      }
    }
    loadUserGroups()
  }, [])

  // groups 已经是当前用户的所属分组
  const generatePassword = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%'
    let password = ''
    for (let i = 0; i < 12; i++) {
      password += chars.charAt(Math.floor(Math.random() * chars.length))
    }
    return password
  }

  const handleChangePassword = async () => {
    if (!passwordForm.currentPassword) {
      setMessage({ type: 'error', text: '请输入当前密码' })
      return
    }
    if (passwordForm.newPassword.length < 8) {
      setMessage({ type: 'error', text: '新密码长度至少为8位' })
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setMessage({ type: 'error', text: '两次输入的密码不一致' })
      return
    }

    setIsLoading(true)
    setMessage(null)

    // Simulate API call
    setTimeout(() => {
      setIsLoading(false)
      setMessage({ type: 'success', text: '密码修改成功' })
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })

      // Clear success message after 3 seconds
      setTimeout(() => setMessage(null), 3000)
    }, 1000)
  }

  const handleGeneratePassword = () => {
    const newPassword = generatePassword()
    setPasswordForm(prev => ({ ...prev, newPassword, confirmPassword: newPassword }))
    setShowPasswords(prev => ({ ...prev, new: true, confirm: true }))
    setMessage({ type: 'success', text: '已生成随机密码' })
    setTimeout(() => setMessage(null), 3000)
  }

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">设置</h1>
        <p className="text-muted-foreground">管理您的账户设置</p>
      </div>

      {/* Profile Section */}
      <Card>
        <CardHeader>
          <CardTitle>个人信息</CardTitle>
          <CardDescription>您的账户基本信息</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center gap-6">
            <Avatar className="w-20 h-20">
              <AvatarFallback className="bg-primary text-white text-2xl">
                {user?.username?.[0] || 'U'}
              </AvatarFallback>
            </Avatar>
            <div>
              <div className="font-medium text-lg">{user?.username || '用户'}</div>
              <div className="text-muted-foreground">{user?.email || 'user@company.com'}</div>
              <div className="flex items-center gap-2 mt-2 flex-wrap">
                <Badge variant="outline">
                  <Shield className="w-3 h-3 mr-1" />
                  {user?.role === 'ADMIN' ? '管理员' : '普通用户'}
                </Badge>
                {groups.length > 0 && (
                  <div className="flex items-center gap-1">
                    <Users className="w-3 h-3 text-muted-foreground" />
                    {groups.map(group => (
                      <Badge key={group.id} variant="secondary">
                        {group.name}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>

          <Separator />

          <div className="grid grid-cols-2 gap-6">
            <div className="space-y-2">
              <Label className="flex items-center gap-2">
                <User className="w-4 h-4" />
                用户名
              </Label>
              <Input value={user?.username || ''} disabled />
            </div>
            <div className="space-y-2">
              <Label className="flex items-center gap-2">
                <Mail className="w-4 h-4" />
                邮箱
              </Label>
              <Input value={user?.email || ''} disabled />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Password Section */}
      <Card>
        <CardHeader>
          <CardTitle>修改密码</CardTitle>
          <CardDescription>定期更换密码可以保护账户安全</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {message && (
            <div className={`p-3 rounded-lg flex items-center gap-2 ${
              message.type === 'success'
                ? 'bg-green-50 text-green-600'
                : 'bg-red-50 text-red-600'
            }`}>
              <CheckCircle className="w-4 h-4" />
              {message.text}
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="current">当前密码</Label>
            <div className="relative">
              <Input
                id="current"
                type={showPasswords.current ? 'text' : 'password'}
                placeholder="输入当前密码"
                value={passwordForm.currentPassword}
                onChange={(e) => setPasswordForm(prev => ({ ...prev, currentPassword: e.target.value }))}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute right-1 top-1/2 -translate-y-1/2 h-8 w-8"
                onClick={() => setShowPasswords(prev => ({ ...prev, current: !prev.current }))}
              >
                {showPasswords.current ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="new" className="flex items-center gap-2">
              <Lock className="w-4 h-4" />
              新密码
            </Label>
            <div className="relative">
              <Input
                id="new"
                type={showPasswords.new ? 'text' : 'password'}
                placeholder="输入新密码"
                value={passwordForm.newPassword}
                onChange={(e) => setPasswordForm(prev => ({ ...prev, newPassword: e.target.value }))}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute right-1 top-1/2 -translate-y-1/2 h-8 w-8"
                onClick={() => setShowPasswords(prev => ({ ...prev, new: !prev.new }))}
              >
                {showPasswords.new ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="confirm">确认新密码</Label>
            <div className="relative">
              <Input
                id="confirm"
                type={showPasswords.confirm ? 'text' : 'password'}
                placeholder="再次输入新密码"
                value={passwordForm.confirmPassword}
                onChange={(e) => setPasswordForm(prev => ({ ...prev, confirmPassword: e.target.value }))}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute right-1 top-1/2 -translate-y-1/2 h-8 w-8"
                onClick={() => setShowPasswords(prev => ({ ...prev, confirm: !prev.confirm }))}
              >
                {showPasswords.confirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
            </div>
          </div>

          <div className="flex items-center gap-4 pt-2">
            <Button
              onClick={handleGeneratePassword}
              variant="outline"
            >
              <RefreshCw className="w-4 h-4 mr-2" />
              生成随机密码
            </Button>
            <span className="text-sm text-muted-foreground">
              生成12位包含大小写字母、数字和特殊字符的强密码
            </span>
          </div>

          <Separator />

          <div className="flex justify-end">
            <Button onClick={handleChangePassword} disabled={isLoading}>
              {isLoading ? '保存中...' : '保存修改'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
