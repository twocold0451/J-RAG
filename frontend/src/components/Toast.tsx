import { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import { CheckCircle2, XCircle, Info, X } from 'lucide-react'
import { cn } from '@/lib/utils'

type ToastType = 'success' | 'error' | 'info'

interface Toast {
  id: number
  message: string
  type: ToastType
}

interface ToastContextType {
  toasts: Toast[]
  showToast: (message: string, type?: ToastType) => void
  hideToast: (id: number) => void
}

const ToastContext = createContext<ToastContextType | undefined>(undefined)

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider')
  }
  return context
}

const toastIcons = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
}

const toastStyles = {
  success: "bg-white border-l-4 border-green-500 shadow-lg",
  error: "bg-white border-l-4 border-red-500 shadow-lg",
  info: "bg-white border-l-4 border-primary shadow-lg",
}

const iconStyles = {
  success: "text-green-500",
  error: "text-red-500",
  info: "text-primary",
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const showToast = useCallback((message: string, type: ToastType = 'info') => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id))
    }, 3000)
  }, [])

  const hideToast = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id))
  }, [])

  return (
    <ToastContext.Provider value={{ toasts, showToast, hideToast }}>
      {children}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-3 w-full max-w-md">
        {toasts.map(toast => {
          const Icon = toastIcons[toast.type]
          return (
            <div
              key={toast.id}
              className={cn(
                "flex items-center gap-3 px-4 py-3.5 rounded-lg animate-in slide-in-from-top-5 fade-in duration-300",
                toastStyles[toast.type]
              )}
            >
              <Icon className={cn("w-5 h-5 shrink-0", iconStyles[toast.type])} />
              <span className="flex-1 text-sm font-medium text-foreground">
                {toast.message}
              </span>
              <button
                onClick={() => hideToast(toast.id)}
                className="text-muted-foreground hover:text-foreground transition-colors p-0.5 rounded hover:bg-muted"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}
