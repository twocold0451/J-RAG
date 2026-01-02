import { useEffect, useRef, useCallback, useState } from 'react'

export interface DocumentUpdate {
  documentId: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  errorMessage?: string
}

export function useDocumentProgress(userId: string | number | null) {
  const [updates, setUpdates] = useState<Map<string, DocumentUpdate>>(new Map())
  const clientRef = useRef<any>(null)
  const subscriptionRef = useRef<any>(null)

  // 获取用户 ID
  const getUserId = useCallback(() => {
    if (userId !== null) return String(userId)
    const token = localStorage.getItem('token')
    if (!token) return null
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      return String(payload.userId || payload.sub)
    } catch {
      return null
    }
  }, [userId])

  useEffect(() => {
    const uid = getUserId()
    if (!uid) return

    let StompClient: any
    let sockjs: any

    const initWebSocket = async () => {
      try {
        const SockJS = (await import('sockjs-client')).default
        const Stomp = (await import('stompjs')).default

        const wsUrl = `${window.location.origin}/ws`
        sockjs = new SockJS(wsUrl)
        StompClient = Stomp.over(sockjs)

        clientRef.current = StompClient

        StompClient.connect(
          {},
          () => {
            subscriptionRef.current = StompClient.subscribe(
              `/user/${uid}/queue/document-updates`,
              (message: any) => {
                try {
                  const update = JSON.parse(message.body) as DocumentUpdate
                  setUpdates((prev) => {
                    const next = new Map(prev)
                    next.set(update.documentId, update)
                    return next
                  })
                } catch (err) {
                  console.error('Failed to parse document update:', err)
                }
              }
            )
          },
          () => {} // Connected silently
        )
      } catch (err) {
        // WebSocket init silently failed, progress won't be tracked
      }
    }

    initWebSocket()

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe()
      }
      if (StompClient) {
        StompClient.disconnect(() => {})
      }
      if (sockjs) {
        sockjs.close()
      }
    }
  }, [getUserId])

  const getProgress = useCallback(
    (documentId: string) => updates.get(documentId),
    [updates]
  )

  const clearProgress = useCallback((documentId?: string) => {
    setUpdates((prev) => {
      const next = new Map(prev)
      if (documentId) {
        next.delete(documentId)
      } else {
        next.clear()
      }
      return next
    })
  }, [])

  return { getProgress, clearProgress, updates: Array.from(updates.values()) }
}
