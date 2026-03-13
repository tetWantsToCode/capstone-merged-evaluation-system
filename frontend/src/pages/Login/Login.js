import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../../contexts/ToastContext';
import TextPressure from '../../components/TextPressure/TextPressure';
import Aurora from '../../components/Aurora/Aurora';
import BlurText from '../../components/BlurText/BlurText';
import '../../components/TextPressure/TextPressure.css';
import '../../components/Aurora/Aurora.css';
import './Login.css';

const API_BASE_URL = 'http://localhost:8080';

function Login() {
  const navigate = useNavigate();
  const toast = useToast();
  const oauthHandledRef = useRef(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Listen for OAuth callback
    window.addEventListener('message', handleOAuthCallback);

    return () => {
      window.removeEventListener('message', handleOAuthCallback);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const redirectByRole = (role) => {
    if (role === 'TEACHER') navigate('/teacher/dashboard');
    else if (role === 'ADVISER') navigate('/adviser/dashboard');
    else if (role === 'STUDENT') navigate('/student/dashboard');
    else navigate('/login');
  };

  const handleOAuthCallback = async (event) => {
    // Verify origin for security
    if (event.origin !== window.location.origin) return;

    if (event.data.type === 'GOOGLE_OAUTH_CODE') {
      if (oauthHandledRef.current) {
        return;
      }
      oauthHandledRef.current = true;

      setLoading(true);
      setError('');

      try {
        toast.info('Completing sign in...');

        const response = await fetch(`${API_BASE_URL}/api/auth/google/callback`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ code: event.data.code }),
        });

        if (!response.ok) {
          const errData = await response.json().catch(() => ({ message: 'Login failed' }));
          throw new Error(errData.message || 'Login failed');
        }

        const data = await response.json();

        // Store user data with token
        localStorage.setItem('user', JSON.stringify(data));

        toast.success('Login successful!');
        redirectByRole(data.role);
      } catch (err) {
        toast.error('Google login failed: ' + (err.message || 'Unknown error'));
        setError(err.message || 'Google login failed');
        oauthHandledRef.current = false;
      } finally {
        setLoading(false);
      }
    }
  };

  const handleGoogleLogin = async () => {
    try {
      setLoading(true);
      setError('');
      oauthHandledRef.current = false;

      // Get authorization URL
      const response = await fetch(`${API_BASE_URL}/api/auth/google/authorization-url`, {
        method: 'GET',
      });

      if (!response.ok) {
        throw new Error('Failed to get authorization URL');
      }

      const data = await response.json();

      // Open Google OAuth in a popup
      const width = 500;
      const height = 600;
      const left = window.screenX + (window.outerWidth - width) / 2;
      const top = window.screenY + (window.outerHeight - height) / 2;

      window.open(
        data.authUrl,
        'Google OAuth',
        `width=${width},height=${height},left=${left},top=${top}`
      );
    } catch (err) {
      toast.error('Failed to initiate Google login');
      setError(err.message || 'Failed to initiate Google login');
      setLoading(false);
    }
  };

  // Spotlight effect — track mouse on the card
  const cardRef = useRef(null);
  const handleCardMouse = useCallback((e) => {
    const card = cardRef.current;
    if (!card) return;
    const rect = card.getBoundingClientRect();
    card.style.setProperty('--mouse-x', `${e.clientX - rect.left}px`);
    card.style.setProperty('--mouse-y', `${e.clientY - rect.top}px`);
  }, []);

  return (
    <>
      <Aurora
        colorStops={['#8a151f', '#f2c94c', '#6c0f17', '#d4a843']}
        speed={8}
      />
      <div className="login-container">
        <div className="login-box" ref={cardRef} onMouseMove={handleCardMouse}>
          <div className="text-pressure-wrapper">
            <TextPressure
              text="SAES"
              flex
              alpha={false}
              stroke={false}
              width
              weight
              italic
              textColor="#f2c94c"
              strokeColor="#8a151f"
              minFontSize={42}
            />
          </div>
          <h3>
            <BlurText
              text="Student and Adviser Evaluation System"
              delay={60}
              animateBy="words"
            />
          </h3>

        <div className="google-button-container">
          <button
            onClick={handleGoogleLogin}
            disabled={loading}
            className="google-login-btn"
          >
            <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
              <path fill="none" d="M0 0h48v48H0z"/>
            </svg>
            {loading ? 'Signing in...' : 'Sign in with Google'}
          </button>
        </div>
      </div>
    </div>
    </>
  );
}

export default Login;