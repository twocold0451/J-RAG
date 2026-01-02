# 企业知识库系统前端

基于 RAG 技术的企业级知识管理平台前端项目。

## 技术栈

- React 19 + TypeScript + Vite
- Tailwind CSS + DaisyUI
- Zustand 状态管理
- React Router 路由
- React Markdown Markdown 渲染

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

访问 http://localhost:5173

### 环境变量配置

创建 `.env.local` 文件：

```env
# 开发环境
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=企业知识库
```

### 构建生产版本

```bash
npm run build
```

产物在 `dist` 目录，可部署到任意静态托管服务（Vercel、Netlify、Zeabur 等）。


## 目录结构

```
src/
├── api/           # API 调用封装
├── components/    # 通用组件
├── hooks/         # 自定义 Hooks
├── pages/         # 页面组件
├── store/         # Zustand 状态管理
├── types/         # TypeScript 类型定义
└── utils/         # 工具函数
```
