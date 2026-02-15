import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import TeacherSidebar from '../../components/Sidebar/TeacherSidebar';
import AdviserSidebar from '../../components/Sidebar/AdviserSidebar';
import './Profile.css';

const API_BASE_URL = 'http://localhost:8080';

const Profile = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const oauthHandledRef = useRef(false);
  const [user, setUser] = useState(null);
  const [googleLinkStatus, setGoogleLinkStatus] = useState({
    isLinked: false,
    googleEmail: null,
    message: ''
  });
  const [loading, setLoading] = useState(true);
  const [linkingGoogle, setLinkingGoogle] = useState(false);

  const getAuthToken = () => {
    const user = JSON.parse(localStorage.getItem('user'));
    return user?.token || null;
  };

  const getHeaders = () => {
    const headers = {
      'Content-Type': 'application/json',
    };
    const token = getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  };

  useEffect(() => {
    fetchUserProfile();
    checkGoogleLinkStatus();
  }, []);

  const fetchUserProfile = async () => {
    try {
      const currentUser = authAPI.getCurrentUser();
      setUser(currentUser);
    } catch (error) {
      toast.error('Error fetching user profile');
    } finally {
      setLoading(false);
    }
  };

  const checkGoogleLinkStatus = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/google-auth/status`, {
        method: 'GET',
        headers: getHeaders(),
      });
      if (response.ok) {
        const data = await response.json();
        setGoogleLinkStatus(data);
      }
    } catch (error) {
      toast.error('Error checking Google link status');
    }
  };

  const handleLinkGoogleAccount = async () => {
    try {
      setLinkingGoogle(true);
      oauthHandledRef.current = false;
      const response = await fetch(`${API_BASE_URL}/api/google-auth/authorization-url`, {
        method: 'GET',
        headers: getHeaders(),
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
      
      const popup = window.open(
        data.authUrl,
        'Google OAuth',
        `width=${width},height=${height},left=${left},top=${top}`
      );

      // Listen for OAuth callback
      window.removeEventListener('message', handleOAuthCallback);
      window.addEventListener('message', handleOAuthCallback);

      // Fallback cleanup (avoid popup.closed polling because it triggers COOP warnings in some browsers)
      setTimeout(() => {
        setLinkingGoogle(false);
        window.removeEventListener('message', handleOAuthCallback);
      }, 2 * 60 * 1000);
    } catch (error) {
      toast.error('Failed to initiate Google account linking');
      setLinkingGoogle(false);
    }
  };

  const handleOAuthCallback = async (event) => {
    // Verify origin for security
    if (event.origin !== window.location.origin) return;
    
    if (event.data.type === 'GOOGLE_OAUTH_CODE') {
      if (oauthHandledRef.current) {
        return;
      }
      oauthHandledRef.current = true;
      window.removeEventListener('message', handleOAuthCallback);

      try {
        const response = await fetch(`${API_BASE_URL}/api/google-auth/callback`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify({ code: event.data.code }),
        });
        
        if (!response.ok) {
          const errText = await response.text().catch(() => '');
          throw new Error(errText || 'Failed to link account');
        }
        
        const data = await response.json();
        setGoogleLinkStatus(data);
        toast.success(data.message);

        await checkGoogleLinkStatus();
      } catch (error) {
        toast.error('Failed to link Google account');
      } finally {
        setLinkingGoogle(false);
      }
    }
  };

  const handleUnlinkGoogleAccount = async () => {
    if (!window.confirm('Are you sure you want to unlink your Google account? You will no longer be able to create Google Forms.')) {
      return;
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/google-auth/unlink`, {
        method: 'DELETE',
        headers: getHeaders(),
      });
      
      if (!response.ok) {
        throw new Error('Failed to unlink account');
      }
      
      const data = await response.json();
      setGoogleLinkStatus(data);
      toast.success(data.message);
    } catch (error) {
      toast.error('Failed to unlink Google account');
    }
  };

  if (loading) {
    return <div className="profile-container">Loading...</div>;
  }

  return (
    <div className="profile-container">
      {user?.role === 'TEACHER' ? <TeacherSidebar /> : <AdviserSidebar />}
      
      <div className="profile-content">
        <div className="profile-header">
          <h1>My Profile</h1>
        </div>

        {/* User Information Section */}
        <div className="profile-section">
          <h2>Account Information</h2>
          <div className="profile-info">
            <div className="info-row">
              <label>Name:</label>
              <span>{user?.firstName} {user?.lastName}</span>
            </div>
            <div className="info-row">
              <label>Email:</label>
              <span>{user?.email}</span>
            </div>
            <div className="info-row">
              <label>Role:</label>
              <span>{user?.role}</span>
            </div>
            {user?.department && (
              <div className="info-row">
                <label>Department:</label>
                <span>{user.department}</span>
              </div>
            )}
            {user?.phoneNumber && (
              <div className="info-row">
                <label>Phone:</label>
                <span>{user.phoneNumber}</span>
              </div>
            )}
          </div>
        </div>

        {/* Google Account Linking Section - Only for TEACHER role */}
        {user?.role === 'TEACHER' && (
          <div className="profile-section google-section">
            <h2>Google Account Integration</h2>
            <p className="section-description">
              Link your Google account to create and manage Google Forms for student evaluations.
            </p>

            <div className="google-link-status">
              {googleLinkStatus.isLinked ? (
                <>
                  <div className="status-badge linked">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    <span>Google Account Linked</span>
                  </div>
                  <div className="linked-email">
                    <label>Linked Account:</label>
                    <span>{googleLinkStatus.googleEmail}</span>
                  </div>
                  <button 
                    className="btn-unlink"
                    onClick={handleUnlinkGoogleAccount}
                  >
                    Unlink Google Account
                  </button>
                </>
              ) : (
                <>
                  <div className="status-badge not-linked">
                    <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                    </svg>
                    <span>No Google Account Linked</span>
                  </div>
                  <button 
                    className="btn-link-google"
                    onClick={handleLinkGoogleAccount}
                    disabled={linkingGoogle}
                  >
                    <svg width="18" height="18" viewBox="0 0 18 18">
                      <path fill="#4285F4" d="M17.64,9.2c0-0.637-0.057-1.251-0.164-1.84H9v3.481h4.844c-0.209,1.125-0.843,2.078-1.796,2.717v2.258h2.908C16.658,14.137,17.64,11.888,17.64,9.2z"/>
                      <path fill="#34A853" d="M9,18c2.43,0,4.467-0.806,5.956-2.184l-2.908-2.258c-0.806,0.54-1.837,0.86-3.048,0.86c-2.344,0-4.328-1.584-5.036-3.711H0.957v2.332C2.438,15.983,5.482,18,9,18z"/>
                      <path fill="#FBBC05" d="M3.964,10.707c-0.18-0.54-0.282-1.117-0.282-1.707s0.102-1.167,0.282-1.707V4.961H0.957C0.347,6.175,0,7.55,0,9s0.348,2.825,0.957,4.039L3.964,10.707z"/>
                      <path fill="#EA4335" d="M9,3.582c1.321,0,2.508,0.454,3.44,1.345l2.582-2.582C13.463,0.891,11.426,0,9,0C5.482,0,2.438,2.017,0.957,4.961L3.964,7.293C4.672,5.166,6.656,3.582,9,3.582z"/>
                    </svg>
                    {linkingGoogle ? 'Linking...' : 'Link Google Account'}
                  </button>
                  <p className="info-text">
                    You'll be redirected to Google to authorize access to Google Forms.
                  </p>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Profile;
