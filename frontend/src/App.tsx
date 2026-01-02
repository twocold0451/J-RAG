import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from '@/components/Layout'
import { ToastProvider } from '@/components/Toast'
import { useAuthStore } from '@/store/auth'
import { useEffect } from 'react'

// Placeholder pages
import Dashboard from '@/pages/Dashboard'
import Chat from '@/pages/Chat'
import Documents from '@/pages/Documents'
import Templates from '@/pages/Templates'
import Groups from '@/pages/Groups'
import Users from '@/pages/Users'
import Settings from '@/pages/Settings'
import Login from '@/pages/Login'
import TeamChats from '@/pages/TeamChats'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore()
  if (isLoading) {
    return <div className="flex items-center justify-center h-screen">加载中...</div>
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return <Layout>{children}</Layout>
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading, user } = useAuthStore()
  if (isLoading) {
    return <div className="flex items-center justify-center h-screen">加载中...</div>
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/chat" replace />
  }
  return <Layout>{children}</Layout>
}

function AppRoutes() {
  const loadUser = useAuthStore(state => state.loadUser)

  useEffect(() => {
    loadUser()
  }, [loadUser])

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/dashboard" element={<AdminRoute><Dashboard /></AdminRoute>} />
      <Route path="/chat" element={<ProtectedRoute><Chat /></ProtectedRoute>} />
      <Route path="/documents" element={<AdminRoute><Documents /></AdminRoute>} />
      <Route path="/templates" element={<AdminRoute><Templates /></AdminRoute>} />
      <Route path="/team-chats" element={<AdminRoute><TeamChats /></AdminRoute>} />
      <Route path="/groups" element={<AdminRoute><Groups /></AdminRoute>} />
      <Route path="/users" element={<AdminRoute><Users /></AdminRoute>} />
      <Route path="/settings" element={<ProtectedRoute><Settings /></ProtectedRoute>} />
      <Route path="/" element={<Navigate to="/chat" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AppRoutes />
      </ToastProvider>
    </BrowserRouter>
  )
}
