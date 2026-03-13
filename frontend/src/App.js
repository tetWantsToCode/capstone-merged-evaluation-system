import './App.css';
import { useEffect, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login/Login';
import ProtectedRoute from './components/ProtectedRoute';
import { ToastProvider } from './contexts/ToastContext';

// Teacher Pages
import Teacher from './pages/DashboardTeacher/Teacher';
import Classes from './pages/DashboardTeacher/Classes';
import Teams from './pages/DashboardTeacher/Teams';
import Questionnaires from './pages/DashboardTeacher/Questionnaires';
import Reports from './pages/DashboardTeacher/Reports';
import EvaluationDetail from './pages/DashboardTeacher/EvaluationDetail';
import Students from './pages/DashboardTeacher/Students';
import Advisers from './pages/DashboardTeacher/Advisers';
import UserManagement from './pages/DashboardTeacher/UserManagement';

// Adviser Pages
import Adviser from './pages/DashboardAdviser/Adviser';
import Evaluations from './pages/DashboardAdviser/Evaluations';
import Completed from './pages/DashboardAdviser/Completed';
import EvaluateForm from './pages/DashboardAdviser/EvaluateForm';

// Profile
import Profile from './pages/Profile/Profile';
import GoogleCallback from './pages/Profile/GoogleCallback';

function App() {
  const [theme, setTheme] = useState(() => {
    const savedTheme = localStorage.getItem('theme');
    return savedTheme === 'light' ? 'light' : 'dark';
  });

  useEffect(() => {
    document.body.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  return (
    <ToastProvider>
      <button
        type="button"
        className="theme-toggle-btn"
        onClick={toggleTheme}
        aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        title={theme === 'dark' ? 'Light theme' : 'Dark theme'}
      >
        {theme === 'dark' ? '☀' : '☾'}
      </button>
      <Router>
        <Routes>
        {/* Public */}
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Navigate to="/login" replace />} />

        {/* Teacher */}
        <Route path="/teacher/dashboard" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Teacher />
          </ProtectedRoute>
        } />

        <Route path="/teacher/classes" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Classes />
          </ProtectedRoute>
        } />

        <Route path="/teacher/teams" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Teams />
          </ProtectedRoute>
        } />

        <Route path="/teacher/questionnaires" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Questionnaires />
          </ProtectedRoute>
        } />

        <Route path="/teacher/reports" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Reports />
          </ProtectedRoute>
        } />

        <Route path="/teacher/reports/evaluation/:evaluationId" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <EvaluationDetail />
          </ProtectedRoute>
        } />

        <Route path="/teacher/students" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Students />
          </ProtectedRoute>
        } />

        <Route path="/teacher/advisers" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Advisers />
          </ProtectedRoute>
        } />

        <Route path="/teacher/user-management" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <UserManagement />
          </ProtectedRoute>
        } />

        {/* Adviser */}
        <Route path="/adviser/dashboard" element={
          <ProtectedRoute allowedRoles={['ADVISER']}>
            <Adviser />
          </ProtectedRoute>
        } />

        <Route path="/adviser/evaluations/:teamId" element={
          <ProtectedRoute allowedRoles={['ADVISER']}>
            <Evaluations />
          </ProtectedRoute>
        } />

        <Route path="/adviser/evaluate/:teamId/:questionnaireId" element={
          <ProtectedRoute allowedRoles={['ADVISER']}>
            <EvaluateForm />
          </ProtectedRoute>
        } />

        <Route path="/adviser/completed" element={
          <ProtectedRoute allowedRoles={['ADVISER']}>
            <Completed />
          </ProtectedRoute>
        } />

        {/* Profile */}
        <Route path="/profile" element={
          <ProtectedRoute allowedRoles={['TEACHER', 'ADVISER']}>
            <Profile />
          </ProtectedRoute>
        } />

        <Route path="/profile/google-callback" element={<GoogleCallback />} />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/login" />} />
      </Routes>
      </Router>
    </ToastProvider>
  );
}

export default App;
