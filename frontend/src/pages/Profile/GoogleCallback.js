import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

const GoogleCallback = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    if (error) {
      console.error('OAuth error:', error);
      if (window.opener) {
        window.opener.postMessage({
          type: 'GOOGLE_OAUTH_ERROR',
          error: 'Failed to link Google account'
        }, window.location.origin);
        window.close();
      } else {
        navigate('/profile?error=google_link_failed');
      }
      return;
    }

    if (code) {
      // Send code to parent window
      if (window.opener) {
        window.opener.postMessage({
          type: 'GOOGLE_OAUTH_CODE',
          code: code
        }, window.location.origin);
        window.close();
      } else {
        // If not in popup, redirect to profile page
        // The parent page should handle the callback
        navigate('/profile');
      }
    }
  }, [searchParams, navigate]);

  return (
    <div style={{ 
      display: 'flex', 
      justifyContent: 'center', 
      alignItems: 'center', 
      height: '100vh',
      flexDirection: 'column',
      gap: '20px'
    }}>
      <div className="spinner"></div>
      <p>Completing Google account linking...</p>
    </div>
  );
};

export default GoogleCallback;
