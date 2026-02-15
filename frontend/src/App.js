import './App.css';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login/Login';
import Register from './pages/Register/Register';
import ProtectedRoute from './components/ProtectedRoute';
import { ToastProvider } from './contexts/ToastContext';

// Teacher Pages
import Teacher from './pages/DashboardTeacher/Teacher';
import Classes from './pages/DashboardTeacher/Classes';
import Teams from './pages/DashboardTeacher/Teams';
import Questionnaires from './pages/DashboardTeacher/Questionnaires';
import Reports from './pages/DashboardTeacher/Reports';
import EvaluationDetail from './pages/DashboardTeacher/EvaluationDetail';
import Student from './pages/DashboardTeacher/Student';

// Adviser Pages
import Adviser from './pages/DashboardAdviser/Adviser';
import Evaluations from './pages/DashboardAdviser/Evaluations';
import Completed from './pages/DashboardAdviser/Completed';
import EvaluateForm from './pages/DashboardAdviser/EvaluateForm';

// Profile
import Profile from './pages/Profile/Profile';
import GoogleCallback from './pages/Profile/GoogleCallback';

function App() {
  return (
    <ToastProvider>
      <Router>
        <Routes>
        {/* Public */}
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

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
            <Student />
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
