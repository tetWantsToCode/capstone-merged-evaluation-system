import React, { useMemo, useState } from 'react';
import TeacherSidebar from '../../components/Sidebar/TeacherSidebar';
import { useToast } from '../../contexts/ToastContext';
import './Teacher.css';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const AiAssistant = () => {
  const toast = useToast();
  const [messages, setMessages] = useState([
    { role: 'assistant', text: 'Hi! Tell me what questionnaire you want to create or improve.' },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const token = useMemo(() => {
    try {
      const user = JSON.parse(localStorage.getItem('user'));
      return user?.token || null;
    } catch {
      return null;
    }
  }, []);

  const sendMessage = async () => {
    const trimmed = input.trim();
    if (!trimmed) return;
    if (trimmed.length > 2000) {
      toast.error('Message is too long (max 2000 characters).');
      return;
    }
    if (!token) {
      toast.error('You are not authenticated. Please log in again.');
      return;
    }

    setMessages((prev) => [...prev, { role: 'user', text: trimmed }]);
    setInput('');
    setLoading(true);

    try {
      const res = await fetch(`${API_BASE_URL}/ai/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ message: trimmed }),
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || `AI request failed (HTTP ${res.status})`);
      }

      setMessages((prev) => [...prev, { role: 'assistant', text: data.reply }]);
    } catch (e) {
      toast.error(e.message || 'AI request failed');
      setMessages((prev) => [...prev, { role: 'assistant', text: 'Sorry—something went wrong calling the AI.' }]);
    } finally {
      setLoading(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!loading) sendMessage();
    }
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>AI Assistant</h1>

        <div className="section">
          <div style={{ whiteSpace: 'pre-wrap' }}>
            {messages.map((m, idx) => (
              <p key={idx}>
                <strong>{m.role === 'user' ? 'You' : 'AI'}:</strong> {m.text}
              </p>
            ))}
          </div>
        </div>

        <div className="section">
          <textarea
            className="form-input"
            rows={3}
            placeholder="Ask the AI to draft questions, improve wording, suggest scales, etc."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
            disabled={loading}
          />
          <button className="btn-primary" onClick={sendMessage} disabled={loading || !input.trim()}>
            {loading ? 'Sending...' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AiAssistant;
