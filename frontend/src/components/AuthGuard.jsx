import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import useAppStore from '../store/useAppStore';
import api from '../utils/api';
import toast from 'react-hot-toast';
import useWebSocket from '../hooks/useWebSocket';

function AuthGuard({ children }) {
  const navigate = useNavigate();
  const { currentUser, setCurrentUser, clearState } = useAppStore();
  useWebSocket();

  useEffect(() => {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
      navigate('/login');
      toast.error('请先登录');
      return;
    }

    // Validate token on component mount or if currentUser is null
    if (!currentUser) {
      api.get('/auth/me')
        .then(response => {
          setCurrentUser(response.data);
        })
        .catch(() => {
          localStorage.removeItem('jwtToken');
          clearState();
          navigate('/login');
          toast.error('登录状态失效，请重新登录');
        });
    }
  }, [currentUser, setCurrentUser, clearState, navigate]);

  if (!currentUser) {
    return <div className="min-h-screen flex items-center justify-center bg-base-200">加载中...</div>;
  }

  return <>{children}</>;
}

export default AuthGuard;
