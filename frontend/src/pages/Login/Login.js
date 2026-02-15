import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authAPI } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import './Login.css';

function Login() {
  const navigate = useNavigate();
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      toast.info('Attempting login...');
      const data = await authAPI.login(email, password);
      
      // Store user data in localStorage
      localStorage.setItem('user', JSON.stringify(data));
      toast.success('Login successful!');
      
      // Redirect based on role
      if (data.role === 'TEACHER') {
        navigate('/teacher/dashboard');
      } else if (data.role === 'ADVISER') {
        navigate('/adviser/dashboard');
      } else {
        toast.warning('Unknown role, redirecting to home');
        navigate('/');
      }
    } catch (err) {
      toast.error('Login failed: ' + (err.message || 'Unable to connect to server'));
      setError(err.message || 'Unable to connect to server. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-box">
        <h1>Adviser Evaluation System</h1>
        <h2>Login</h2>
        {error && <div className="error-message">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Enter your email"
              required
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              required
              disabled={loading}
            />
          </div>
          <button type="submit" className="login-button" disabled={loading}>
            {loading ? 'Logging in...' : 'Login'}
          </button>
          <div className="register-link">
            Don't have an account? <Link to="/register">Register here</Link>
          </div>
        </form>
      </div>
    </div>
  );
}

export default Login;
