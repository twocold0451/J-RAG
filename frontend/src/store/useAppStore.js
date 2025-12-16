import { create } from 'zustand';

const useAppStore = create((set) => ({
  currentUser: null,
  conversations: [],
  selectedConversation: null,
  documents: [], // Global document library
  currentConversationDocuments: [], // Documents linked to the selected conversation

  setCurrentUser: (user) => set({ currentUser: user }),
  setConversations: (convs) => set({ conversations: convs }),
  addConversation: (conv) => set((state) => ({ conversations: [conv, ...state.conversations] })),
  setSelectedConversation: (conv) => set({ selectedConversation: conv }),
  setDocuments: (docs) => set({ documents: docs }),
  addDocument: (doc) => set((state) => ({ documents: [doc, ...state.documents] })),
    removeDocument: (docId) => set((state) => ({ documents: state.documents.filter(doc => doc.id !== docId) })),
    updateDocument: (docId, updates) => set((state) => ({
      documents: state.documents.map(doc => doc.id === docId ? { ...doc, ...updates } : doc)
    })),
  
    setCurrentConversationDocuments: (docs) => set({ currentConversationDocuments: docs }),  addToCurrentConversationDocuments: (doc) => set((state) => ({ 
    currentConversationDocuments: [...state.currentConversationDocuments, doc] 
  })),
  removeFromCurrentConversationDocuments: (docId) => set((state) => ({
    currentConversationDocuments: state.currentConversationDocuments.filter(doc => doc.id !== docId)
  })),

  clearState: () => set({
    currentUser: null,
    conversations: [],
    selectedConversation: null,
    documents: [],
    currentConversationDocuments: []
  }),
}));

export default useAppStore;
