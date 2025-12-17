import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import useAppStore from '../store/useAppStore';
import api from '../utils/api';
import toast from 'react-hot-toast';
import { BiSolidMessageSquareAdd, BiMenu, BiLogOut, BiUpload, BiBot, BiUser, BiFile, BiTrash, BiX, BiSidebar, BiGlobe, BiCog } from 'react-icons/bi';
import { format } from 'date-fns';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import DocumentManager from '../components/DocumentManager';
import ConfirmModal from '../components/ConfirmModal';
import { fetchEventSource } from '@microsoft/fetch-event-source';

function ChatPage() {
  const navigate = useNavigate();
  const { 
    currentUser, 
    conversations, 
    setConversations, 
    selectedConversation, 
    setSelectedConversation, 
    currentConversationDocuments,
    setCurrentConversationDocuments,
    removeFromCurrentConversationDocuments,
    clearState,
  } = useAppStore();

  const [loadingConversations, setLoadingConversations] = useState(false);
  const [isCreatingNewChat, setIsCreatingNewChat] = useState(false);
  const [newChatTitle, setNewChatTitle] = useState('');
  
  // Chat states
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const messagesEndRef = useRef(null);

    // Document Modal state
    const [isDocModalOpen, setIsDocModalOpen] = useState(false);
    
    // Confirm Modal state
    const [modalConfig, setModalConfig] = useState({ 
      isOpen: false, 
      title: '', 
      message: '', 
      type: 'confirm',
      onConfirm: () => {}, 
      onCancel: () => {} 
    });
  
    const showModal = (options) => {
      return new Promise((resolve) => {
        setModalConfig({
          ...options,
          isOpen: true,
          onConfirm: (val) => {
            setModalConfig(prev => ({ ...prev, isOpen: false }));
            resolve(val === undefined ? true : val);
          },
          onCancel: () => {
            setModalConfig(prev => ({ ...prev, isOpen: false }));
            resolve(false);
          }
        });
      });
    };
  
            // Right Sidebar state
  
            const [isRightSidebarOpen, setIsRightSidebarOpen] = useState(false); // Default closed
  
            
  
            const drawerCheckboxRef = useRef(null);

  useEffect(() => {
    // Open left drawer on mount (mobile convenience)
    if (drawerCheckboxRef.current && window.innerWidth < 1024) {
        drawerCheckboxRef.current.checked = true;
    }
  }, []);

  useEffect(() => {
    if (currentUser) {
      fetchConversations();
    }
  }, [currentUser]);

  useEffect(() => {
    if (selectedConversation) {
      fetchMessages(selectedConversation.id);
      fetchConversationDocuments(selectedConversation.id);
    } else {
      setMessages([]);
      setCurrentConversationDocuments([]);
    }
  }, [selectedConversation]);

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const fetchConversations = async () => {
    setLoadingConversations(true);
    try {
      const response = await api.get('/conversations');
      setConversations(response.data);
      if (response.data.length > 0 && !selectedConversation) {
        setSelectedConversation(response.data[0]); 
      }
    } catch (error) {
      toast.error('加载会话失败');
      console.error(error);
    } finally {
      setLoadingConversations(false);
    }
  };

  const fetchMessages = async (conversationId) => {
    setLoadingMessages(true);
    try {
      const response = await api.get(`/conversations/${conversationId}/messages`);
      setMessages(response.data);
    } catch (error) {
      toast.error('加载消息失败');
    } finally {
      setLoadingMessages(false);
    }
  };

  const fetchConversationDocuments = async (conversationId) => {
    try {
        const response = await api.get(`/conversations/${conversationId}/documents`);
        setCurrentConversationDocuments(response.data);
    } catch (error) {
        console.error("Failed to fetch conversation documents", error);
    }
  };

  const handleNewChat = async () => {
    if (!newChatTitle.trim()) {
      toast.error('请输入会话标题');
      return;
    }
    try {
      const response = await api.post('/conversations', {
        title: newChatTitle,
        documentIds: []
      });
      setConversations([response.data, ...conversations]);
      setSelectedConversation(response.data);
      setNewChatTitle('');
      setIsCreatingNewChat(false);
      if (drawerCheckboxRef.current) drawerCheckboxRef.current.checked = false;
      toast.success('新会话创建成功');
    } catch (error) {
      toast.error('创建会话失败');
    }
  };

  const handleSendMessage = async () => {
    if (!inputMessage.trim() || !selectedConversation) return;

    const currentMsg = inputMessage;
    setInputMessage('');
    setSending(true);

    const tempUserMsg = {
        id: Date.now(),
        role: 'USER',
        content: currentMsg,
        createdAt: new Date().toISOString()
    };
    setMessages(prev => [...prev, tempUserMsg]);

    const tempAiMsgId = Date.now() + 1;
    const tempAiMsg = {
        id: tempAiMsgId,
        role: 'ASSISTANT',
        content: '',
        createdAt: new Date().toISOString(),
        isStreaming: true
    };
    setMessages(prev => [...prev, tempAiMsg]);

    const ctrl = new AbortController();
    
    try {
      const token = localStorage.getItem('jwtToken');
      let aiResponseText = '';
      
      const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
      const streamUrl = `${apiBaseUrl}/conversations/${selectedConversation.id}/chat/stream`;

      await fetchEventSource(streamUrl, {
          method: 'POST',
          headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({ message: currentMsg }),
          signal: ctrl.signal,
          openWhenHidden: true, // 防止切换标签页时断连重试
          async onopen(response) {
              if (response.ok) {
                  return; // 一切正常
              } else {
                  // 服务端返回错误 (4xx, 5xx)，抛出异常以触发 onerror
                  // 注意：这里抛错会进入 onerror，onerror 再 throw 才会停止重试
                  throw new Error(`Failed to send message: ${response.statusText}`);
              }
          },
          onmessage(msg) {
              if (msg.data) {
                  aiResponseText += msg.data;
                  setMessages(prev => prev.map(m => 
                      m.id === tempAiMsgId ? { ...m, content: aiResponseText } : m
                  ));
              }
          },
          onclose() {
              // 正常结束，手动 abort 以确保库完全停止
              ctrl.abort();
              console.log('Stream closed by server');
          },
          onerror(err) {
              console.error('EventSource failed:', err);
              // 出错时手动 abort，绝对禁止重试
              ctrl.abort();
              // 必须 rethrow 错误，通知外层 (虽然 abort 已经杀死了连接)
              throw err; 
          }
      });
    } catch (error) {
      // 如果是我们手动 abort 的，通常不视为错误提示给用户，或者根据需要处理
      if (!ctrl.signal.aborted) {
          toast.error('发送失败');
          console.error(error);
      }
    } finally {
      setSending(false);
    }
  };

  const handleRemoveDocFromContext = async (docId) => {
      if (!selectedConversation) return;
      try {
          await api.delete(`/conversations/${selectedConversation.id}/documents/${docId}`);
          removeFromCurrentConversationDocuments(docId);
          toast.success('已从上下文中移除');
      } catch (error) {
          toast.error('移除失败');
      }
  };

  const handleTogglePublicConversation = async (targetConv = selectedConversation) => {
    if (!targetConv) return;
    const newStatus = !targetConv.isPublic;
    let allowedUsers = null;

    if (newStatus) {
        // Turning ON: Ask for whitelist
        const result = await showModal({
            title: '设置公共助手',
            message: '设置白名单（输入允许访问的用户名，用逗号分隔）。\n留空则对所有用户开放。',
            type: 'prompt',
            confirmText: '确定',
            defaultValue: ''
        });
        if (result === false) return; // Cancelled
        allowedUsers = result.trim();
    } else {
        // Turning OFF
        const result = await showModal({
            title: '取消公共助手',
            message: '确定要取消公共助手状态吗？\n其他用户将无法再看到此入口。',
            type: 'confirm',
            confirmText: '确定取消',
            confirmStyle: 'btn-error'
        });
        if (!result) return;
    }

    try {
      await api.put(`/conversations/${targetConv.id}/public`, { isPublic: newStatus, allowedUsers });
      // Update local state
      const updatedConv = { ...targetConv, isPublic: newStatus };
      setConversations(conversations.map(c => c.id === targetConv.id ? updatedConv : c));
      if (selectedConversation && selectedConversation.id === targetConv.id) {
          setSelectedConversation(updatedConv);
      }
      toast.success(newStatus ? '已设为公共助手' : '已取消公共助手');
    } catch (error) {
      toast.error('操作失败');
    }
  };

  const handleDeleteConversation = async (e, convId) => {
    e.stopPropagation(); // Prevent selecting the conversation when clicking delete
    
    const result = await showModal({
        title: '删除会话',
        message: '确定要删除这个会话吗？此操作无法撤销。',
        type: 'confirm',
        confirmText: '删除',
        confirmStyle: 'btn-error'
    });
    if (!result) return;

    try {
        await api.delete(`/conversations/${convId}`);
        const updatedList = conversations.filter(c => c.id !== convId);
        setConversations(updatedList);

        // If deleted current conversation, switch to the first available or clear
        if (selectedConversation && selectedConversation.id === convId) {
            if (updatedList.length > 0) {
                setSelectedConversation(updatedList[0]);
            } else {
                setSelectedConversation(null);
                setMessages([]);
                setCurrentConversationDocuments([]);
            }
        }
        toast.success('会话已删除');
    } catch (error) {
        toast.error('删除会话失败');
    }
  };

  const handleClearMessages = async () => {
    if (!selectedConversation) return;
    
    const result = await showModal({
        title: '清空消息',
        message: '确定要清空当前会话的所有消息吗？此操作无法撤销。',
        type: 'confirm',
        confirmText: '清空',
        confirmStyle: 'btn-error'
    });
    if (!result) return;

    try {
        await api.delete(`/conversations/${selectedConversation.id}/messages`);
        setMessages([]);
        toast.success('会话已清空');
    } catch (error) {
        toast.error('清空消息失败');
    }
  };

  const handleDeleteSingleMessage = async (messageId) => {
    const result = await showModal({
        title: '删除消息',
        message: '确定要删除这条消息吗？',
        type: 'confirm',
        confirmText: '删除',
        confirmStyle: 'btn-error'
    });
    if (!result) return;

    try {
        await api.delete(`/conversations/messages/${messageId}`);
        setMessages(prev => prev.filter(m => m.id !== messageId));
        toast.success('消息已删除');
    } catch (error) {
        toast.error('删除消息失败');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('jwtToken');
    clearState();
    navigate('/login');
    toast.success('已退出登录');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const getFileIcon = (filename) => {
    const ext = filename.split('.').pop().toLowerCase();
    const iconMap = {
      'pdf': 'pdf',
      'txt': 'text',
      'md': 'markdown',
      'html': 'html'
    };
    return iconMap[ext] || 'file';
  };

  const getIconClass = (type) => {
    const iconClass = {
      'pdf': 'bg-red-500/20 text-red-500',
      'text': 'bg-blue-500/20 text-blue-500',
      'markdown': 'bg-purple-500/20 text-purple-500',
      'html': 'bg-orange-500/20 text-orange-500',
      'file': 'bg-gray-500/20 text-gray-500'
    };
    return iconClass[type] || '';
  };

  const getProcessingStatusIcon = (status) => {
    switch (status) {
      case 'PENDING':
        return <div className="w-2 h-2 bg-warning rounded-full animate-pulse" title="等待处理"></div>;
      case 'PROCESSING':
        return <div className="w-2 h-2 bg-info rounded-full animate-spin" title="处理中..."></div>;
      case 'FAILED':
        return <div className="w-2 h-2 bg-error rounded-full" title="处理失败"></div>;
      default:
        return <div className="w-2 h-2 bg-success rounded-full" title="已完成"></div>;
    }
  };

  const countMessages = (conversationId) => {
    // Since we don't have global message counts, use a simple estimate
    const selectedCount = selectedConversation?.id === conversationId ? messages.length : 0;
    return selectedCount;
  };

  return (
    <div className="drawer lg:drawer-open h-screen bg-base-100">
      <input id="my-drawer-2" type="checkbox" className="drawer-toggle" ref={drawerCheckboxRef} />

      <div className="drawer-content flex flex-row h-full overflow-hidden relative">
        {/* Main Chat Column */}
        <div className="flex-1 flex flex-col min-w-0">
            {/* Header */}
            <div className="navbar bg-base-100 border-b shrink-0 z-10 px-4">
            <div className="flex-none lg:hidden">
                <label htmlFor="my-drawer-2" className="btn btn-ghost btn-circle">
                <BiMenu className="h-6 w-6" />
                </label>
            </div>
            <div className="flex-1 min-w-0">
                <h1 className="text-lg font-bold truncate">
                {selectedConversation?.title || 'QARAG'}
                </h1>
            </div>
            <div className="flex-none flex items-center gap-2">
                <button
                    className={`btn btn-ghost btn-circle ${isRightSidebarOpen ? 'text-primary' : ''}`}
                    onClick={() => setIsRightSidebarOpen(!isRightSidebarOpen)}
                    title="切换右侧栏"
                >
                <BiSidebar className="h-6 w-6 rotate-180" />
                </button>

                <button
                  className="btn btn-ghost btn-circle"
                  onClick={handleClearMessages}
                  title="清空会话"
                  disabled={!selectedConversation || messages.length === 0}
                >
                  <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
            </div>
            </div>

            {/* Messages Area */}
            <main className="flex-1 overflow-y-auto p-4 bg-base-200 scroll-smooth">
            {!selectedConversation ? (
                <div className="h-full flex flex-col items-center justify-center text-gray-500">
                    <BiBot className="w-16 h-16 mb-4 opacity-50" />
                    <p>请选择或创建一个会话开始聊天</p>
                </div>
            ) : (
                <div className="max-w-3xl mx-auto space-y-6 pb-4">
                    {loadingMessages ? (
                        <div className="flex justify-center py-10"><span className="loading loading-spinner"></span></div>
                    ) : messages.length === 0 ? (
                        <div className="text-center py-10 text-gray-400">
                            <p>暂无消息，开始提问吧！</p>
                        </div>
                    ) : (
                        messages.map((msg, idx) => {
                            const isUser = msg.role === 'USER';

                            return (
                                <div key={idx} className={`chat ${isUser ? 'chat-end' : 'chat-start'} group`}>
                                    <div className="chat-image avatar">
                                        <div className="w-10 rounded-full bg-base-300 flex items-center justify-center text-xl text-base-content/70">
                                            {isUser ? <BiUser className="mt-2 ml-2"/> : <BiBot className="mt-2 ml-2"/>}
                                        </div>
                                    </div>
                                                                    <div className={`chat-bubble ${isUser ? 'chat-bubble-primary' : 'chat-bubble-secondary'} shadow-sm whitespace-pre-wrap min-h-[2.5rem] ${!isUser ? 'leading-tight' : ''}`}>
                                                                        {msg.role === 'ASSISTANT' && !msg.content ? (
                                                                            <span className="loading loading-dots loading-sm"></span>
                                                                        ) : (
                                                                            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw]}>
                                                                                {msg.content}
                                                                            </ReactMarkdown>
                                                                        )}
                                                                    </div>                                    <div className="chat-footer opacity-50 text-xs mt-1 flex items-center gap-2">
                                        <span>{msg.createdAt ? format(new Date(msg.createdAt), 'HH:mm') : ''}</span>
                                        {!msg.isStreaming && (
                                          <button
                                            className="opacity-0 group-hover:opacity-100 transition-all hover:scale-110 text-base-content/40 hover:text-error"
                                            onClick={() => handleDeleteSingleMessage(msg.id)}
                                            title="删除消息"
                                          >
                                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                                            </svg>
                                          </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })
                    )}
                    <div ref={messagesEndRef} />
                </div>
            )}
            </main>

            {/* Input Area */}
            <div className="bg-base-100 p-4 border-t shrink-0">
            <div className="max-w-3xl mx-auto flex gap-2">
                <textarea
                className="textarea textarea-bordered w-full resize-none focus:outline-primary"
                placeholder={selectedConversation ? "输入你的问题..." : "请先选择一个会话"}
                rows="1"
                style={{ minHeight: '3rem', maxHeight: '10rem' }}
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={!selectedConversation || sending}
                ></textarea>
                <button 
                    className="btn btn-primary"
                    onClick={handleSendMessage}
                    disabled={!selectedConversation || sending || !inputMessage.trim()}
                >
                发送
                </button>
            </div>
            </div>
        </div>

        {/* Right Sidebar (Context Panel) */}
        {/* Mobile Overlay/Backdrop */}
        {isRightSidebarOpen && (
            <div 
                className="absolute inset-0 bg-black/50 z-20 lg:hidden"
                onClick={() => setIsRightSidebarOpen(false)}
            ></div>
        )}

        {/* Sidebar Panel */}
        <div className={`
            fixed inset-y-0 right-0 z-30 w-80 bg-base-100 border-l shadow-2xl transition-transform duration-300 ease-in-out transform
            lg:static lg:shadow-none lg:z-auto lg:transform-none
            ${isRightSidebarOpen ? 'translate-x-0' : 'translate-x-full lg:hidden'}
        `}>
            <div className="flex flex-col h-full">
                <div className="p-4 border-b flex justify-between items-center bg-base-50">
                    <h3 className="font-bold flex items-center gap-2">
                        <BiFile /> 上下文文档
                    </h3>
                    <button className="btn btn-ghost btn-xs lg:hidden" onClick={() => setIsRightSidebarOpen(false)}>
                        <BiX className="w-5 h-5" />
                    </button>
                </div>
                
                <div className="flex-1 overflow-y-auto p-4">
                    {!selectedConversation ? (
                        <p className="text-sm text-gray-500 text-center mt-10">请选择会话</p>
                    ) : (
                        <div className="space-y-3">
                            <button
                                className="btn btn-outline btn-primary w-full btn-sm gap-2 dashed"
                                onClick={() => setIsDocModalOpen(true)}
                            >
                                <BiUpload /> 上传/选择文档
                            </button>

                            {currentConversationDocuments.length === 0 ? (
                                <div className="text-center py-8 text-gray-400 text-sm border-2 border-dashed rounded-lg">
                                    <p>当前会话无关联文档</p>
                                    <p className="text-xs mt-1">AI 将仅凭通用知识回答</p>
                                </div>
                            ) : (
                                <ul className="space-y-2">
                                    {currentConversationDocuments.map(doc => (
                                        <li key={doc.id} className="p-3 bg-base-200 rounded-lg flex justify-between items-start group hover:bg-base-300 transition-colors">
                                            <div className="flex items-start gap-2 min-w-0">
                                                <BiFile className="w-4 h-4 mt-1 flex-shrink-0 text-primary" />
                                                <div className="min-w-0">
                                                    <p className="text-sm font-medium truncate" title={doc.name}>{doc.name}</p>
                                                    <p className="text-xs text-gray-500">
                                                        {doc.uploadedAt ? format(new Date(doc.uploadedAt), 'MM-dd HH:mm') : ''}
                                                    </p>
                                                </div>
                                                                                                                                                                                    </div>
                                                                                                                                                                                    {currentUser && doc.userId === currentUser.id && (
                                                                                                                                                                                        <button
                                                                                                                                                                                            className="btn btn-ghost btn-xs text-gray-400 hover:text-error opacity-0 group-hover:opacity-100 transition-opacity"
                                                                                                                                                                                            onClick={() => handleRemoveDocFromContext(doc.id)}
                                                                                                                                                                                            title="移除文档"
                                                                                                                                                                                        >
                                                                                                                                                                                            <BiTrash />
                                                                                                                                                                                        </button>
                                                                                                                                                                                    )}
                                                                                                                                                                                </li>                                    ))}
                                </ul>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
      </div>

      {/* Left Sidebar (Conversations) */}
      <div className="drawer-side z-30">
        <label htmlFor="my-drawer-2" aria-label="close sidebar" className="drawer-overlay"></label>
        <ul className="menu p-4 w-80 min-h-full bg-base-100 text-base-content border-r flex flex-col">
          {/* Sidebar Header */}
          <div className="flex justify-between items-center mb-6 px-2">
            <h2 className="text-xl font-bold flex items-center gap-2">
                <BiBot className="text-primary"/> QARAG
            </h2>
            <button 
                className="btn btn-ghost btn-circle btn-sm" 
                onClick={() => setIsCreatingNewChat(!isCreatingNewChat)}
                title="新建会话"
            >
              <BiSolidMessageSquareAdd className="h-6 w-6 text-primary" />
            </button>
          </div>

          {/* New Chat Input */}
          {isCreatingNewChat && (
            <div className="form-control mb-4 px-2 animate-fadeIn">
              <input
                type="text"
                placeholder="会话标题..."
                className="input input-bordered input-sm w-full mb-2"
                value={newChatTitle}
                onChange={(e) => setNewChatTitle(e.target.value)}
                autoFocus
              />
              <div className="flex gap-2">
                  <button className="btn btn-sm btn-primary flex-1" onClick={handleNewChat}>创建</button>
                  <button className="btn btn-sm btn-ghost" onClick={() => setIsCreatingNewChat(false)}>取消</button>
              </div>
            </div>
          )}

          {/* Conversation List */}
          <div className="flex-1 overflow-y-auto -mx-2 px-2">
              {loadingConversations ? (
                <div className="flex justify-center p-8">
                  <span className="loading loading-spinner loading-lg text-primary"></span>
                </div>
              ) : (
                <>
                  {/* Public Assistants Section */}
                  {conversations.filter(c => c.isPublic && c.parentId === null).length > 0 && (
                    <div className="mb-4">
                      <h3 className="px-4 text-xs font-bold text-base-content/50 uppercase tracking-wider mb-2">公共助手</h3>
                      <ul className="space-y-2">
                        {conversations.filter(c => c.isPublic && c.parentId === null).map((conv) => (
                                                    <li key={conv.id} className="group">
                                                      <div
                                                        className="relative flex items-center gap-2 p-2 rounded-lg hover:bg-base-200 transition-colors cursor-pointer"
                                                        onClick={async () => {
                                // Check if we already have a child conversation for this parent
                                const existingChild = conversations.find(c => c.parentId === conv.id);
                                if (existingChild) {
                                  setSelectedConversation(existingChild);
                                } else {
                                  // Create new child conversation
                                  try {
                                    const response = await api.post('/conversations', {
                                      title: `${conv.title}`,
                                      documentIds: [],
                                      parentId: conv.id
                                    });
                                    setConversations([response.data, ...conversations]);
                                    setSelectedConversation(response.data);
                                    toast.success('已创建会话');
                                  } catch (error) {
                                    toast.error('创建会话失败');
                                  }
                                }
                                if (drawerCheckboxRef.current) drawerCheckboxRef.current.checked = false;
                              }}
                            >
                              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center text-primary">
                                <BiBot className="w-5 h-5" />
                              </div>
                              <div className="flex-1 min-w-0 pr-16">
                                <p className="font-bold truncate">{conv.title}</p>
                                <p className="text-xs text-base-content/60 truncate">公共助手</p>
                              </div>

                              {currentUser && currentUser.role === 'ADMIN' && conv.userId === currentUser.id && (
                                <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center space-x-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                  <button
                                    className="btn btn-sm btn-circle btn-ghost text-base-content/40 hover:text-primary"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setSelectedConversation(conv);
                                      if (drawerCheckboxRef.current) drawerCheckboxRef.current.checked = false;
                                    }}
                                    title="配置助手（管理文档）"
                                  >
                                    <BiCog />
                                  </button>
                                  <button
                                    className={`btn btn-sm btn-circle ${conv.isPublic ? 'btn-primary text-primary-content' : 'btn-ghost text-base-content/40 hover:text-primary'}`}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handleTogglePublicConversation(conv);
                                    }}
                                    title="取消公共助手"
                                  >
                                    <BiGlobe />
                                  </button>
                                  <button
                                    className="btn btn-sm btn-circle btn-ghost text-error hover:bg-error/10"
                                    onClick={(e) => handleDeleteConversation(e, conv.id)}
                                    title="删除公共助手"
                                  >
                                    <BiTrash />
                                  </button>
                                </div>
                              )}
                            </div>
                          </li>
                        ))}
                      </ul>
                      <div className="divider my-2"></div>
                    </div>
                  )}

                  {/* My Conversations Section */}
                  {conversations.length === 0 && !isCreatingNewChat ? (
                    <div className="text-center py-10 text-gray-400 text-sm mt-10 space-y-2">
                      <BiBot className="w-12 h-12 mx-auto opacity-30" />
                      <p>暂无会话</p>
                    </div>
                  ) : (
                    <ul className="space-y-3">
                      {conversations.filter(c => !c.isPublic || c.parentId !== null).map((conv) => {
                        const isSelected = selectedConversation?.id === conv.id;
                        const msgCount = countMessages(conv.id);
                        const docCount = currentConversationDocuments.filter(d =>
                          selectedConversation?.id === conv.id
                        ).length || 0;
                        const lastActivity = conv.updatedAt || conv.createdAt;

                        return (
                          <li key={conv.id} className="group">
                            <div
                              className={`
                                relative overflow-hidden rounded-xl transition-all duration-300 cursor-pointer border-2
                                ${isSelected
                                  ? 'bg-primary text-primary-content shadow-xl border-primary hover:bg-primary'
                                  : 'bg-base-100 border-transparent hover:bg-base-200 hover:border-base-300 hover:shadow-md'
                                }
                              `}
                              onClick={ (e) => {
                                if (!e.target.closest('button')) {
                                  setSelectedConversation(conv);
                                  if (drawerCheckboxRef.current) drawerCheckboxRef.current.checked = false;
                                }
                              }}
                            >
                                                            {isSelected && (
                                                              <div className="absolute left-0 top-0 h-full w-1 bg-primary-focus"></div>
                                                            )}
                                                            <div className="p-3">
                                                              <div className="flex items-center justify-between mb-1">
                                  <h3 className={`font-bold truncate pr-10 ${isSelected ? 'text-primary-content' : 'text-base-content'}`}>
                                    {conv.title}
                                  </h3>
                                  {lastActivity && (
                                    <span className={`text-xs font-medium whitespace-nowrap ${isSelected ? 'text-primary-content/70' : 'text-base-content/60'}`}>
                                      {format(new Date(lastActivity), 'MM-dd HH:mm')}
                                    </span>
                                  )}
                                </div>

                                <div className="flex items-center gap-3 text-xs">
                                  {msgCount > 0 && (
                                    <div className={`flex items-center gap-1.5 ${isSelected ? 'text-primary-content/70' : 'text-base-content/60'}`}>
                                      <span className="w-2.5 h-2.5 rounded-full bg-current opacity-70"></span>
                                      <span>{msgCount} 消息</span>
                                    </div>
                                  )}
                                  {docCount > 0 && (
                                    <div className={`flex items-center gap-1.5 ${isSelected ? 'text-primary-content/70' : 'text-base-content/60'}`}>
                                      <BiFile className="w-3.5 h-3.5 opacity-70" />
                                      <span>{docCount} 文档</span>
                                    </div>
                                  )}
                                </div>

                                {currentUser && (
                                  <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center space-x-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                    {currentUser.role === 'ADMIN' && (
                                      <button
                                        className={`btn btn-sm btn-circle ${conv.isPublic ? 'btn-primary text-primary-content' : 'btn-ghost text-base-content/40 hover:text-primary'}`}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleTogglePublicConversation(conv);
                                        }}
                                        title={conv.isPublic ? "取消公共助手" : "设为公共助手"}
                                      >
                                        <BiGlobe className="w-5 h-5" />
                                      </button>
                                    )}
                                    <button
                                      className="btn btn-sm btn-circle btn-ghost text-error hover:bg-error/10"
                                      onClick={(e) => handleDeleteConversation(e, conv.id)}
                                      title="删除会话"
                                    >
                                      <BiTrash className="w-5 h-5" />
                                    </button>
                                  </div>
                                )}
                              </div>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </>
              )}
          </div>

          <div className="divider my-2"></div>

          {/* User Info */}
          {currentUser && (
            <div className="flex items-center gap-3 p-2 rounded-lg hover:bg-base-200 transition-colors">
              <div className="avatar placeholder">
                  <div className="bg-neutral text-neutral-content rounded-full w-10">
                      <span>{currentUser.username.substring(0,1).toUpperCase()}</span>
                  </div>
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-bold truncate">{currentUser.username}</p>
                <p className="text-xs text-gray-500 truncate">{currentUser.email}</p>
              </div>
              <button 
                  className="btn btn-ghost btn-square btn-sm text-error" 
                  onClick={handleLogout}
                  title="退出登录"
              >
                <BiLogOut className="h-5 w-5" />
              </button>
            </div>
          )}
        </ul>
      </div>

            {/* Document Manager Modal */}
            <DocumentManager
                isOpen={isDocModalOpen}
                onClose={() => setIsDocModalOpen(false)}
            />
            
            {/* Global Confirm Modal */}
            <ConfirmModal {...modalConfig} />
          </div>
        );
      }
export default ChatPage;
