// ==================== Auth Types ====================
export interface UserResponse {
  id: number
  username: string
  role: 'USER' | 'ADMIN'
  token?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

// ==================== Document Types ====================
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface DocumentDto {
  id: string
  name: string
  category: string | null
  status: DocumentStatus
  progress?: number
  errorMessage?: string | null
  userId: number
  isPublic: boolean
  uploadedAt: string
}

export interface UploadResponse {
  documentId: string
  message: string
  isPublic: boolean
}

export interface UploadRequest {
  file: File
  isPublic?: boolean
  category?: string
}

export interface IngestUrlRequest {
  url: string
  isPublic?: boolean
  category?: string
}

// ==================== Conversation Types ====================
export interface ConversationResponse {
  id: number
  title: string
  templateId?: number
  userId?: number
  username?: string
  isPublic?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ConversationCreateRequest {
  title: string
  templateId?: number
  documentIds?: string[]
  isPublic?: boolean
}

export function createConversationRequest(
  title: string,
  templateId?: number,
  documentIds?: string[],
  isPublic: boolean = true
): ConversationCreateRequest {
  return {
    title,
    templateId,
    documentIds,
    isPublic,
  }
}

export interface ChatMessageDto {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface ChatRequest {
  message: string
  useDeepThinking?: boolean
}

// ==================== Template Types ====================
export interface TemplateDto {
  id: number
  name: string
  description?: string
  icon?: string
  documentCount?: number
  visibleGroups?: number[]
  documentIds?: string[]
  public?: boolean
  createdAt?: string
}

export interface TemplateCreateRequest {
  name: string
  description?: string
  documentIds: string[]
  visibleGroupIds?: number[]
  isPublic?: boolean
}

// ==================== Group Types ====================
export interface UserGroupDto {
  id: number
  name: string
  description?: string
  memberCount?: number
  templateCount?: number
  createdAt?: string
}

export interface GroupCreateRequest {
  name: string
  description?: string
  userIds: number[]
}

// ==================== Admin Types ====================
export interface AdminUserResponse {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  groupIds?: number[]
  initialPassword?: string
  createdAt?: string
}

export interface CreateUserRequest {
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  groupIds: number[]
}

// ==================== Generic Types ====================
export interface ApiResponse<T> {
  data: T
  success?: boolean
  message?: string
}

export interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}

// Re-export for backward compatibility
export interface User {
  id: number
  username: string
  email?: string
  role: 'USER' | 'ADMIN'
  groupIds?: number[]
  createdAt?: string
}

export interface Document {
  id: string
  filename: string
  category: string
  status: DocumentStatus
  size: number
  isPublic: boolean
  createdAt: string
}

export interface Conversation {
  id: number
  title: string
  templateId?: number
  templateName?: string
  userId?: number
  isPublic?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ChatMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface UserGroup {
  id: number
  name: string
  description?: string
  memberCount?: number
  templateCount?: number
  createdAt?: string
}

export interface Template {
  id: number
  name: string
  description?: string
  icon?: string
  userId?: number
  isPublic?: boolean
  visibleGroupIds?: number[]
  documentCount?: number
  createdAt?: string
}
