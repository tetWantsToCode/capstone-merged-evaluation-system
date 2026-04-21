import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { userManagementAPI } from '../../services/api';
import { useToast } from '../../contexts/ToastContext';
import TeacherSidebar from '../../components/Sidebar/TeacherSidebar';
import SummaryCard from '../../components/Cards/SummaryCard';
import './Teacher.css';

function UserManagement() {
  const toast = useToast();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [showImportModal, setShowImportModal] = useState(false);
  const [importType, setImportType] = useState('STUDENT'); // 'STUDENT' or 'ADVISER'
  const [showExportModal, setShowExportModal] = useState(false);
  const [filterRole, setFilterRole] = useState('ALL');

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await userManagementAPI.getUsers();
      setUsers(data);
    } catch (err) {
      toast.error('Failed to load users: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id, email) => {
    if (!window.confirm(`Remove ${email} from the system?`)) return;
    try {
      await userManagementAPI.deleteUser(id);
      toast.success(`${email} removed`);
      fetchUsers();
    } catch (err) {
      toast.error('Failed to remove: ' + err.message);
    }
  };

  const handleImport = async (e) => {
    e.preventDefault();
    if (!uploadFile) { setUploadError('Please select a file'); return; }
    setUploading(true);
    setUploadError('');
    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      const res = importType === 'STUDENT' 
        ? await userManagementAPI.uploadStudentSheet(formData)
        : await userManagementAPI.uploadAdviserSheet(formData);
      toast.success(res.message || `${importType === 'STUDENT' ? 'Student' : 'Adviser'} import successful`);
      setShowImportModal(false);
      setUploadFile(null);
      setImportType('STUDENT');
      fetchUsers();
    } catch (err) {
      setUploadError('Import failed: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  const downloadTemplate = () => {
    const headers = importType === 'STUDENT'
      ? ['CLASS', 'TEAMCODE', 'MEMBER#', 'STUDENTID', 'LASTNAME', 'FIRSTNAME', 'EMAIL', 'ADVISOREMAIL']
      : ['LASTNAME', 'FIRSTNAME', 'EMAIL', 'ADVISOREMAIL'];
    
    const sampleData = importType === 'STUDENT'
      ? [['2B', 'T001', '1', '202301', 'Doe', 'John', 'john.doe@cit.edu', 'adviser.one@cit.edu']]
      : [['Johnson', 'Robert', 'robert.johnson@cit.edu', 'robert.johnson@cit.edu']];

    const csvContent = [
      headers.join(','),
      ...sampleData.map(row => row.join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${importType === 'STUDENT' ? 'students' : 'advisers'}_template.csv`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
    setShowExportModal(false);
    toast.success('Template downloaded successfully');
  };

  const exportData = async () => {
    const headers = importType === 'STUDENT'
      ? ['CLASS', 'TEAMCODE', 'MEMBER#', 'STUDENTID', 'LASTNAME', 'FIRSTNAME', 'EMAIL', 'ADVISOREMAIL']
      : ['LASTNAME', 'FIRSTNAME', 'EMAIL', 'ADVISOREMAIL'];

    try {
      const exportRows = await userManagementAPI.getExportData(importType);
      const rows = exportRows.map((row) => headers.map((header) => row[header] || ''));

      const csvContent = [
        headers.join(','),
        ...rows.map(row => row.map(cell => `"${cell || ''}"`).join(','))
      ].join('\n');

      const blob = new Blob([csvContent], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${importType === 'STUDENT' ? 'students' : 'advisers'}_export_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      setShowExportModal(false);
      toast.success('Data exported successfully');
    } catch (err) {
      toast.error('Export failed: ' + err.message);
    }
  };

  const filtered = filterRole === 'ALL'
    ? users
    : users.filter(u => u.role === filterRole);

  const counts = {
    total: users.length,
    teacher: users.filter(u => u.role === 'TEACHER').length,
    adviser: users.filter(u => u.role === 'ADVISER').length,
    student: users.filter(u => u.role === 'STUDENT').length,
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />

      <div className="teacher-content">
        <h1>User Management</h1>

        {/* Summary Cards */}
        <div className="summary-row">
          <SummaryCard title="Total Users" value={loading ? '-' : String(counts.total)} />
          <SummaryCard title="Teachers" value={loading ? '-' : String(counts.teacher)} />
          <SummaryCard title="Advisers" value={loading ? '-' : String(counts.adviser)} />
          <SummaryCard title="Students" value={loading ? '-' : String(counts.student)} />
        </div>

        <div className="section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2>System Users</h2>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <select
                className="filter-select"
                value={filterRole}
                onChange={(e) => setFilterRole(e.target.value)}
              >
                <option value="ALL">All Roles</option>
                <option value="TEACHER">Teachers</option>
                <option value="ADVISER">Advisers</option>
                <option value="STUDENT">Students</option>
              </select>
              <button 
                className="btn"
                onClick={() => { setImportType('STUDENT'); setShowImportModal(true); }}
                title="Import Users"
                style={{ padding: '10px 12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="7 10 12 15 17 10"></polyline>
                  <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
              </button>
              <button 
                className="btn"
                onClick={() => setShowExportModal(true)}
                title="Export Users"
                style={{ padding: '10px 12px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17 8 12 3 7 8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
              </button>
            </div>
          </div>

          {loading ? (
            <p>Loading users...</p>
          ) : filtered.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '32px', color: '#666' }}>
              <p>No users found. Upload student or adviser sheets to get started.</p>
            </div>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((u, idx) => (
                  <tr key={u.id}>
                    <td>{idx + 1}</td>
                    <td>{u.firstName && u.lastName ? `${u.firstName} ${u.lastName}` : '—'}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '13px' }}>{u.email}</td>
                    <td>
                      <span style={{
                        padding: '3px 10px',
                        borderRadius: '12px',
                        fontSize: '11px',
                        fontWeight: '700',
                        background: u.role === 'TEACHER' ? '#d4edda' : u.role === 'ADVISER' ? '#cce5ff' : '#fff3cd',
                        color: u.role === 'TEACHER' ? '#155724' : u.role === 'ADVISER' ? '#004085' : '#856404',
                      }}>
                        {u.role}
                      </span>
                    </td>
                    <td>
                      {u.email !== 'authortet@gmail.com' && (
                        <button
                          className="btn-secondary"
                          style={{ padding: '5px 12px', fontSize: '12px', color: '#dc3545', borderColor: '#dc3545' }}
                          onClick={() => handleDelete(u.id, u.email)}
                        >
                          Remove
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Import Modal */}
      {showImportModal && createPortal((
        <div className="modal-overlay" onClick={() => { setShowImportModal(false); setUploadFile(null); setUploadError(''); }}>
          <div className="modal-content" style={{ width: '95%', maxWidth: '900px' }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2 style={{ margin: 0 }}>Import Data</h2>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  className={importType === 'STUDENT' ? 'btn' : 'btn-secondary'}
                  style={{ padding: '8px 16px', fontSize: '12px' }}
                  onClick={() => { setImportType('STUDENT'); setUploadFile(null); setUploadError(''); }}
                >
                  Students
                </button>
                <button
                  className={importType === 'ADVISER' ? 'btn' : 'btn-secondary'}
                  style={{ padding: '8px 16px', fontSize: '12px' }}
                  onClick={() => { setImportType('ADVISER'); setUploadFile(null); setUploadError(''); }}
                >
                  Advisers
                </button>
              </div>
            </div>

            {uploadError && <div className="error-message">{uploadError}</div>}
            <p style={{ fontSize: '13px', color: '#555', marginBottom: '12px' }}>
              Upload an Excel (.xlsx / .xls) or CSV file. First row must be the header; columns are matched by header name and do not need to be in a fixed order:
            </p>

            {importType === 'STUDENT' ? (
              <>
                <table className="class-table" style={{ marginBottom: '16px' }}>
                  <thead>
                    <tr><th>CLASS</th><th>TEAMCODE</th><th>MEMBER#</th><th>STUDENTID</th><th>LASTNAME</th><th>FIRSTNAME</th><th>EMAIL</th><th>ADVISOREMAIL</th></tr>
                  </thead>
                  <tbody>
                    <tr><td>2B</td><td>T001</td><td>1</td><td>202301</td><td>Doe</td><td>John</td><td>john.doe@cit.edu</td><td>adviser.one@cit.edu</td></tr>
                    <tr><td>2B</td><td>T001</td><td>2</td><td>202302</td><td>Smith</td><td>Jane</td><td>jane.smith@cit.edu</td><td>adviser.one@cit.edu</td></tr>
                  </tbody>
                </table>
                <p style={{ fontSize: '12px', color: '#888', marginBottom: '16px' }}>
                  Required: <strong>CLASS, TEAMCODE, MEMBER#, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, ADVISOREMAIL</strong>
                </p>
              </>
            ) : (
              <>
                <table className="class-table" style={{ marginBottom: '16px' }}>
                  <thead>
                    <tr><th>LASTNAME</th><th>FIRSTNAME</th><th>EMAIL</th></tr>
                  </thead>
                  <tbody>
                    <tr><td>Johnson</td><td>Robert</td><td>robert.johnson@cit.edu</td></tr>
                    <tr><td>Williams</td><td>Sarah</td><td>sarah.williams@cit.edu</td></tr>
                  </tbody>
                </table>
                <p style={{ fontSize: '12px', color: '#888', marginBottom: '16px' }}>
                  Required: <strong>LASTNAME, FIRSTNAME, EMAIL</strong>
                </p>
              </>
            )}

            <form onSubmit={handleImport}>
              <div className="form-group">
                <label>Select File (.xlsx, .xls, or .csv) *</label>
                <input
                  type="file"
                  accept=".xlsx,.xls,.csv"
                  onChange={(e) => { setUploadFile(e.target.files[0]); setUploadError(''); }}
                  required
                />
                {uploadFile && (
                  <p style={{ fontSize: '12px', color: '#28a745', marginTop: '6px' }}>
                    Selected: {uploadFile.name}
                  </p>
                )}
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => { setShowImportModal(false); setUploadFile(null); setUploadError(''); }}
                  disabled={uploading}
                >
                  Cancel
                </button>
                <button type="submit" className="btn" disabled={uploading}>
                  {uploading ? 'Importing...' : 'Import & Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      ), document.body)}

      {/* Export Modal */}
      {showExportModal && createPortal((
        <div className="modal-overlay" onClick={() => setShowExportModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Export Data</h2>
            <p style={{ fontSize: '13px', color: '#555', marginBottom: '24px' }}>
              Choose what you'd like to export:
            </p>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
              <div 
                style={{
                  padding: '20px',
                  border: '1px solid rgba(138, 21, 31, 0.3)',
                  borderRadius: '10px',
                  cursor: 'pointer',
                  transition: 'all 0.3s ease',
                  backgroundColor: 'rgba(138, 21, 31, 0.1)'
                }}
                onClick={downloadTemplate}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(138, 21, 31, 0.6)';
                  e.currentTarget.style.backgroundColor = 'rgba(138, 21, 31, 0.2)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(138, 21, 31, 0.3)';
                  e.currentTarget.style.backgroundColor = 'rgba(138, 21, 31, 0.1)';
                }}
              >
                <div style={{ fontSize: '20px', marginBottom: '8px' }}>📋</div>
                <h3 style={{ margin: '0 0 8px 0', color: '#f5f0eb', fontSize: '14px', fontWeight: '600' }}>
                  Template
                </h3>
                <p style={{ margin: '0', fontSize: '12px', color: '#a09890' }}>
                  Download blank template with headers for {importType === 'STUDENT' ? 'students' : 'advisers'}
                </p>
              </div>

              <div 
                style={{
                  padding: '20px',
                  border: '1px solid rgba(138, 21, 31, 0.3)',
                  borderRadius: '10px',
                  cursor: 'pointer',
                  transition: 'all 0.3s ease',
                  backgroundColor: 'rgba(138, 21, 31, 0.1)'
                }}
                onClick={exportData}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(138, 21, 31, 0.6)';
                  e.currentTarget.style.backgroundColor = 'rgba(138, 21, 31, 0.2)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = 'rgba(138, 21, 31, 0.3)';
                  e.currentTarget.style.backgroundColor = 'rgba(138, 21, 31, 0.1)';
                }}
              >
                <div style={{ fontSize: '20px', marginBottom: '8px' }}>📊</div>
                <h3 style={{ margin: '0 0 8px 0', color: '#f5f0eb', fontSize: '14px', fontWeight: '600' }}>
                  Data
                </h3>
                <p style={{ margin: '0', fontSize: '12px', color: '#a09890' }}>
                  Export current {importType === 'STUDENT' ? 'students' : 'advisers'} data
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
              <button
                className="btn-secondary"
                onClick={() => setShowExportModal(false)}
                style={{ padding: '10px 20px' }}
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      ), document.body)}
    </div>
  );
}

export default UserManagement;
