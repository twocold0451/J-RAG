import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ChatPage from './pages/ChatPage';
import { Toaster } from 'react-hot-toast';
import AuthGuard from './components/AuthGuard';

function App() {
  return (
    <Router>
      <Toaster position="top-center" reverseOrder={false} />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <AuthGuard>
              <ChatPage />
            </AuthGuard>
          }
        />
      </Routes>
    </Router>
  );
}

export default App;
