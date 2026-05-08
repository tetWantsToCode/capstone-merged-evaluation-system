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

const AI_PROVIDERS = [
  { value: 'gemini',    label: 'Google Gemini',  hint: 'Get your key at aistudio.google.com' },
  { value: 'openai',    label: 'OpenAI (ChatGPT)', hint: 'Get your key at platform.openai.com/api-keys' },
  { value: 'anthropic', label: 'Anthropic (Claude)', hint: 'Get your key at console.anthropic.com' },
];

const Profile = () => {
  const toast = useToast();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const [googleSheetsUrl, setGoogleSheetsUrl] = useState('');
  const [sheetsUrlLoading, setSheetsUrlLoading] = useState(false);
  const [sheetsUrlEditing, setSheetsUrlEditing] = useState(false);

  // AI key state
  const [aiProvider, setAiProvider] = useState('gemini');
  const [aiApiKey, setAiApiKey] = useState('');
  const [aiHasKey, setAiHasKey] = useState(false);
  const [aiKeyEditing, setAiKeyEditing] = useState(false);
  const [aiKeyLoading, setAiKeyLoading] = useState(false);
  const [showAiKey, setShowAiKey] = useState(false);

  useEffect(() => {
    fetchUserProfile();
  }, []);

  useEffect(() => {
    if (user?.role === 'TEACHER') {
      fetchGoogleSheetsUrl();
      fetchAiKey();
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

  const fetchGoogleSheetsUrl = async () => {
    const token = getToken();
    if (!token) return;

    try {
      const res = await fetch(`${API_BASE_URL}/api/user-management/google-sheets/url`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const data = await res.json().catch(() => null);
      if (res.ok && data?.googleSheetsUrl) {
        setGoogleSheetsUrl(data.googleSheetsUrl);
      }
    } catch (e) {
      // Silent fail
    }
  };

  const fetchAiKey = async () => {
    const token = getToken();
    if (!token) return;

    try {
      const res = await fetch(`${API_BASE_URL}/api/user-management/ai-key`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data) {
        setAiHasKey(data.hasKey || false);
        if (data.aiProvider) setAiProvider(data.aiProvider);
      }
    } catch (e) {
      // Silent fail
    }
  };

  const handleSaveGoogleSheetsUrl = async () => {
    const token = getToken();
    if (!token) {
      toast.error('You are not authenticated');
      return;
    }

    if (!googleSheetsUrl.trim()) {
      toast.error('Please enter a Google Sheets URL');
      return;
    }

    try {
      setSheetsUrlLoading(true);
      const res = await fetch(`${API_BASE_URL}/api/user-management/google-sheets/url`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ googleSheetsUrl: googleSheetsUrl.trim() }),
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || 'Failed to save Google Sheets URL');
      }

      setSheetsUrlEditing(false);
      toast.success('Google Sheets URL saved successfully');
    } catch (e) {
      toast.error(e.message || 'Failed to save Google Sheets URL');
    } finally {
      setSheetsUrlLoading(false);
    }
  };

  const handleSaveAiKey = async () => {
    const token = getToken();
    if (!token) {
      toast.error('You are not authenticated');
      return;
    }
    if (!aiApiKey.trim()) {
      toast.error('Please enter your API key');
      return;
    }

    try {
      setAiKeyLoading(true);
      const res = await fetch(`${API_BASE_URL}/api/user-management/ai-key`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ aiApiKey: aiApiKey.trim(), aiProvider }),
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || 'Failed to save AI key');
      }

      setAiHasKey(true);
      setAiApiKey('');
      setAiKeyEditing(false);
      setShowAiKey(false);
      toast.success('AI API key saved successfully');
    } catch (e) {
      toast.error(e.message || 'Failed to save AI key');
    } finally {
      setAiKeyLoading(false);
    }
  };

  const handleDeleteAiKey = async () => {
    const token = getToken();
    if (!token) {
      toast.error('You are not authenticated');
      return;
    }

    try {
      setAiKeyLoading(true);
      const res = await fetch(`${API_BASE_URL}/api/user-management/ai-key`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });

      const data = await res.json().catch(() => null);
      if (!res.ok) {
        throw new Error(data?.message || 'Failed to remove AI key');
      }

      setAiHasKey(false);
      setAiProvider('gemini');
      setAiApiKey('');
      setAiKeyEditing(false);
      toast.success('AI API key removed');
    } catch (e) {
      toast.error(e.message || 'Failed to remove AI key');
    } finally {
      setAiKeyLoading(false);
    }
  };

  if (loading) {
    return <div className="profile-container">Loading...</div>;
  }

  const selectedProvider = AI_PROVIDERS.find(p => p.value === aiProvider) || AI_PROVIDERS[0];

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
          <>
            {/* AI API Key Section */}
            <div className="profile-section">
              <h2>AI Assistant Settings</h2>
              <p style={{ fontSize: '13px', color: '#888', marginBottom: '16px' }}>
                Configure your own AI API key to power the AI Assistant and evaluation analytics.
                Supported providers: Google Gemini, OpenAI (ChatGPT), and Anthropic (Claude).
              </p>

              {aiKeyEditing ? (
                <div className="form-group">
                  <label style={{ marginBottom: '6px', display: 'block' }}>AI Provider *</label>
                  <select
                    value={aiProvider}
                    onChange={(e) => setAiProvider(e.target.value)}
                    style={{ marginBottom: '12px', width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #ccc' }}
                  >
                    {AI_PROVIDERS.map(p => (
                      <option key={p.value} value={p.value}>{p.label}</option>
                    ))}
                  </select>

                  <label style={{ marginBottom: '6px', display: 'block' }}>API Key *</label>
                  <div style={{ position: 'relative', marginBottom: '4px' }}>
                    <input
                      type={showAiKey ? 'text' : 'password'}
                      placeholder="Paste your API key here"
                      value={aiApiKey}
                      onChange={(e) => setAiApiKey(e.target.value)}
                      style={{ width: '100%', paddingRight: '70px' }}
                    />
                    <button
                      type="button"
                      onClick={() => setShowAiKey(v => !v)}
                      style={{
                        position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)',
                        background: 'none', border: 'none', cursor: 'pointer', fontSize: '12px', color: '#888'
                      }}
                    >
                      {showAiKey ? 'Hide' : 'Show'}
                    </button>
                  </div>
                  <p style={{ fontSize: '11px', color: '#888', marginBottom: '12px' }}>
                    {selectedProvider.hint}
                  </p>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn"
                      onClick={handleSaveAiKey}
                      disabled={aiKeyLoading}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                    >
                      {aiKeyLoading ? 'Saving...' : 'Save Key'}
                    </button>
                    <button
                      className="btn-secondary"
                      onClick={() => { setAiKeyEditing(false); setAiApiKey(''); setShowAiKey(false); }}
                      disabled={aiKeyLoading}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  {aiHasKey ? (
                    <div className="linked-email" style={{ marginBottom: '12px' }}>
                      <label>Provider</label>
                      <span>{selectedProvider.label}</span>
                      <label style={{ marginTop: '8px' }}>API Key</label>
                      <span style={{ fontFamily: 'monospace', letterSpacing: '2px' }}>••••••••••••••••</span>
                    </div>
                  ) : (
                    <p style={{ fontSize: '13px', color: '#999', fontStyle: 'italic', marginBottom: '12px' }}>
                      No AI API key configured yet
                    </p>
                   
                  )}
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn"
                      onClick={() => setAiKeyEditing(true)}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                    >
                      {aiHasKey ? 'Update Key' : 'Add AI Key'}
                    </button>
                    {aiHasKey && (
                      <button
                        className="btn-secondary"
                        onClick={handleDeleteAiKey}
                        disabled={aiKeyLoading}
                        style={{ padding: '8px 16px', fontSize: '12px', color: '#e53e3e', borderColor: '#e53e3e' }}
                      >
                        {aiKeyLoading ? 'Removing...' : 'Remove Key'}
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Google Sheets Section */}
            <div className="profile-section google-section">
              <h2>Google Sheets Auto-Export</h2>
              <p style={{ fontSize: '13px', color: '#888', marginBottom: '16px' }}>
                Configure a Google Sheet to automatically receive updates whenever student data changes (new students, evaluations, etc.)
              </p>

              {sheetsUrlEditing ? (
                <div className="form-group">
                  <label>Google Sheets URL *</label>
                  <input
                    type="text"
                    placeholder="https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit"
                    value={googleSheetsUrl}
                    onChange={(e) => setGoogleSheetsUrl(e.target.value)}
                    style={{ marginBottom: '12px' }}
                  />
                  <p style={{ fontSize: '11px', color: '#888', marginBottom: '12px' }}>
                    Share the spreadsheet with your Google account (the one you logged in with) before entering the link
                  </p>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button 
                      className="btn" 
                      onClick={handleSaveGoogleSheetsUrl} 
                      disabled={sheetsUrlLoading}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                    >
                      {sheetsUrlLoading ? 'Saving...' : 'Save'}
                    </button>
                    <button 
                      className="btn-secondary" 
                      onClick={() => setSheetsUrlEditing(false)}
                      disabled={sheetsUrlLoading}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  {googleSheetsUrl ? (
                    <div className="linked-email" style={{ marginBottom: '12px' }}>
                      <label>Connected Sheet</label>
                      <span style={{ wordBreak: 'break-all' }}>{googleSheetsUrl}</span>
                    </div>
                  ) : (
                    <p style={{ fontSize: '13px', color: '#999', fontStyle: 'italic' }}>
                      No Google Sheet configured yet
                    </p>
                  )}
                  <button 
                    className="btn" 
                    onClick={() => setSheetsUrlEditing(true)}
                    style={{ padding: '8px 16px', fontSize: '12px' }}
                  >
                    {googleSheetsUrl ? 'Update' : 'Configure'} Sheet
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default Profile;
