import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../utils/api';
import toast from 'react-hot-toast';

function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await api.post('/auth/login', { username, password });
      localStorage.setItem('jwtToken', response.data.token);
      toast.success('登录成功');
      navigate('/');
    } catch (error) {
      toast.error(error.response?.data?.message || '登录失败');
    }
  };

  return (
    <div className="min-h-screen flex bg-base-200 p-4 pattern-grid">
      {/* 电脑端左侧品牌区域 */}
      <div className="hidden lg:flex lg:w-1/2 items-center justify-center p-8">
        <div className="max-w-lg">
          {/* Logo */}
          <div className="mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-secondary shadow-lg">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>

          <h1 className="text-5xl font-bold mb-4">QARAG</h1>
          <p className="text-xl text-base-content/70 mb-8">
            基于检索增强生成的智能问答系统
          </p>

          <div className="space-y-4">
            <div className="flex items-start space-x-3">
              <div className="mt-1">
                <svg className="h-5 w-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">智能问答</h3>
                <p className="text-base-content/60">基于大语言模型的智能对话系统</p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <div className="mt-1">
                <svg className="h-5 w-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">检索增强</h3>
                <p className="text-base-content/60">结合向量数据库的精准信息检索</p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <div className="mt-1">
                <svg className="h-5 w-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <div>
                <h3 className="font-semibold mb-1">实时同步</h3>
                <p className="text-base-content/60">WebSocket 实时更新文档处理状态</p>
              </div>
            </div>
          </div>

          <div className="mt-12">
            <p className="text-sm text-base-content/50">
              还没有账号？
              <Link to="/register" className="link link-primary ml-2">立即注册</Link>
            </p>
          </div>
        </div>
      </div>

      {/* 右侧登录表单区域 */}
      <div className="w-full lg:w-1/2 flex items-center justify-center">
        <div className="w-full max-w-md">
          {/* 卡片堆叠效果 */}
          <div className="-mb-6 relative z-0">
            <div className="card bg-primary/10 mx-8 h-16 rounded-b-2xl rounded-t-none"></div>
          </div>

          <div className="card shadow-2xl bg-base-100 glass-card relative z-10 rounded-t-2xl">
            <form className="card-body" onSubmit={handleSubmit}>
              {/* 渐变头像 */}
              <div className="flex justify-center mb-4">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center shadow-lg">
                  <svg xmlns="http://www.w3.org/2000/svg" className="h-10 w-10 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
              </div>

              <h2 className="text-2xl font-bold text-center mb-2">登录</h2>
              <p className="text-center text-base-content/70 text-sm mb-6">
                基于检索增强生成的智能问答系统
              </p>

              <div className="form-control">
                <label className="label">
                  <span className="label-text">用户名</span>
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-base-content/40">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </span>
                  <input
                    type="text"
                    placeholder="用户名"
                    className="input input-bordered w-full pl-10 focus:outline-offset-2 focus:outline-primary-focus"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-control">
                <label className="label">
                  <span className="label-text">密码</span>
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-base-content/40">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                  </span>
                  <input
                    type="password"
                    placeholder="密码"
                    className="input input-bordered w-full pl-10 focus:outline-offset-2 focus:outline-primary-focus"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-control mt-6">
                <button type="submit" className="btn btn-primary btn-block">登录</button>
              </div>
              <label className="label justify-center lg:hidden">
                <Link to="/register" className="label-text-alt link link-hover">没有账号？注册</Link>
              </label>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;