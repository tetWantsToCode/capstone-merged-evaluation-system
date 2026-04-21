import React, { useState, useEffect } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { studentAPI, classAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";
import "./Teacher.css";

const StudentManagement = () => {
  const toast = useToast();
  const [students, setStudents] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [confirmModal, setConfirmModal] = useState({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: null
  });
  const [showModal, setShowModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importLoading, setImportLoading] = useState(false);
  const [importError, setImportError] = useState('');
  const [editMode, setEditMode] = useState(false);
  const [currentStudent, setCurrentStudent] = useState({
    id: null,
    studentId: '',
    firstName: '',
    lastName: '',
    email: '',
    classIds: []
  });

  useEffect(() => {
    fetchStudents();
    fetchClasses();
  }, []);

  const fetchStudents = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await studentAPI.getAllStudents();
      setStudents(data);
    } catch (err) {
      setError('Failed to load students: ' + err.message);
      toast.error('Failed to load students: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchClasses = async () => {
    try {
      const data = await classAPI.getAllClasses();
      setClasses(data);
    } catch (err) {
      toast.error('Failed to load classes');
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    if (name === 'classIds') {
      // Handle multi-select
      const selectedOptions = Array.from(e.target.selectedOptions, option => parseInt(option.value));
      setCurrentStudent({
        ...currentStudent,
        classIds: selectedOptions
      });
    } else {
      setCurrentStudent({
        ...currentStudent,
        [name]: value
      });
    }
  };

  const handleAddNew = () => {
    setEditMode(false);
    setCurrentStudent({
      id: null,
      studentId: '',
      firstName: '',
      lastName: '',
      email: '',
      phoneNumber: '',
      classIds: []
    });
    setShowModal(true);
  };

  const handleEdit = (student) => {
    setEditMode(true);
    setCurrentStudent(student);
    setShowModal(true);
  };

  const handleDelete = (id) => {
    setConfirmModal({
      isOpen: true,
      title: "Delete Student",
      message: "Are you sure you want to delete this student? This action cannot be undone.",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await studentAPI.deleteStudent(id);
          await fetchStudents();
          toast.success('Student deleted successfully');
        } catch (err) {
          toast.error('Failed to delete student: ' + err.message);
        }
      }
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      if (editMode) {
        await studentAPI.updateStudent(currentStudent.id, currentStudent);
        toast.success('Student updated successfully');
      } else {
        await studentAPI.createStudent(currentStudent);
        toast.success('Student added successfully');
      }
      setShowModal(false);
      await fetchStudents();
    } catch (err) {
      setError('Failed to save student: ' + err.message);
      toast.error('Failed to save student: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowModal(false);
    setError('');
  };

  const handleImportFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setImportFile(file);
      setImportError('');
    }
  };

  const handleImportSubmit = async (e) => {
    e.preventDefault();
    if (!importFile) {
      setImportError('Please select a file');
      return;
    }

    setImportLoading(true);
    setImportError('');

    try {
      const formData = new FormData();
      formData.append('file', importFile);
      
      const response = await studentAPI.importStudents(formData);
      toast.success(response.message || `Successfully imported students`);
      setShowImportModal(false);
      setImportFile(null);
      await fetchStudents();
    } catch (err) {
      setImportError('Failed to import students: ' + err.message);
      toast.error('Failed to import students: ' + err.message);
    } finally {
      setImportLoading(false);
    }
  };

  const handleImportCancel = () => {
    setShowImportModal(false);
    setImportFile(null);
    setImportError('');
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Students</h1>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h2>All Students</h2>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button className="btn" onClick={handleAddNew}>Add New Student</button>
              <button className="btn" onClick={() => setShowImportModal(true)}>Import from Excel</button>
            </div>
          </div>

          {loading ? (
            <p>Loading students...</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Student ID</th>
                  <th>First Name</th>
                  <th>Last Name</th>
                  <th>Class</th>
                  <th>Email</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {students.length === 0 ? (
                  <tr>
                    <td colSpan="6" style={{ textAlign: 'center' }}>No students found</td>
                  </tr>
                ) : (
                  students.map((student) => (
                    <tr key={student.id}>
                      <td>{student.studentId}</td>
                      <td>{student.firstName}</td>
                      <td>{student.lastName}</td>
                      <td>
                        {student.classIds && student.classIds.length > 0 
                          ? student.classIds.map(classId => {
                              const cls = classes.find(c => c.id === classId);
                              return cls ? cls.name : '';
                            }).filter(n => n).join(', ')
                          : 'N/A'
                        }
                      </td>
                      <td>{student.email || 'N/A'}</td>
                      <td>
                        <div className="action-buttons">
                        <button className="btn btn-sm" onClick={() => handleEdit(student)}>
                          Edit
                        </button>
                        <button className="btn-secondary btn-sm" onClick={() => handleDelete(student.id)}>
                          Delete
                        </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>

        {/* Modal for Add/Edit */}
        {showModal && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h2>{editMode ? 'Edit Student' : 'Add New Student'}</h2>
              {error && <div className="error-message">{error}</div>}
              <form onSubmit={handleSubmit}>
                <div className="form-group">
                  <label>Classes * (Hold Ctrl/Cmd to select multiple)</label>
                  <select
                    name="classIds"
                    value={currentStudent.classIds || []}
                    onChange={handleInputChange}
                    multiple
                    required
                    style={{ minHeight: '100px' }}
                  >
                    {classes.map((cls) => (
                      <option key={cls.id} value={cls.id}>
                        {cls.name} {cls.section ? `- ${cls.section}` : ''} ({cls.schoolYear})
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label>Student ID *</label>
                  <input
                    type="text"
                    name="studentId"
                    value={currentStudent.studentId}
                    onChange={handleInputChange}
                    required
                    placeholder="e.g., 2021-12345"
                  />
                </div>
                <div className="form-group">
                  <label>First Name *</label>
                  <input
                    type="text"
                    name="firstName"
                    value={currentStudent.firstName}
                    onChange={handleInputChange}
                    required
                    placeholder="Enter first name"
                  />
                </div>
                <div className="form-group">
                  <label>Last Name *</label>
                  <input
                    type="text"
                    name="lastName"
                    value={currentStudent.lastName}
                    onChange={handleInputChange}
                    required
                    placeholder="Enter last name"
                  />
                </div>
                <div className="form-group">
                  <label>Email</label>
                  <input
                    type="email"
                    name="email"
                    value={currentStudent.email}
                    onChange={handleInputChange}
                    placeholder="student@example.com"
                  />
                </div>
                <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
                  <button type="submit" className="btn">
                    {editMode ? 'Update' : 'Add'} Student
                  </button>
                  <button type="button" className="btn-secondary" onClick={handleCancel}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Import Modal */}
        {showImportModal && (
          <div className="modal-overlay">
            <div className="modal-content">
              <h2>Import Students from Excel</h2>
              {importError && <div className="error-message">{importError}</div>}
              <form onSubmit={handleImportSubmit}>
                <div className="form-group">
                  <label>Select File (.xlsx, .xls, or .csv) *</label>
                  <p style={{ fontSize: '0.9em', color: '#666', marginBottom: '10px' }}>
                    File should contain columns: Student ID, First Name, Last Name, Email (optional), Phone Number (optional)
                  </p>
                  <input
                    type="file"
                    accept=".xlsx,.xls,.csv"
                    onChange={handleImportFileChange}
                    required
                  />
                  {importFile && (
                    <p style={{ fontSize: '0.9em', color: '#4CAF50', marginTop: '10px' }}>
                      Selected: {importFile.name}
                    </p>
                  )}
                </div>
                <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
                  <button type="submit" className="btn" disabled={importLoading}>
                    {importLoading ? 'Importing...' : 'Import Students'}
                  </button>
                  <button type="button" className="btn-secondary" onClick={handleImportCancel} disabled={importLoading}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Confirm Modal */}
        <ConfirmModal
          isOpen={confirmModal.isOpen}
          title={confirmModal.title}
          message={confirmModal.message}
          onConfirm={confirmModal.onConfirm}
          onCancel={() => setConfirmModal({ ...confirmModal, isOpen: false })}
          isDanger={true}
        />
      </div>
    </div>
  );
};

export default StudentManagement;