import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  MessageCircle,
  FileText,
  ClipboardList,
  Users,
  Users2,
  LogOut,
  Menu,
  MessageSquareDashed,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet'

interface LayoutProps {
  children: React.ReactNode
}

const workNavItems = [
  { path: '/dashboard', label: '仪表盘', icon: LayoutDashboard },
]

const knowledgeNavItems = [
  { path: '/documents', label: '文档管理', icon: FileText },
  { path: '/templates', label: '对话模板', icon: ClipboardList },
]

const adminNavItems = [
  { path: '/team-chats', label: '团队对话', icon: MessageSquareDashed },
  { path: '/users', label: '用户管理', icon: Users },
  { path: '/groups', label: '用户分组', icon: Users2 },
]

// AI对话对所有用户可见
const chatNavItems = [
  { path: '/chat', label: 'AI 对话', icon: MessageCircle },
]

export function Layout({ children }: LayoutProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()

  const isAdmin = user?.role === 'ADMIN'

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const NavGroup = ({ title, items }: { title: string; items: any[] }) => (
    <div className="mb-6">
      <div className="text-[10px] uppercase font-bold text-sidebar-foreground/50 px-4 mb-2 tracking-wider">
        {title}
      </div>
      <div className="space-y-1">
        {items.map((item) => {
          const Icon = item.icon
          const isActive = location.pathname === item.path
          return (
            <Link
              key={item.path}
              to={item.path}
              className={cn(
                'flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 group relative',
                isActive
                  ? 'bg-sidebar-active text-primary-foreground shadow-md'
                  : 'hover:bg-sidebar-active/10 hover:text-sidebar-active'
              )}
            >
              <Icon className={cn("w-[18px] h-[18px] transition-colors", isActive ? "text-primary-foreground" : "text-sidebar-foreground/70 group-hover:text-sidebar-active")} />
              <span className="flex-1">{item.label}</span>
              {item.badge && (
                <span className={cn(
                  "text-[10px] px-1.5 py-0.5 rounded-full font-bold",
                  isActive 
                    ? "bg-white/20 text-white" 
                    : "bg-orange-500 text-white shadow-sm"
                )}>
                  {item.badge}
                </span>
              )}
              {isActive && <div className="absolute right-2 w-1.5 h-1.5 rounded-full bg-primary-foreground animate-pulse" />}
            </Link>
          )
        })}
      </div>
    </div>
  )

  const SidebarContent = () => (
    <div className="flex flex-col h-full text-sidebar-foreground">
      <div className="p-6">
        <div className="flex items-center gap-3 px-2">
          <div className="w-8 h-8 bg-primary rounded-xl flex items-center justify-center text-lg shadow-lg shadow-primary/20">
            📚
          </div>
          <span className="font-bold text-lg tracking-tight">{import.meta.env.VITE_APP_NAME || '企业知识库'}</span>
        </div>
      </div>

      <nav className="flex-1 px-4 overflow-y-auto">
        {isAdmin && <NavGroup title="工作台" items={workNavItems} />}
        <NavGroup title="AI 对话" items={chatNavItems} />
        {isAdmin && <NavGroup title="知识管理" items={knowledgeNavItems} />}
        {isAdmin && <NavGroup title="管理" items={adminNavItems} />}
      </nav>

      <div className="p-4 mt-auto">
        <div className="bg-sidebar-border/30 rounded-2xl p-4 border border-sidebar-border/50 backdrop-blur-sm">
          <div
            className="flex items-center gap-3 mb-3 cursor-pointer hover:bg-sidebar-accent/50 rounded-xl p-2 -p-2 transition-colors"
            onClick={() => navigate('/settings')}
          >
            <Avatar className="h-9 w-9 border-2 border-sidebar-active/20">
              <AvatarFallback className="bg-sidebar-active text-white font-medium">
                {user?.username?.[0] || 'U'}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-semibold truncate">
                {user?.username || '用户'}
              </div>
              <div className="text-xs text-sidebar-foreground/60 truncate">
                {user?.email || 'user@example.com'}
              </div>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start text-sidebar-foreground/70 hover:text-destructive hover:bg-destructive/10"
            onClick={handleLogout}
          >
            <LogOut className="w-4 h-4 mr-2" />
            退出登录
          </Button>
        </div>
      </div>
    </div>
  )

  return (
    <div className="flex h-screen w-full bg-muted/30 p-3 gap-3 overflow-hidden font-sans">
      {/* Floating Sidebar (Desktop) */}
      <aside className="hidden md:flex w-[280px] flex-col rounded-3xl bg-sidebar border border-sidebar-border shadow-xl shadow-sidebar/5 transition-all duration-300">
        <SidebarContent />
      </aside>

      {/* Main Content Island */}
      <main className="flex-1 flex flex-col rounded-3xl bg-background border shadow-sm overflow-hidden relative">
        {/* Top Bar */}
        <header className="h-16 flex items-center justify-between px-6 border-b bg-background/50 backdrop-blur-xl sticky top-0 z-20">
          <div className="flex items-center gap-4">
            {/* Mobile Sidebar Trigger */}
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="md:hidden">
                  <Menu className="w-5 h-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="p-0 w-[280px] bg-sidebar border-r-sidebar-border">
                <SidebarContent />
              </SheetContent>
            </Sheet>

            <div className="hidden md:flex items-center gap-2 text-sm text-muted-foreground/80">
              <span className="hover:text-foreground transition-colors cursor-pointer">首页</span>
              <span className="text-muted-foreground/40">/</span>
              <span className="text-foreground font-medium bg-muted/50 px-2 py-0.5 rounded-md">
                {[...workNavItems, ...knowledgeNavItems, ...adminNavItems].find((i) => i.path === location.pathname)?.label || '工作台'}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {/* Right side of header remains empty for now or for user profile if needed elsewhere */}
          </div>
        </header>

        {/* Scrollable Content Area */}
        <div className="flex-1 overflow-y-auto scroll-smooth">
          <div className="p-6 max-w-7xl mx-auto w-full">
            {children}
          </div>
        </div>
      </main>
    </div>
  )
}
