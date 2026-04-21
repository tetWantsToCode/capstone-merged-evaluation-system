/**
 * APEER API service — same backend as main app. CRA-compatible (no import.meta).
 */
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const getAuthToken = () => {
  const userStr = localStorage.getItem('user');
  if (!userStr) return null;
  const user = JSON.parse(userStr);
  return user?.token || null;
};

const getHeaders = (includeAuth = true) => {
  const headers = { 'Content-Type': 'application/json' };
  if (includeAuth) {
    const token = getAuthToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

export const apeerAuthAPI = {
  getCurrentUser: () => {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  },
  isAuthenticated: () => !!getAuthToken(),
};

export const apeerClassAPI = {
  getAllClasses: async () => {
    const res = await fetch(`${API_BASE_URL}/classes`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch classes');
    return res.json();
  },
};

export const apeerTeamAPI = {
  getAllTeams: async () => {
    const res = await fetch(`${API_BASE_URL}/teams`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch teams');
    return res.json();
  },
};

export const apeerStudentAPI = {
  getAllStudents: async () => {
    const res = await fetch(`${API_BASE_URL}/students`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch students');
    return res.json();
  },
  importStudents: async (formData) => {
    const token = getAuthToken();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const res = await fetch(`${API_BASE_URL}/students/import`, { method: 'POST', headers, body: formData });
    if (!res.ok) throw new Error('Failed to import students');
    return res.json();
  },
};

export const apeerQuestionnaireAPI = {
  getAllQuestionnaires: async () => {
    const res = await fetch(`${API_BASE_URL}/questionnaires`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch questionnaires');
    return res.json();
  },
};

export const apeerEvaluationAPI = {
  getAllEvaluations: async () => {
    const res = await fetch(`${API_BASE_URL}/evaluations`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch evaluations');
    return res.json();
  },
};

export const apeerActivityAPI = {
  getActivities: async () => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch activities');
    return res.json();
  },
  getActivitiesForStudent: async () => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/for-student`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch activities');
    return res.json();
  },
  getActivity: async (id) => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/${id}`, { method: 'GET', headers: getHeaders() });
    if (!res.ok) throw new Error('Failed to fetch activity');
    return res.json();
  },
  createActivity: async (data) => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to create activity');
    }
    return res.json();
  },
  getMembers: async (activityId) => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/${activityId}/members`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch members');
    return res.json();
  },

  getActivitySummary: async (activityId) => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/${activityId}/summary`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch activity summary');
    return res.json();
  },
};

export const apeerStudentInsightsAPI = {
  getAiSummary: async () => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/student/ai-summary`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch AI summary');
    return res.json();
  },
  getHistory: async () => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/student/history`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch evaluation history');
    return res.json();
  },
};

export const apeerSubmissionAPI = {
  getSubmissionStatus: async (activityId) => {
    const res = await fetch(`${API_BASE_URL}/apeer/activities/${activityId}/submission-status`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error('Failed to fetch status');
    return res.json();
  },
  submit: async (data) => {
    const res = await fetch(`${API_BASE_URL}/apeer/evaluations/submit`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(data),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to submit');
    }
    return res.json();
  },
};

export default {
  apeerAuthAPI,
  apeerClassAPI,
  apeerTeamAPI,
  apeerStudentAPI,
  apeerQuestionnaireAPI,
  apeerEvaluationAPI,
  apeerActivityAPI,
  apeerSubmissionAPI,
};
