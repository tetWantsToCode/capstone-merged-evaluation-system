import { useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useToast } from '../contexts/ToastContext';
import { authAPI } from '../services/api';

const InactivityTimeout = ({ timeout = 3600000 }) => { // Default to 1 hour (3600000 ms)
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const timerRef = useRef(null);

  const isAuthPage = location.pathname === '/login' || location.pathname === '/' || location.pathname === '/register';

  const resetTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    
    // Don't run the inactivity timer on login/auth pages
    if (isAuthPage) return;

    timerRef.current = setTimeout(() => {
      // Logout logic
      authAPI.logout();
      localStorage.removeItem('token'); // Just in case it's used elsewhere
      toast.warning('You have been logged out due to 1 hour of inactivity.');
      navigate('/login', { replace: true });
    }, timeout);
  };

  useEffect(() => {
    if (isAuthPage) {
      if (timerRef.current) clearTimeout(timerRef.current);
      return;
    }

    // Events to track user activity
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart'];
    
    const handleActivity = () => {
      resetTimer();
    };

    // Set initial timer
    resetTimer();

    // Add event listeners
    events.forEach(event => {
      document.addEventListener(event, handleActivity);
    });

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      events.forEach(event => {
        document.removeEventListener(event, handleActivity);
      });
    };
  }, [isAuthPage, resetTimer, location.pathname, timeout]);

  return null;
};

export default InactivityTimeout;
