// API Base URL
// Create React App exposes only REACT_APP_* env vars at build time.
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const readApiErrorMessage = async (response) => {
  const contentType = response.headers?.get?.('content-type') || '';
  if (contentType.includes('application/json')) {
    const data = await response.json().catch(() => null);
    return data?.message || data?.error || null;
  }

  const text = await response.text().catch(() => null);
  if (!text) return null;
  return text.length > 300 ? text.slice(0, 300) + '…' : text;
};

const throwApiError = async (response, fallbackMessage) => {
  const msg = await readApiErrorMessage(response);
  throw new Error(msg || `${fallbackMessage} (HTTP ${response.status})`);
};

const LOCAL_API_FALLBACK_URL = 'http://localhost:8080/api';

const shouldUseLocalFallback = () => API_BASE_URL !== LOCAL_API_FALLBACK_URL;

const fetchQuestionnaireWithFallback = async (path, options = {}) => {
  try {
    return await fetch(`${API_BASE_URL}${path}`, options);
  } catch (error) {
    if (!shouldUseLocalFallback()) {
      throw error;
    }

    // Some local setups accidentally point to an HTTPS endpoint with an untrusted cert.
    // Retry against local HTTP so questionnaires still load during development.
    return await fetch(`${LOCAL_API_FALLBACK_URL}${path}`, options);
  }
};

// Helper function to get auth token
const getAuthToken = () => {
  const userStr = localStorage.getItem('user');
  if (!userStr) {
    return null;
  }
  const user = JSON.parse(userStr);
  return user?.token || null;
};

// Helper function to create headers
const getHeaders = (includeAuth = true) => {
  const headers = {
    'Content-Type': 'application/json',
  };
  
  if (includeAuth) {
    const token = getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  
  return headers;
};

// Authentication API
export const authAPI = {
  login: async (email, password) => {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: getHeaders(false),
      body: JSON.stringify({ email, password }),
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Login failed');
    }
    
    const data = await response.json();
    return data;
  },
  
  register: async (userData) => {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: getHeaders(false),
      body: JSON.stringify(userData),
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Registration failed');
    }
    
    return await response.json();
  },
  
  logout: () => {
    localStorage.removeItem('user');
  },
  
  getCurrentUser: () => {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  },
  
  isAuthenticated: () => {
    const userStr = localStorage.getItem('user');
    if (!userStr) return false;
    const user = JSON.parse(userStr);
    return !!user?.token;
  },
  
  getAllUsers: async () => {
    const response = await fetch(`${API_BASE_URL}/users`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch users');
    }
    
    return await response.json();
  },
  
  getUsersByRole: async (role) => {
    const response = await fetch(`${API_BASE_URL}/users/role/${role}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch users by role');
    }
    
    return await response.json();
  },

  googleLogin: async (idToken) => {
    const response = await fetch(`${API_BASE_URL}/auth/google/login`, {
      method: 'POST',
      headers: getHeaders(false),
      body: JSON.stringify({ idToken }),
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Google login failed' }));
      throw new Error(error.message || 'Google login failed');
    }

    return await response.json();
  },

};

// User API
export const userAPI = {
  getProfile: async () => {
    const response = await fetch(`${API_BASE_URL}/users/profile`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch profile');
    }
    
    return await response.json();
  },
  
  updateProfile: async (userData) => {
    const response = await fetch(`${API_BASE_URL}/users/profile`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(userData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to update profile');
    }
    
    return await response.json();
  }
};

// Classes API
export const classAPI = {
  getAllClasses: async () => {
    const headers = getHeaders();
    const response = await fetch(`${API_BASE_URL}/classes`, {
      method: 'GET',
      headers: headers,
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch classes');
    }
    
    return await response.json();
  },

  getClassById: async (id) => {
    const response = await fetch(`${API_BASE_URL}/classes/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch class');
    }
    
    return await response.json();
  },
  
  createClass: async (classData) => {
    const response = await fetch(`${API_BASE_URL}/classes`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(classData),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to create class' }));
      throw new Error(error.message || 'Failed to create class');
    }
    
    return await response.json();
  },
  
  updateClass: async (id, classData) => {
    const response = await fetch(`${API_BASE_URL}/classes/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(classData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to update class');
    }
    
    return await response.json();
  },
  
  deleteClass: async (id) => {
    const response = await fetch(`${API_BASE_URL}/classes/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to delete class');
    }
    
    return await response.json();
  }
};

// Teams API
export const teamAPI = {
  getAllTeams: async () => {
    const response = await fetch(`${API_BASE_URL}/teams`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch teams');
    }
    
    return await response.json();
  },
  
  getTeamById: async (id) => {
    const response = await fetch(`${API_BASE_URL}/teams/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch team');
    }
    
    return await response.json();
  },
  
  createTeam: async (teamData) => {
    const response = await fetch(`${API_BASE_URL}/teams`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(teamData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to create team');
    }
    
    return await response.json();
  },
  
  updateTeam: async (id, teamData) => {
    const response = await fetch(`${API_BASE_URL}/teams/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(teamData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to update team');
    }
    
    return await response.json();
  },
  
  deleteTeam: async (id) => {
    const response = await fetch(`${API_BASE_URL}/teams/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to delete team');
    }
    
    return await response.json();
  }
};

// Evaluations API
export const evaluationAPI = {
  getAllEvaluations: async () => {
    const response = await fetch(`${API_BASE_URL}/evaluations`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch evaluations');
    }
    
    return await response.json();
  },
  
  getEvaluationById: async (id) => {
    const response = await fetch(`${API_BASE_URL}/evaluations/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch evaluation');
    }
    
    return await response.json();
  },
  
  createEvaluation: async (evaluationData) => {
    const response = await fetch(`${API_BASE_URL}/evaluations`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(evaluationData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to create evaluation');
    }
    
    return await response.json();
  },
  
  submitEvaluation: async (id, scores) => {
    const response = await fetch(`${API_BASE_URL}/evaluations/${id}/submit`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(scores),
    });
    
    if (!response.ok) {
      throw new Error('Failed to submit evaluation');
    }
    
    return await response.json();
  }
};

// Questionnaires API
export const questionnaireAPI = {
  getAllQuestionnaires: async () => {
    const response = await fetchQuestionnaireWithFallback('/questionnaires', {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch questionnaires');
    }
    
    return await response.json();
  },
  
  getQuestionnaireById: async (id) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch questionnaire');
    }
    
    return await response.json();
  },
  
  getQuestionnairesByClass: async (classId) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/class/${classId}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch questionnaires for class');
    }
    
    return await response.json();
  },

  getQuestionnairesByClassForTeacher: async (classId) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/class/${classId}/teacher`, {
      method: 'GET',
      headers: getHeaders(),
    });

    if (!response.ok) {
      throw new Error('Failed to fetch questionnaires for teacher class');
    }

    return await response.json();
  },
  
  createQuestionnaire: async (questionnaireData) => {
    const response = await fetchQuestionnaireWithFallback('/questionnaires', {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(questionnaireData),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to create questionnaire' }));
      throw new Error(error.message || 'Failed to create questionnaire');
    }
    
    return await response.json();
  },
  
  updateQuestionnaire: async (id, questionnaireData) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(questionnaireData),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to update questionnaire' }));
      throw new Error(error.message || 'Failed to update questionnaire');
    }
    
    return await response.json();
  },

  duplicateQuestionnaire: async (id) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}/duplicate`, {
      method: 'POST',
      headers: getHeaders(),
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to duplicate questionnaire' }));
      throw new Error(error.message || 'Failed to duplicate questionnaire');
    }

    return await response.json();
  },

  updateQuestionnaireStatus: async (id, isActive) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}/status`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify({ isActive }),
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to update questionnaire status' }));
      throw new Error(error.message || 'Failed to update questionnaire status');
    }

    return await response.json();
  },
  
  deleteQuestionnaire: async (id) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to delete questionnaire' }));
      throw new Error(error.message || 'Failed to delete questionnaire');
    }
    
    return await response.json();
  },
  
  assignToClasses: async (id, classIds) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}/assign`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ classIds }),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to assign questionnaire' }));
      throw new Error(error.message || 'Failed to assign questionnaire');
    }
    
    return await response.json();
  },
  
  unassignFromClasses: async (id, classIds) => {
    const response = await fetchQuestionnaireWithFallback(`/questionnaires/${id}/unassign`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ classIds }),
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Failed to unassign questionnaire' }));
      throw new Error(error.message || 'Failed to unassign questionnaire');
    }
    
    return await response.json();
  }
};

// Reports API
export const reportAPI = {
  getAllReports: async () => {
    const response = await fetch(`${API_BASE_URL}/reports`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch reports');
    }
    
    return await response.json();
  },
  
  getReportById: async (id) => {
    const response = await fetch(`${API_BASE_URL}/reports/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch report');
    }
    
    return await response.json();
  },
  
  generateReport: async (reportData) => {
    const response = await fetch(`${API_BASE_URL}/reports/generate`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(reportData),
    });
    
    if (!response.ok) {
      throw new Error('Failed to generate report');
    }
    
    return await response.json();
  },
  
  downloadReport: async (id) => {
    const response = await fetch(`${API_BASE_URL}/reports/${id}/download`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to download report');
    }
    
    return await response.blob();
  }
};

// Students API
export const studentAPI = {
  getAllStudents: async () => {
    const headers = getHeaders();
    const response = await fetch(`${API_BASE_URL}/students`, {
      method: 'GET',
      headers: headers,
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch students');
    }
    
    return await response.json();
  },
  
  getStudentById: async (id) => {
    const response = await fetch(`${API_BASE_URL}/students/${id}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch student');
    }
    
    return await response.json();
  },
  
  createStudent: async (studentData) => {
    const response = await fetch(`${API_BASE_URL}/students`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(studentData),
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to create student');
    }
    
    return await response.json();
  },
  
  updateStudent: async (id, studentData) => {
    const response = await fetch(`${API_BASE_URL}/students/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(studentData),
    });
    
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Failed to update student');
    }
    
    return await response.json();
  },
  
  deleteStudent: async (id) => {
    const response = await fetch(`${API_BASE_URL}/students/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    
    if (!response.ok) {
      throw new Error('Failed to delete student');
    }
    
    return await response.json();
  },
  
  importStudents: async (formData) => {
    const token = getAuthToken();
    const headers = {};
    
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    
    // DO NOT set Content-Type for multipart/form-data
    // Let the browser set it automatically with the correct boundary
    const response = await fetch(`${API_BASE_URL}/students/import`, {
      method: 'POST',
      headers: headers,
      body: formData,
    });
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      throw new Error(errorData?.message || 'Failed to import students: ' + response.statusText);
    }
    
    return await response.json();
  }
};

// Adviser Evaluation API
export const adviserAPI = {
  // Get teams assigned to logged-in adviser
  getMyTeams: async () => {
    const response = await fetch(`${API_BASE_URL}/adviser/teams`, {
      method: 'GET',
      headers: getHeaders(),
    });

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch adviser teams');
    }

    return await response.json();
  },

  // Get questionnaires for a team
  getTeamQuestionnaires: async (teamId) => {
    const response = await fetch(`${API_BASE_URL}/adviser/teams/${teamId}/questionnaires`, {
      method: 'GET',
      headers: getHeaders(),
    });

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch team questionnaires');
    }

    return await response.json();
  },

  getTeamEvaluationStatuses: async (teamId) => {
    const response = await fetch(`${API_BASE_URL}/adviser/teams/${teamId}/evaluation-statuses`, {
      method: 'GET',
      headers: getHeaders(),
    });

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch team evaluation statuses');
    }

    return await response.json();
  },

  // Get or create evaluation
  getEvaluation: async (teamId, questionnaireId) => {
    const response = await fetch(
      `${API_BASE_URL}/adviser/evaluation/${teamId}/${questionnaireId}`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to get evaluation');
    }

    return await response.json();
  },

  // Save draft evaluation
  saveEvaluation: async (payload) => {
    const response = await fetch(`${API_BASE_URL}/adviser/evaluation/save`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      await throwApiError(response, 'Failed to save evaluation');
    }

    return await response.json();
  },

  // Submit evaluation
  submitEvaluation: async (evaluationId) => {
    const response = await fetch(
      `${API_BASE_URL}/adviser/evaluation/submit/${evaluationId}`,
      {
        method: 'POST',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to submit evaluation');
    }

    return await response.json();
  },

  getCompletedEvaluations: async () => {
    const response = await fetch(
      `${API_BASE_URL}/adviser/evaluations/completed`,
      {
        method: "GET",
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch completed evaluations');
    }

    return await response.json();
  },
};

// Teacher Report API
export const teacherReportAPI = {
  getQuestionnaires: async () => {
    const response = await fetch(`${API_BASE_URL}/teacher/reports/questionnaires`, {
      method: 'GET',
      headers: getHeaders(),
    });

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch questionnaires');
    }

    return await response.json();
  },

  getQuestionnaireEvaluations: async (questionnaireId) => {
    const response = await fetch(
      `${API_BASE_URL}/teacher/reports/questionnaire/${questionnaireId}/evaluations`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch evaluations');
    }

    return await response.json();
  },

  getStudentQuestionnaireEvaluations: async (questionnaireId) => {
    const response = await fetch(
      `${API_BASE_URL}/teacher/reports/questionnaire/${questionnaireId}/student-evaluations`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch student evaluations');
    }

    return await response.json();
  },

  getEvaluationDetails: async (evaluationId) => {
    const response = await fetch(
      `${API_BASE_URL}/teacher/reports/evaluation/${evaluationId}`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch evaluation details');
    }

    return await response.json();
  },

  getStudentEvaluationDetails: async (evaluationId) => {
    const response = await fetch(
      `${API_BASE_URL}/teacher/reports/student-evaluation/${evaluationId}`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch student evaluation details');
    }

    return await response.json();
  },

  getPendingEvaluations: async () => {
    const response = await fetch(
      `${API_BASE_URL}/teacher/reports/pending-evaluations`,
      {
        method: 'GET',
        headers: getHeaders(),
      }
    );

    if (!response.ok) {
      await throwApiError(response, 'Failed to fetch pending evaluations');
    }

    return await response.json();
  },
};


// User Management API (formerly Admin API)
export const userManagementAPI = {
  getUsers: async () => {
    const response = await fetch(`${API_BASE_URL}/user-management/users`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Failed to fetch users');
    }
    return await response.json();
  },

  deleteUser: async (id) => {
    const response = await fetch(`${API_BASE_URL}/user-management/users/${id}`, {
      method: 'DELETE',
      headers: getHeaders(),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Failed to delete user');
    }
    return await response.json();
  },

  uploadUserSheet: async (formData) => {
    const token = getAuthToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const response = await fetch(`${API_BASE_URL}/user-management/upload-users`, {
      method: 'POST',
      headers: headers,
      body: formData,
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Upload failed');
    }
    return await response.json();
  },

  uploadStudentSheet: async (formData) => {
    const token = getAuthToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const response = await fetch(`${API_BASE_URL}/user-management/upload-students`, {
      method: 'POST',
      headers: headers,
      body: formData,
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Upload failed');
    }
    return await response.json();
  },

  uploadAdviserSheet: async (formData) => {
    const token = getAuthToken();
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const response = await fetch(`${API_BASE_URL}/user-management/upload-advisers`, {
      method: 'POST',
      headers: headers,
      body: formData,
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Upload failed');
    }
    return await response.json();
  },

  getExportData: async (type) => {
    const response = await fetch(`${API_BASE_URL}/user-management/export?type=${encodeURIComponent(type)}`, {
      method: 'GET',
      headers: getHeaders(),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => null);
      throw new Error(err?.message || 'Failed to fetch export data');
    }
    return await response.json();
  },
};

export default {
  authAPI,
  userAPI,
  classAPI,
  teamAPI,
  evaluationAPI,
  questionnaireAPI,
  reportAPI,
  studentAPI,
  adviserAPI,
  teacherReportAPI,
  userManagementAPI
};
