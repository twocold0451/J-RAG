import { useEffect, useCallback, useState } from 'react'
import { Client } from '@stomp/stompjs'

export interface DocumentUpdate {
  documentId: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  errorMessage?: string
}

export function useDocumentProgress(userId: string | number | null) {
  const [updates, setUpdates] = useState<Map<string, DocumentUpdate>>(new Map())

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

    const token = localStorage.getItem('token')
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const brokerURL = `${protocol}//${window.location.host}/ws`

    const client = new Client({
      brokerURL,
      
      // 添加鉴权头
      connectHeaders: {
        Authorization: token ? `Bearer ${token}` : '',
      },
      
      // 自动重连配置
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: () => {
        // 订阅个人文档更新频道 (Spring User Destination)
        client.subscribe('/user/queue/document-updates', (message) => {
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
        })
      },

      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message'])
      }
    })

    try {
      client.activate()
    } catch (err) {
      console.error('WebSocket activation failed:', err)
    }

    return () => {
      client.deactivate()
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
