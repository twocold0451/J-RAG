import type {
  UserResponse,
  DocumentDto,
  UploadResponse,
  ConversationResponse,
  ChatMessageDto,
  TemplateDto,
  UserGroupDto,
  AdminUserResponse,
  ConversationCreateRequest,
  TemplateCreateRequest,
  GroupCreateRequest,
  CreateUserRequest,
  ChatRequest,
  IngestUrlRequest,
} from '@/types'

const API_BASE = (import.meta.env?.VITE_API_BASE_URL as string) || '/api'

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: Record<string, unknown>
  params?: Record<string, string | number | undefined>
  headers?: Record<string, string>
}

async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, params, headers = {} } = options

  const token = localStorage.getItem('token')
  const requestHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...headers,
  }
  if (token) {
    requestHeaders['Authorization'] = `Bearer ${token}`
  }

  const url = new URL(`${API_BASE}${endpoint}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
        url.searchParams.append(key, String(value))
      }
    })
  }

  const response = await fetch(url.toString(), {
    method,
    headers: requestHeaders,
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }))
    throw new Error(error.message || `请求失败: ${response.status}`)
  }

  // Handle empty responses
  const text = await response.text()
  if (!text) return {} as T

  return JSON.parse(text)
}

export const api = {
  // ==================== Auth ====================
  login: (username: string, password: string) =>
    request<UserResponse>('/auth/login', {
      method: 'POST',
      body: { username, password },
    }),

  register: (username: string, password: string, email: string) =>
    request<UserResponse>('/auth/register', {
      method: 'POST',
      body: { username, password, email },
    }),

  getCurrentUser: () => request<UserResponse>('/auth/me'),

  changePassword: (currentPassword: string, newPassword: string) =>
    request('/auth/password', {
      method: 'PUT',
      body: { currentPassword, newPassword },
    }),

  getUserGroups: () => request<UserGroupDto[]>('/user/groups'),

  // ==================== Documents ====================
  getDocuments: () => request<DocumentDto[]>('/documents'),

  uploadDocument: async (file: File, category: string, isPublic: boolean): Promise<UploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('category', category)
    formData.append('isPublic', String(isPublic))

    const token = localStorage.getItem('token')
    const response = await fetch(`${API_BASE}/upload`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    })

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '上传失败' }))
      throw new Error(error.message || '上传失败')
    }

    return response.json()
  },

  deleteDocument: (id: string) =>
    request(`/documents/${id}`, { method: 'DELETE' }),

  ingestUrl: (data: IngestUrlRequest) =>
    request<UploadResponse>('/ingest-url', {
      method: 'POST',
      body: data,
      params: data.category ? { category: data.category } : undefined,
    }),

  // ==================== Conversations ====================
  getConversations: () =>
    request<ConversationResponse[]>('/conversations'),

  createConversation: (data: ConversationCreateRequest) =>
    request<ConversationResponse>('/conversations', {
      method: 'POST',
      body: data,
    }),

  getConversationMessages: (conversationId: number) =>
    request<ChatMessageDto[]>(`/conversations/${conversationId}/messages`),

  sendMessage: (conversationId: number, data: ChatRequest): Promise<ReadableStream> => {
    const token = localStorage.getItem('token')
    return fetch(`${API_BASE}/conversations/${conversationId}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(data),
    }).then((response) => {
      if (!response.ok) {
        throw new Error(`请求失败: ${response.status}`)
      }
      return response.body as ReadableStream
    })
  },

  deleteConversation: (id: number) =>
    request(`/conversations/${id}`, { method: 'DELETE' }),

  // ==================== Team Conversations ====================
  getTeamConversations: () =>
    request<ConversationResponse[]>('/conversations/public'),

  getTeamConversationMessages: (conversationId: number) =>
    request<ChatMessageDto[]>(`/conversations/${conversationId}/public/messages`),

  // ==================== Templates ====================
  getTemplates: () => request<TemplateDto[]>('/templates'),

  createTemplate: (data: TemplateCreateRequest) =>
    request<TemplateDto>('/templates', {
      method: 'POST',
      body: data,
    }),

  updateTemplate: (id: number, data: TemplateCreateRequest) =>
    request<TemplateDto>(`/templates/${id}`, {
      method: 'PUT',
      body: data,
    }),

  deleteTemplate: (id: number) =>
    request(`/templates/${id}`, { method: 'DELETE' }),

  // ==================== Groups ====================
  getGroups: () => request<UserGroupDto[]>('/groups'),

  getGroupMembers: (groupId: number) =>
    request<number[]>(`/groups/${groupId}/members`),

  createGroup: (data: GroupCreateRequest) =>
    request<UserGroupDto>('/groups', {
      method: 'POST',
      body: data,
    }),

  updateGroup: (id: number, data: GroupCreateRequest) =>
    request<UserGroupDto>(`/groups/${id}`, {
      method: 'PUT',
      body: data,
    }),

  deleteGroup: (id: number) =>
    request(`/groups/${id}`, { method: 'DELETE' }),

  // ==================== Admin ====================
  getUsers: () => request<AdminUserResponse[]>('/admin/users'),

  createUser: (data: CreateUserRequest) =>
    request<AdminUserResponse>('/admin/users', {
      method: 'POST',
      body: data,
    }),

  updateUser: (id: number, data: Partial<CreateUserRequest>) =>
    request<AdminUserResponse>(`/admin/users/${id}`, {
      method: 'PUT',
      body: data,
    }),

  deleteUser: (id: number) =>
    request(`/admin/users/${id}`, { method: 'DELETE' }),

  resetPassword: (id: number) =>
    request<{ newPassword: string }>(`/admin/users/${id}/reset-password`, {
      method: 'POST',
    }),
}

// Helper function for streaming responses
export async function* streamResponse(response: Response): AsyncGenerator<string> {
  const reader = response.body?.getReader()
  if (!reader) return

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // Parse SSE format
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = line.slice(6)
        if (data === '[DONE]') return
        try {
          const parsed = JSON.parse(data)
          if (parsed.content !== undefined) {
            yield parsed.content
          }
        } catch {
          // Not JSON, yield as-is
          yield data
        }
      }
    }
  }
}
