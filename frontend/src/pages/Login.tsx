import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { BookOpen, Loader2 } from 'lucide-react'

// Rotating Ring
function RotatingRing({ className, reverse = false }: { className: string; reverse?: boolean }) {
  return (
    <div className={`absolute rounded-full border border-emerald-300/20 ${className}`}>
      <div
        className="w-full h-full rounded-full border border-emerald-400/30 border-t-transparent animate-spin"
        style={{ animationDirection: reverse ? 'reverse' : 'normal', animationDuration: '12s' }}
      />
    </div>
  )
}

export default function Login() {
  const navigate = useNavigate()
  const { setAuth } = useAuthStore()
  const [formData, setFormData] = useState({ username: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const res = await api.login(formData.username, formData.password)
      setAuth(res, res.token || '')
      navigate('/dashboard')
    } catch (err: any) {
      setError(err.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 overflow-hidden bg-[#f0fdf4]">
      {/* Background Layers */}
      <div className="absolute inset-0">
        {/* Green Gradient */}
        <div className="absolute inset-0 bg-gradient-to-br from-[#ecfdf5] via-[#f0fdf4] to-[#dcfce7]" />

        {/* Green Light Orbs */}
        <div className="absolute top-[-15%] left-[-10%] w-[600px] h-[600px] bg-emerald-200/40 rounded-full blur-[100px] animate-pulse-slow" />
        <div className="absolute bottom-[-15%] right-[-10%] w-[500px] h-[500px] bg-teal-200/30 rounded-full blur-[100px] animate-pulse-slow" style={{ animationDelay: '1.5s' }} />
        <div className="absolute top-[30%] left-[40%] w-[400px] h-[400px] bg-green-100/40 rounded-full blur-[80px] animate-pulse-slow" style={{ animationDelay: '3s' }} />

        {/* Rotating Rings */}
        <RotatingRing className="top-[5%] left-[5%] w-[250px] h-[250px]" />
        <RotatingRing className="bottom-[10%] right-[3%] w-[200px] h-[200px]" reverse />
        <RotatingRing className="top-[30%] right-[15%] w-[120px] h-[120px]" />

        {/* Subtle Grid */}
        <div className="absolute inset-0 bg-[linear-gradient(rgba(16,185,129,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(16,185,129,0.03)_1px,transparent_1px)] bg-[size:60px_60px]" />

        {/* Floating Particles */}
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={i}
            className="absolute bg-emerald-300/40 rounded-full animate-float"
            style={{
              width: `${2 + Math.random() * 4}px`,
              height: `${2 + Math.random() * 4}px`,
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              animationDelay: `${Math.random() * 5}s`,
              animationDuration: `${5 + Math.random() * 3}s`,
            }}
          />
        ))}
      </div>

      {/* Main Container */}
      <div className="relative z-10 h-full flex flex-col items-center justify-center p-6">
        {/* Logo */}
        <div className="text-center mb-8 group cursor-pointer">
          <div className="relative inline-block mb-6">
            <div className="absolute inset-0 bg-emerald-400/40 blur-2xl rounded-full animate-pulse" />
            <div className="absolute inset-0 bg-gradient-to-br from-emerald-300/30 to-transparent rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-500 animate-rotate" style={{ animationDuration: '8s' }} />
            <div className="relative w-20 h-20 bg-gradient-to-br from-emerald-400 to-emerald-600 rounded-2xl flex items-center justify-center shadow-2xl shadow-emerald-500/30 transform group-hover:scale-110 group-hover:rotate-3 transition-all duration-500">
              <BookOpen className="w-10 h-10 text-white" />
            </div>
          </div>

          <h1 className="text-4xl font-bold text-slate-800 mb-2 tracking-tight">
            J-RAG
          </h1>
          <p className="text-slate-500 text-sm">
            智能知识引擎
          </p>
        </div>

        {/* Form Card */}
        <div className="w-full max-w-sm bg-white/80 backdrop-blur-2xl rounded-3xl border border-slate-200/50 p-6 shadow-2xl hover:bg-white/90 hover:shadow-[0_8px_40px_rgba(16,185,129,0.15)] transition-all duration-500">
          {/* Title */}
          <div className="text-center mb-6">
            <h2 className="text-2xl font-semibold text-slate-800">
              欢迎登录
            </h2>
            <p className="text-sm text-slate-500 mt-1">
              输入您的账户信息
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="group/input">
              <Label className="text-xs uppercase tracking-wider text-slate-500 mb-2 block group-focus/input:text-emerald-600 transition-colors">
                用户名
              </Label>
              <input
                type="text"
                className="w-full h-11 bg-slate-50 border border-slate-200 rounded-xl px-4 text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-emerald-400/50 focus:bg-white focus:shadow-[0_0_20px_rgba(16,185,129,0.15)] transition-all duration-300"
                disabled={loading}
                value={formData.username}
                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                placeholder="请输入用户名"
                required
              />
            </div>

            <div className="group/input">
              <Label className="text-xs uppercase tracking-wider text-slate-500 mb-2 block group-focus/input:text-emerald-600 transition-colors">
                密码
              </Label>
              <input
                type="password"
                className="w-full h-11 bg-slate-50 border border-slate-200 rounded-xl px-4 text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-emerald-400/50 focus:bg-white focus:shadow-[0_0_20px_rgba(16,185,129,0.15)] transition-all duration-300"
                disabled={loading}
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                placeholder="请输入密码"
                required
              />
            </div>

            {error && (
              <div className="p-2.5 bg-red-50 border border-red-100 rounded-xl text-sm text-red-500 text-center">
                {error}
              </div>
            )}

            <Button
              type="submit"
              disabled={loading}
              className="w-full h-11 bg-gradient-to-r from-emerald-500 to-emerald-600 hover:from-emerald-400 hover:to-emerald-500 hover:shadow-[0_0_30px_rgba(16,185,129,0.4)] text-white font-medium rounded-xl shadow-lg shadow-emerald-500/20 transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]"
            >
              {loading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  登录中...
                </span>
              ) : (
                '进入系统'
              )}
            </Button>
          </form>

          {/* Footer Links */}
          <div className="mt-5 flex items-center justify-center gap-4 text-xs">
            <button className="text-slate-500 hover:text-emerald-600 transition-colors">
              忘记密码？
            </button>
            <span className="text-slate-300">|</span>
            <button className="text-slate-500 hover:text-emerald-600 transition-colors">
              联系管理员
            </button>
          </div>
        </div>

        {/* GitHub Link */}
        <a
          href="https://github.com/twocold0451/J-RAG"
          target="_blank"
          rel="noopener noreferrer"
          className="absolute bottom-4 text-xs text-slate-400 hover:text-emerald-600 transition-colors uppercase tracking-widest"
        >
          github.com/twocold0451/J-RAG
        </a>
      </div>

      <style>{`
        * {
          scrollbar-width: none;
          -ms-overflow-style: none;
        }
        *::-webkit-scrollbar {
          display: none;
        }

        @keyframes float {
          0%, 100% { transform: translateY(0); opacity: 0.4; }
          50% { transform: translateY(-15px); opacity: 0.7; }
        }
        @keyframes pulse-slow {
          0%, 100% { opacity: 0.6; transform: scale(1); }
          50% { opacity: 0.9; transform: scale(1.1); }
        }
        @keyframes rotate {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        .animate-float { animation: float 6s ease-in-out infinite; }
        .animate-pulse-slow { animation: pulse-slow 5s ease-in-out infinite; }
        .animate-rotate { animation: rotate 10s linear infinite; }
      `}</style>
    </div>
  )
}