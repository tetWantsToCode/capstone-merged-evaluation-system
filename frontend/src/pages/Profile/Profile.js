import React, { useState, useEffect } from 'react';
import { authAPI } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import TeacherSidebar from '../../components/Sidebar/TeacherSidebar';
import AdviserSidebar from '../../components/Sidebar/AdviserSidebar';
import './Profile.css';

const API_BASE_URL = (process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api').replace(/\/api\/?$/, '');
const LOCAL_API_BASE_URL = 'http://localhost:8080';

const fetchWithLocalFallback = async (path, options = {}) => {
  try {
    return await fetch(`${API_BASE_URL}${path}`, options);
  } catch (error) {
    if (API_BASE_URL === LOCAL_API_BASE_URL) {
      throw error;
    }
    return fetch(`${LOCAL_API_BASE_URL}${path}`, options);
  }
};

const Profile = () => {
  const toast = useToast();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const [googleStatus, setGoogleStatus] = useState({
    loading: false,
    isLinked: false,
    googleEmail: null,
    message: null,
  });

  useEffect(() => {
    fetchUserProfile();
  }, []);

  useEffect(() => {
    if (user?.role === 'TEACHER') {
      fetchGoogleLinkStatus();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.role]);

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

  const getToken = () => {
    try {
      const currentUser = authAPI.getCurrentUser();
      return currentUser?.token || null;
    } catch {
      return null;
    }
  };

  const fetchGoogleLinkStatus = async () => {
    const token = getToken();
    if (!token) return;

    try {
      setGoogleStatus((prev) => ({ ...prev, loading: true }));
      const res = await fetchWithLocalFallback('/api/google-auth/status', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || `Failed to load Google link status (HTTP ${res.status})`);
      }

      setGoogleStatus({
        loading: false,
        isLinked: !!data?.isLinked,
        googleEmail: data?.googleEmail || null,
        message: data?.message || null,
      });
    } catch (e) {
      setGoogleStatus((prev) => ({ ...prev, loading: false }));
      toast.error(e.message || 'Failed to load Google link status');
    }
  };

  const startGoogleLink = async () => {
    const token = getToken();
    if (!token) {
      toast.error('You are not authenticated. Please log in again.');
      return;
    }

    try {
      setGoogleStatus((prev) => ({ ...prev, loading: true }));

      const res = await fetchWithLocalFallback('/api/google-auth/authorization-url', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || `Failed to start Google linking (HTTP ${res.status})`);
      }

      const authUrl = data?.authUrl;
      if (!authUrl) {
        throw new Error('Missing authUrl from server');
      }

      const width = 520;
      const height = 640;
      const left = window.screenX + (window.outerWidth - width) / 2;
      const top = window.screenY + (window.outerHeight - height) / 2;
      const popup = window.open(
        authUrl,
        'Google Link',
        `width=${width},height=${height},left=${left},top=${top}`
      );

      if (!popup) {
        throw new Error('Popup was blocked. Please allow popups and try again.');
      }

      const onMessage = async (event) => {
        if (event.origin !== window.location.origin) return;
        if (event.data?.type !== 'GOOGLE_OAUTH_CODE') return;

        window.removeEventListener('message', onMessage);

        try {
          const cbRes = await fetch(`${API_BASE_URL}/api/google-auth/callback`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ code: event.data.code }),
          });

          const cbData = await cbRes.json().catch(() => null);
          if (!cbRes.ok) {
            throw new Error(cbData?.message || `Failed to link Google account (HTTP ${cbRes.status})`);
          }

          toast.success('Google account linked successfully');
          await fetchGoogleLinkStatus();
        } catch (err) {
          toast.error(err.message || 'Failed to link Google account');
          setGoogleStatus((prev) => ({ ...prev, loading: false }));
        }
      };

      window.addEventListener('message', onMessage);
    } catch (e) {
      setGoogleStatus((prev) => ({ ...prev, loading: false }));
      toast.error(e.message || 'Failed to start Google linking');
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
          </div>
        </div>

        {user?.role === 'TEACHER' && (
          <div className="profile-section google-section">
            <h2>Google Account</h2>

            <div className="google-link-status">
              {googleStatus.googleEmail && (
                <div className="linked-email">
                  <label>Google email</label>
                  <span>{googleStatus.googleEmail}</span>
                </div>
              )}

              {!googleStatus.isLinked ? (
                <button className="btn-link-google" onClick={startGoogleLink} disabled={googleStatus.loading}>
                  Link Google Account
                </button>
              ) : null}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default Profile;
