import React, { useState, useEffect } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { userManagementAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import "./Teacher.css";

const Advisers = () => {
  const toast = useToast();
  const [advisers, setAdvisers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchAdvisers();
  }, []);

  const fetchAdvisers = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await userManagementAPI.getUsers();
      // Filter only ADVISER role users
      const adviserUsers = data.filter(user => user.role === 'ADVISER');
      setAdvisers(adviserUsers);
    } catch (err) {
      setError('Failed to load advisers: ' + err.message);
      toast.error('Failed to load advisers: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Advisers</h1>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h2>All Advisers</h2>
          </div>

          {loading ? (
            <p>Loading advisers...</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                </tr>
              </thead>
              <tbody>
                {advisers.length === 0 ? (
                  <tr>
                    <td colSpan="2" style={{ textAlign: 'center' }}>No advisers found</td>
                  </tr>
                ) : (
                  advisers.map((adviser) => (
                    <tr key={adviser.id}>
                      <td>{adviser.firstName && adviser.lastName ? `${adviser.firstName} ${adviser.lastName}` : '—'}</td>
                      <td>{adviser.email}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default Advisers;
