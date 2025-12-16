import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../utils/api';
import toast from 'react-hot-toast';

function RegisterPage() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await api.post('/auth/register', { username, email, password });
      toast.success('注册成功，请登录');
      navigate('/login');
    } catch (error) {
      toast.error(error.response?.data?.message || '注册失败');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-base-200 p-4 pattern-grid">
      <div className="card w-full max-w-sm shadow-2xl bg-base-100 glass-card">
        <form className="card-body" onSubmit={handleSubmit}>
          <h2 className="text-2xl font-bold text-center mb-2">注册</h2>
          <p className="text-center text-base-content/70 text-sm mb-6">
            基于检索增强生成的智能问答系统
          </p>
          <div className="form-control">
            <label className="label">
              <span className="label-text">用户名</span>
            </label>
            <input
              type="text"
              placeholder="用户名"
              className="input input-bordered w-full focus:outline-offset-2 focus:outline-primary-focus"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="form-control">
            <label className="label">
              <span className="label-text">邮箱</span>
            </label>
            <input
              type="email"
              placeholder="邮箱"
              className="input input-bordered w-full focus:outline-offset-2 focus:outline-primary-focus"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="form-control">
            <label className="label">
              <span className="label-text">密码</span>
            </label>
            <input
              type="password"
              placeholder="密码"
              className="input input-bordered w-full focus:outline-offset-2 focus:outline-primary-focus"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="form-control mt-6">
            <button type="submit" className="btn btn-primary btn-block">注册</button>
          </div>
          <label className="label justify-center">
            <Link to="/login" className="label-text-alt link link-hover">已有账号？登录</Link>
          </label>
        </form>
      </div>
    </div>
  );
}

export default RegisterPage;