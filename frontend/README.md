# J-RAG Frontend

This is the frontend application for the J-RAG system, built with modern React ecosystem tools.

## 🛠️ Tech Stack

- **Framework**: [React 19](https://react.dev/)
- **Build Tool**: [Vite](https://vitejs.dev/)
- **Language**: TypeScript
- **Styling**: [Tailwind CSS](https://tailwindcss.com/) + [DaisyUI](https://daisyui.com/)
- **State Management**: [Zustand](https://github.com/pmndrs/zustand)
- **Routing**: [React Router v6](https://reactrouter.com/)
- **HTTP Client**: Axios
- **Real-time**: WebSocket (STOMP / SockJS)
- **Markdown Rendering**: react-markdown + remark-gfm

## 🚀 Getting Started

### 1. Installation

Navigate to the frontend directory and install dependencies:

```bash
cd frontend
npm install
```

### 2. Environment Setup

Create a `.env` file in the `frontend` root directory to configure the backend API connection.

```properties
# .env
VITE_API_BASE_URL=http://localhost:8080/api
```

> **Note**: If you don't create this file, ensure your code has a fallback or that your Vite proxy is configured correctly in `vite.config.js`.

### 3. Development

Start the development server:

```bash
npm run dev
```

The app will be available at `http://localhost:5173`.

### 4. Production Build

To build the application for production:

```bash
npm run build
```

The output will be in the `dist` directory. You can preview the production build locally:

```bash
npm run preview
```

## 📂 Project Structure

```
frontend/
├── public/          # Static assets
├── src/
│   ├── components/  # Reusable UI components
│   ├── contexts/    # React Contexts (Auth, etc.)
│   ├── hooks/       # Custom React Hooks
│   ├── pages/       # Route pages (Login, Chat, etc.)
│   ├── services/    # API service calls (Axios)
│   ├── store/       # Zustand state stores
│   ├── types/       # TypeScript interfaces
│   ├── utils/       # Helper functions
│   ├── App.tsx      # Main application component
│   └── main.tsx     # Entry point
└── index.html
```

## 🎨 UI & Styling

We use **DaisyUI** components styled with **Tailwind CSS**. Theme configuration can be found in `tailwind.config.js`.

## 🤝 Contribution

Please ensure your code follows the project's coding standards.
