import React, { useState, useRef, useEffect } from 'react';
import api from '../utils/api';
import toast from 'react-hot-toast';
import useAppStore from '../store/useAppStore';
import ConfirmModal from './ConfirmModal';
import { BiTrash, BiCheck, BiCloudUpload, BiFile, BiX, BiPlus, BiChevronDown } from 'react-icons/bi';
import { format } from 'date-fns';

function DocumentManager({ isOpen, onClose }) {
  const {
    documents,
    setDocuments,
    addDocument,
    removeDocument,
    selectedConversation,
    currentConversationDocuments,
    addToCurrentConversationDocuments,
    removeFromCurrentConversationDocuments,
    currentUser,
  } = useAppStore();

  const [uploading, setUploading] = useState(false);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedDocs, setSelectedDocs] = useState(new Set());
  const [showBulkActions, setShowBulkActions] = useState(false);
  const [documentViewFilter, setDocumentViewFilter] = useState('MY');
  const fileInputRef = useRef(null);
  const dropdownButtonRef = useRef(null);

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

  useEffect(() => {
    if (isOpen) {
      fetchDocuments();
    }
  }, [isOpen]);

  const fetchDocuments = async () => {
    setLoadingDocs(true);
    try {
      const response = await api.get('/documents');
      setDocuments(response.data);
    } catch (error) {
      console.error(error);
      toast.error('获取文档列表失败');
    } finally {
      setLoadingDocs(false);
    }
  };

  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    setUploading(true);
    try {
      const response = await api.post('/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      fetchDocuments();
      toast.success('文件上传成功');

      if (selectedConversation && response.data.documentId) {
          await handleToggleConversationDoc(response.data.documentId, true);
      }
    } catch (error) {
      console.error(error);
      toast.error('文件上传失败: ' + (error.response?.data?.message || error.message));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleToggleConversationDoc = async (docId, isAdding) => {
    if (!selectedConversation) return;

    try {
      if (isAdding) {
        await api.post(`/conversations/${selectedConversation.id}/documents`, {
            documentIds: [docId]
        });
        const docToAdd = documents.find(d => d.id === docId);
        if (docToAdd) addToCurrentConversationDocuments(docToAdd);
        toast.success('已添加到当前会话上下文');
      } else {
        await api.delete(`/conversations/${selectedConversation.id}/documents/${docId}`);
        removeFromCurrentConversationDocuments(docId);
        toast.success('已从当前会话移除');
      }
    } catch (error) {
      toast.error(isAdding ? '添加失败' : '移除失败');
    }
  };

  const handleDeleteGlobal = async (docId) => {
    const result = await showModal({
        title: '彻底删除文档',
        message: '确定要彻底删除这个文档吗？此操作无法撤销，且会从所有相关会话中移除。',
        type: 'confirm',
        confirmText: '彻底删除',
        confirmStyle: 'btn-error'
    });
    if (!result) return;

    try {
      await api.delete(`/documents/${docId}`);
      removeDocument(docId);
      removeFromCurrentConversationDocuments(docId); 
      toast.success('文档已彻底删除');
    } catch (error) {
      toast.error('删除失败');
    }
  };

  const isLinked = (docId) => {
    return currentConversationDocuments.some(d => d.id === docId);
  };

  const getFileIcon = (filename) => {
    const ext = filename.split('.').pop().toLowerCase();
    const iconMap = {
      'pdf': '📄',
      'txt': '📝',
      'md': '📘',
      'html': '🌐'
    };
    return iconMap[ext] || '📎';
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'PENDING':
        return { icon: '⏳', color: 'text-warning' };
      case 'PROCESSING':
        return { icon: '⚙️', color: 'text-info' };
      case 'FAILED':
        return { icon: '❌', color: 'text-error' };
      default:
        return { icon: '✅', color: 'text-success' };
    }
  };

  const filteredDocuments = documents.filter(doc => {
    const matchesSearch = doc.name.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || doc.status === statusFilter;

    let matchesViewFilter = true;
    if (documentViewFilter === 'MY' && currentUser) {
        matchesViewFilter = doc.userId === currentUser.id;
    } else if (documentViewFilter === 'PUBLIC') {
        matchesViewFilter = doc.isPublic === true;
    }
    
    return matchesSearch && matchesStatus && matchesViewFilter;
  });

  const toggleDocSelection = (docId) => {
    const newSelected = new Set(selectedDocs);
    if (newSelected.has(docId)) {
      newSelected.delete(docId);
    } else {
      newSelected.add(docId);
    }
    setSelectedDocs(newSelected);
    setShowBulkActions(newSelected.size > 0);
  };

  const clearSelection = () => {
    setSelectedDocs(new Set());
    setShowBulkActions(false);
  };

  const bulkAddToConversation = async () => {
    if (!selectedConversation) {
      toast.error('请先选择一个会话');
      return;
    }
    try {
      const docIds = Array.from(selectedDocs);
      await api.post(`/conversations/${selectedConversation.id}/documents`, {
        documentIds: docIds
      });
      // Add to local state
      docIds.forEach(docId => {
        const doc = documents.find(d => d.id === docId);
        if (doc) addToCurrentConversationDocuments(doc);
      });
      toast.success(`已批量关联 ${docIds.length} 个文档`);
      clearSelection();
    } catch (error) {
      toast.error('批量关联失败');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal modal-open z-50">
      <div className="modal-box w-11/12 max-w-4xl h-[85vh] flex flex-col p-0 bg-base-100">
        {/* Header */}
        <div className="p-4 border-b flex justify-between items-center bg-base-200">
          <div>
            <h3 className="font-bold text-lg">文档知识库</h3>
            <p className="text-xs text-gray-500">管理您的所有上传文档，并勾选以添加到当前会话</p>
          </div>
          <button className="btn btn-sm btn-circle btn-ghost" onClick={onClose}>
            <BiX className="w-6 h-6" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {/* Search and Filters */}
          <div className="mb-6 space-y-4">
            <div className="flex flex-col md:flex-row gap-4">
              {/* Search */}
              <div className="flex-1">
                <div className="relative">
                  <input
                    type="text"
                    placeholder="搜索文档名称..."
                    className="input input-bordered w-full pl-10"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                  <svg className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-base-content/40" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                  </svg>
                </div>
              </div>

              {/* Status Filter */}
              <div className="dropdown dropdown-bottom dropdown-end">
                <div tabIndex={0} role="button" className="btn btn-outline border-base-300 bg-base-100 hover:bg-base-200 m-1 w-48 justify-between font-normal">
                  <div className="flex items-center gap-2">
                     {statusFilter === 'ALL' ? (
                       <span>全部状态</span>
                     ) : (
                       <>
                         <div className={`w-2 h-2 rounded-full ${
                           statusFilter === 'PENDING' ? 'bg-warning' :
                           statusFilter === 'PROCESSING' ? 'bg-info' :
                           statusFilter === 'COMPLETED' ? 'bg-success' :
                           'bg-error'
                         }`}></div>
                         <span>
                           {statusFilter === 'PENDING' ? '等待中' :
                            statusFilter === 'PROCESSING' ? '处理中' :
                            statusFilter === 'COMPLETED' ? '已完成' :
                            '失败'}
                         </span>
                       </>
                     )}
                  </div>
                  <BiChevronDown className="text-base-content/50" />
                </div>
                <ul tabIndex={0} className="dropdown-content z-[1] menu p-2 shadow-lg bg-base-100 rounded-box w-48 border border-base-200">
                  <li><a onClick={() => { setStatusFilter('ALL'); document.activeElement.blur(); }} className={`${statusFilter === 'ALL' ? 'active' : ''} hover:bg-base-200 hover:text-base-content`}>全部状态</a></li>
                  <li><a onClick={() => { setStatusFilter('PENDING'); document.activeElement.blur(); }} className={`${statusFilter === 'PENDING' ? 'active' : ''} hover:bg-base-200 hover:text-base-content`}><div className="w-2 h-2 rounded-full bg-warning"></div> 等待中</a></li>
                  <li><a onClick={() => { setStatusFilter('PROCESSING'); document.activeElement.blur(); }} className={`${statusFilter === 'PROCESSING' ? 'active' : ''} hover:bg-base-200 hover:text-base-content`}><div className="w-2 h-2 rounded-full bg-info"></div> 处理中</a></li>
                  <li><a onClick={() => { setStatusFilter('COMPLETED'); document.activeElement.blur(); }} className={`${statusFilter === 'COMPLETED' ? 'active' : ''} hover:bg-base-200 hover:text-base-content`}><div className="w-2 h-2 rounded-full bg-success"></div> 已完成</a></li>
                  <li><a onClick={() => { setStatusFilter('FAILED'); document.activeElement.blur(); }} className={`${statusFilter === 'FAILED' ? 'active' : ''} hover:bg-base-200 hover:text-base-content`}><div className="w-2 h-2 rounded-full bg-error"></div> 失败</a></li>
                </ul>
              </div>
            </div>

            {/* Bulk Actions Bar */}
            {showBulkActions && (
              <div className="flex items-center justify-between p-3 rounded-lg bg-primary/10 border border-primary/20">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">
                    已选择 {selectedDocs.size} 个文档
                  </span>
                  <button
                    className="btn btn-xs btn-ghost"
                    onClick={clearSelection}
                  >
                    清除选择
                  </button>
                </div>
                <div className="flex items-center gap-2">
                  {selectedConversation && (
                    <button
                      className="btn btn-sm btn-primary"
                      onClick={bulkAddToConversation}
                    >
                      <BiPlus /> 批量关联
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Upload Area */}
          <div
            className="border-2 border-dashed border-base-300 rounded-xl p-8 mb-8 text-center cursor-pointer hover:border-primary hover:bg-base-50 transition-all group hover:shadow-lg"
            onClick={() => fileInputRef.current?.click()}
          >
            <input
              type="file"
              className="hidden"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept=".txt,.pdf,.md,.html"
            />
            {uploading ? (
              <span className="loading loading-spinner loading-lg text-primary"></span>
            ) : (
              <div className="flex flex-col items-center gap-3 group-hover:scale-105 transition-transform duration-300">
                <BiCloudUpload className="w-16 h-16 text-base-content/40 group-hover:text-primary transition-colors" />
                <div>
                  <p className="font-semibold text-base-content">点击上传新文档</p>
                  <p className="text-sm text-base-content/60 mt-1">支持 TXT, PDF, MD, HTML (最大 10MB)</p>
                </div>
              </div>
            )}
          </div>

          {/* Document List */}
          <div>
             <div className="flex items-center justify-between mb-6">
                <h4 className="font-bold text-lg flex items-center gap-2 text-base-content">
                    <BiFile className="text-primary" /> 所有文档
                </h4>
                <span className="badge badge-lg">
                  {filteredDocuments.length} / {documents.length}
                </span>
             </div>

             {/* Document View Filters */}
             <div className="flex justify-center mb-4 gap-2">
                <button
                    className={`btn btn-sm ${documentViewFilter === 'MY' ? 'btn-active btn-primary' : 'btn-ghost'}`}
                    onClick={() => setDocumentViewFilter('MY')}
                >
                    我的文档
                </button>
             </div>

             {loadingDocs ? (
                 <div className="flex justify-center p-10">
                   <span className="loading loading-spinner loading-lg text-primary"></span>
                 </div>
             ) : filteredDocuments.length === 0 ? (
                 <div className="text-center py-16 bg-base-200 rounded-xl border-2 border-dashed border-base-300">
                    <BiFile className="w-16 h-16 mx-auto opacity-30 text-base-content mb-4" />
                    <p className="text-base-content/60">未找到匹配的文档</p>
                    <p className="text-sm text-base-content/40 mt-2">
                      {searchTerm ? '尝试更换搜索词' : '暂无文档，请先上传'}
                    </p>
                 </div>
             ) : (
                 <div className="grid grid-cols-1 gap-3">
                    {filteredDocuments.map(doc => {
                        const linked = isLinked(doc.id);
                        const selected = selectedDocs.has(doc.id);
                        const fileIcon = getFileIcon(doc.name);
                        const statusInfo = getStatusIcon(doc.status);

                        return (
                            <div key={doc.id} className="relative">
                              <input
                                type="checkbox"
                                className="checkbox absolute left-3 top-1/2 -translate-y-1/2 z-10 opacity-0 group-hover:opacity-100 transition-opacity"
                                checked={selected}
                                onChange={() => toggleDocSelection(doc.id)}
                              />
                              <div className={`
                                flex items-center justify-between p-4 rounded-xl border-2 transition-all
                                ${selected
                                  ? 'border-primary bg-primary/5 scale-[1.01] shadow-md'
                                  : linked
                                  ? 'border-primary/30 bg-primary/5'
                                  : 'border-base-200 bg-base-100 hover:border-base-300 hover:shadow-sm'
                                }
                                hover:translate-x-1
                              `}>
                                <div className="flex items-center gap-4 overflow-hidden">
                                  {/* File Icon */}
                                  <div className={`
                                    w-12 h-12 rounded-lg flex items-center justify-center text-2xl transition-transform
                                    ${linked ? 'bg-primary text-primary-content scale-110' : 'bg-base-200 text-base-content/70'}
                                  `}>
                                    {fileIcon}
                                  </div>

                                  {/* Status Indicator */}
                                  <div className="absolute top-2 left-12">
                                    <div className={statusInfo.color}>
                                      <span className="text-xs">{statusInfo.icon}</span>
                                    </div>
                                  </div>

                                  <div className="min-w-0 flex-1">
                                                                        <div className="flex items-center gap-2 mb-1">
                                                                            <p className="font-semibold truncate max-w-xs md:max-w-md" title={
                                    doc.name}>
                                                                              {doc.name}
                                                                            </p>
                                                                            {doc.status === 'PROCESSING' && <span className="badge badge-sm ba
                                    dge-info font-medium">{doc.progress}%</span>}
                                        {doc.status === 'PENDING' && <span className="badge badge-sm badge-warning animate-pulse">等待</span>}
                                        {doc.status === 'FAILED' && <span className="badge badge-sm badge-error font-medium" title={doc.errorMessage}>失败</span>}
                                    </div>
                                    <div className="flex items-center gap-3 text-xs text-base-content/60">
                                        <p>
                                          {doc.uploadedAt ? format(new Date(doc.uploadedAt), 'yyyy-MM-dd HH:mm') : '-'}
                                        </p>
                                        {doc.status === 'PROCESSING' && (
                                          <progress className="progress progress-info w-32 h-1" value={doc.progress || 0} max="100"></progress>
                                        )}
                                    </div>
                                  </div>
                                </div>

                                <div className="flex items-center gap-2">
                                    {selectedConversation && (
                                        <button
                                            className={`btn btn-sm ${linked ? 'btn-primary' : 'btn-outline'}`}
                                            onClick={() => handleToggleConversationDoc(doc.id, !linked)}  
                                        >
                                            {linked ? <><BiCheck /> 已关联</> : <><BiPlus /> 添加</>} 
                                        </button>
                                    )}
                                    
                                    {currentUser && (doc.userId === currentUser.id || currentUser.role === 'ADMIN') && (
                                        <button
                                            className="btn btn-sm btn-ghost btn-circle text-error hover:bg-error/10 transition-colors"
                                            onClick={() => handleDeleteGlobal(doc.id)}
                                            title="彻底删除文档"
                                        >
                                            <BiTrash />
                                        </button>
                                    )}
                                </div>
                              </div>
                            </div>
                        );
                    })}
                 </div>
             )}
          </div>
        </div>
      </div>
      <div className="modal-backdrop bg-black/50" onClick={onClose}></div>
      <ConfirmModal {...modalConfig} />
    </div>
  );
}

export default DocumentManager;