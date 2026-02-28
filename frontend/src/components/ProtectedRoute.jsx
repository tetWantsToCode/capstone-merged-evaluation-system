import { Navigate, useLocation } from "react-router-dom";
import { useAppStore } from "@/store/useAppStore";

/**
 * Protects routes that require authentication (SRS: Gmail verification grants access).
 * Redirects to /login when not authenticated.
 */
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = useAppStore((s) => s.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
};

export default ProtectedRoute;
