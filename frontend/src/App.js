import './App.css';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login/Login';
import ProtectedRoute from './components/ProtectedRoute';
import { ToastProvider } from './contexts/ToastContext';

// Teacher Pages
import Teacher from './pages/DashboardTeacher/Teacher';
import Questionnaires from './pages/DashboardTeacher/Questionnaires';
import CreateQuestionnaire from './pages/DashboardTeacher/CreateQuestionnaire';
import Reports from './pages/DashboardTeacher/Reports';
import EvaluationDetail from './pages/DashboardTeacher/EvaluationDetail';
import StudentEvaluationDetail from './pages/DashboardTeacher/StudentEvaluationDetail';
import Students from './pages/DashboardTeacher/Students';
import Advisers from './pages/DashboardTeacher/Advisers';
import UserManagement from './pages/DashboardTeacher/UserManagement';

// Adviser Pages
import Adviser from './pages/DashboardAdviser/Adviser';
import Evaluations from './pages/DashboardAdviser/Evaluations';
import Completed from './pages/DashboardAdviser/Completed';
import EvaluateForm from './pages/DashboardAdviser/EvaluateForm';

// Student Pages
import StudentDashboard from './pages/DashboardStudent/StudentDashboard';
import StudentEvaluateForm from './pages/DashboardStudent/StudentEvaluateForm';
import MyTeam from './pages/DashboardStudent/MyTeam';

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
        <Route path="/register" element={<Navigate to="/login" replace />} />

        {/* Teacher */}
        <Route path="/teacher/dashboard" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Teacher />
          </ProtectedRoute>
        } />

        <Route path="/teacher/questionnaires" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <Questionnaires />
          </ProtectedRoute>
        } />

        <Route path="/teacher/questionnaires/create" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <CreateQuestionnaire />
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

        <Route path="/teacher/reports/student-evaluation/:evaluationId" element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <StudentEvaluationDetail />
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

        {/* Student */}
        <Route path="/student/dashboard" element={
          <ProtectedRoute allowedRoles={['STUDENT']}>
            <StudentDashboard />
          </ProtectedRoute>
        } />

        <Route path="/student/evaluate/:questionnaireId" element={
          <ProtectedRoute allowedRoles={['STUDENT']}>
            <StudentEvaluateForm />
          </ProtectedRoute>
        } />

        <Route path="/student/team" element={
          <ProtectedRoute allowedRoles={['STUDENT']}>
            <MyTeam />
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
