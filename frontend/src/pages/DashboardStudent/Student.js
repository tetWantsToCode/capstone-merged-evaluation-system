import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

const Student = () => {
  const navigate = useNavigate();
  useEffect(() => {
    navigate("/student/dashboard", { replace: true });
  }, [navigate]);
  return null;
};

export default Student;
