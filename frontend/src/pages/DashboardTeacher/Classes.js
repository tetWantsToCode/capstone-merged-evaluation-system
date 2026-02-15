import React, { useState, useEffect } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { classAPI, studentAPI, questionnaireAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";
import "./Teacher.css";

const Classes = () => {
  const toast = useToast();
  const [classes, setClasses] = useState([]);
  const [students, setStudents] = useState([]);
  const [classQuestionnaires, setClassQuestionnaires] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  // Modal states
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showManageModal, setShowManageModal] = useState(false);
  const [showAddStudentModal, setShowAddStudentModal] = useState(false);
  const [showAddQuestionnaireModal, setShowAddQuestionnaireModal] = useState(false);
  const [selectedClass, setSelectedClass] = useState(null);
  const [classStudents, setClassStudents] = useState([]);
  const [classQuestionnairesList, setClassQuestionnairesList] = useState([]);
  
  // Form states
  const [newClass, setNewClass] = useState({
    name: "",
    section: "",
    schoolYear: "",
    description: "",
    isActive: true
  });
  
  const [selectedStudentId, setSelectedStudentId] = useState("");
  const [availableStudents, setAvailableStudents] = useState([]);
  const [allQuestionnaires, setAllQuestionnaires] = useState([]);
  const [availableQuestionnaires, setAvailableQuestionnaires] = useState([]);
  const [selectedQuestionnaireId, setSelectedQuestionnaireId] = useState("");
  
  // Confirm modal states
  const [confirmModal, setConfirmModal] = useState({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: null
  });

  // Load classes on component mount
  useEffect(() => {
    loadClasses();
    loadStudents();
    loadQuestionnaires();
  }, []);

  const loadClasses = async () => {
    try {
      setLoading(true);
      // Get logged-in teacher's ID
      const user = JSON.parse(localStorage.getItem('user'));
      if (!user || !user.id) {
        setError("User not logged in");
        setLoading(false);
        return;
      }
      
      // Fetch only this teacher's classes
      const data = await classAPI.getAllClasses();
      // Filter classes for this teacher only
      const teacherClasses = data.filter(c => c.teacherId === user.id);
      setClasses(teacherClasses);
      
      // Fetch questionnaires for each class
      const classQuestionnaireMap = {};
      for (const classItem of teacherClasses) {
        const classQuestionnaires = await loadClassQuestionnaires(classItem.id);
        classQuestionnaireMap[classItem.id] = classQuestionnaires;
      }
      setClassQuestionnaires(classQuestionnaireMap);
      
      setError(null);
    } catch (err) {
      setError("Failed to load classes: " + err.message);
      toast.error("Failed to load classes: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const loadStudents = async () => {
    try {
      const data = await studentAPI.getAllStudents();
      setStudents(data);
    } catch (err) {
      toast.error("Failed to load students");
    }
  };

  const loadQuestionnaires = async () => {
    try {
      const data = await questionnaireAPI.getAllQuestionnaires();
      setAllQuestionnaires(data);
    } catch (err) {
      toast.error("Failed to load questionnaires");
    }
  };

  const loadClassQuestionnaires = async (classId) => {
    try {
      const data = await questionnaireAPI.getQuestionnairesByClass(classId);
      return data;
    } catch (err) {
      return [];
    }
  };

  const handleCreateClass = async (e) => {
    e.preventDefault();
    try {
      toast.info("Creating class...");
      
      // Get the logged-in teacher's ID from localStorage
      const user = JSON.parse(localStorage.getItem('user'));
      const classData = {
        ...newClass,
        teacherId: user?.id
      };
      
      await classAPI.createClass(classData);
      setShowCreateModal(false);
      setNewClass({
        name: "",
        section: "",
        schoolYear: "",
        description: "",
        isActive: true
      });
      loadClasses();
      toast.success("Class created successfully!");
    } catch (err) {
      toast.error("Failed to create class: " + err.message);
    }
  };

  const handleDeleteClass = (classId) => {
    setConfirmModal({
      isOpen: true,
      title: "Delete Class",
      message: "Are you sure you want to delete this class? This will remove all associated students and teams.",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await classAPI.deleteClass(classId);
          loadClasses();
          toast.success("Class deleted successfully!");
        } catch (err) {
          toast.error("Failed to delete class: " + err.message);
        }
      }
    });
  };

  const handleManageClass = (classItem) => {
    setSelectedClass(classItem);
    // Filter students belonging to this class
    const filteredStudents = students.filter(s => s.classIds && s.classIds.includes(classItem.id));
    setClassStudents(filteredStudents);
    // Load questionnaires for this class
    const classQuestionnairesData = classQuestionnaires[classItem.id] || [];
    setClassQuestionnairesList(classQuestionnairesData);
    setShowManageModal(true);
  };

  const handleAddStudent = async (e) => {
    e.preventDefault();
    if (!selectedStudentId) {
      toast.warning("Please select a student");
      return;
    }
    try {
      // Get the selected student
      const student = students.find(s => s.id === parseInt(selectedStudentId));
      if (!student) {
        toast.error("Student not found");
        return;
      }
      
      // Update the student to assign them to this class
      const currentClassIds = student.classIds || [];
      const updatedStudent = {
        ...student,
        classIds: [...currentClassIds, selectedClass.id]
      };
      await studentAPI.updateStudent(student.id, updatedStudent);
      
      setShowAddStudentModal(false);
      setSelectedStudentId("");
      
      // Reload students and update class students
      await loadStudents();
      const updatedStudents = await studentAPI.getAllStudents();
      const filteredStudents = updatedStudents.filter(s => s.classIds && s.classIds.includes(selectedClass.id));
      setClassStudents(filteredStudents);
      toast.success("Student added successfully!");
    } catch (err) {
      toast.error("Failed to add student: " + err.message);
    }
  };

  const handleRemoveStudent = (studentId) => {
    setConfirmModal({
      isOpen: true,
      title: "Remove Student",
      message: "Are you sure you want to remove this student from this class?",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          // Get the student
          const student = students.find(s => s.id === studentId);
          if (!student) {
            toast.error("Student not found");
            return;
          }
          
          // Remove this class from the student's classIds array
          const updatedClassIds = (student.classIds || []).filter(id => id !== selectedClass.id);
          const updatedStudent = {
            ...student,
            classIds: updatedClassIds
          };
          
          await studentAPI.updateStudent(studentId, updatedStudent);
          
          // Update local state
          const updatedStudents = classStudents.filter(s => s.id !== studentId);
          setClassStudents(updatedStudents);
          await loadStudents();
          toast.success("Student removed from class successfully!");
        } catch (err) {
          toast.error("Failed to remove student: " + err.message);
        }
      }
    });
  };

  const openAddStudentModal = () => {
    // Filter students that are not already in this class
    const availableForClass = students.filter(s => !s.classIds || !s.classIds.includes(selectedClass.id));
    setAvailableStudents(availableForClass);
    setSelectedStudentId("");
    setShowAddStudentModal(true);
  };

  const openAddQuestionnaireModal = () => {
    // Filter questionnaires that are not already assigned to this class
    const assignedQuestionnaireIds = classQuestionnairesList.map(q => q.id);
    const availableForClass = allQuestionnaires.filter(q => !assignedQuestionnaireIds.includes(q.id));
    setAvailableQuestionnaires(availableForClass);
    setSelectedQuestionnaireId("");
    setShowAddQuestionnaireModal(true);
  };

  const handleAddQuestionnaire = async (e) => {
    e.preventDefault();
    if (!selectedQuestionnaireId) {
      toast.warning("Please select a questionnaire");
      return;
    }
    try {
      await questionnaireAPI.assignToClasses(parseInt(selectedQuestionnaireId), [selectedClass.id]);
      
      setShowAddQuestionnaireModal(false);
      setSelectedQuestionnaireId("");
      
      // Reload class questionnaires
      const updatedQuestionnaires = await loadClassQuestionnaires(selectedClass.id);
      setClassQuestionnairesList(updatedQuestionnaires);
      
      // Update the class questionnaires map
      setClassQuestionnaires({
        ...classQuestionnaires,
        [selectedClass.id]: updatedQuestionnaires
      });
      
      toast.success("Questionnaire added successfully!");
    } catch (err) {
      toast.error("Failed to add questionnaire: " + err.message);
    }
  };

  const handleRemoveQuestionnaire = (questionnaireId) => {
    setConfirmModal({
      isOpen: true,
      title: "Remove Questionnaire",
      message: "Are you sure you want to remove this questionnaire from this class?",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await questionnaireAPI.unassignFromClasses(questionnaireId, [selectedClass.id]);
          
          // Update local state
          const updatedQuestionnaires = classQuestionnairesList.filter(q => q.id !== questionnaireId);
          setClassQuestionnairesList(updatedQuestionnaires);
          
          // Update the class questionnaires map
          setClassQuestionnaires({
            ...classQuestionnaires,
            [selectedClass.id]: updatedQuestionnaires
          });
          
          toast.success("Questionnaire removed from class successfully!");
        } catch (err) {
          toast.error("Failed to remove questionnaire: " + err.message);
        }
      }
    });
  };

  if (loading) {
    return (
      <div className="teacher-container">
        <TeacherSidebar />
        <div className="teacher-content">
          <h1>Classes</h1>
          <p>Loading classes...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Classes</h1>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h2>Your Classes</h2>
            <button className="btn" onClick={() => setShowCreateModal(true)}>Create New Class</button>
          </div>
          {classes.length === 0 ? (
            <p>No classes found. Create your first class to get started.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Class Name</th>
                  <th>Section</th>
                  <th>School Year</th>
                  <th>Students</th>
                  <th>Teams</th>
                  <th>Questionnaires</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {classes.map((classItem) => {
                  const attachedQuestionnaires = classQuestionnaires[classItem.id] || [];
                  return (
                  <tr key={classItem.id}>
                    <td>{classItem.name}</td>
                    <td>{classItem.section || "N/A"}</td>
                    <td>{classItem.schoolYear}</td>
                    <td>{students.filter(s => s.classIds && s.classIds.includes(classItem.id)).length} Students</td>
                    <td>{classItem.teamIds?.length || 0} Teams</td>
                    <td>
                      {attachedQuestionnaires.length === 0 ? (
                        <span style={{ color: '#6c757d', fontStyle: 'italic' }}>
                          0 Questionnaires
                        </span>
                      ) : (
                        <span style={{ color: '#28a745', fontWeight: 'bold' }}>
                          {attachedQuestionnaires.length} {attachedQuestionnaires.length === 1 ? 'Questionnaire' : 'Questionnaires'}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={classItem.isActive ? "status-active" : "status-inactive"}>
                        {classItem.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <button 
                        className="btn btn-sm" 
                        onClick={() => handleManageClass(classItem)}
                      > Manage
                      </button>
                      <button 
                        className="btn btn-sm btn-danger" 
                        onClick={() => handleDeleteClass(classItem.id)}
                        style={{ marginLeft: "5px" }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Create Class Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Create New Class</h2>
            <form onSubmit={handleCreateClass}>
              <div className="form-group">
                <label>Class Name *</label>
                <input
                  type="text"
                  value={newClass.name}
                  onChange={(e) => setNewClass({ ...newClass, name: e.target.value })}
                  placeholder="e.g., IT 3310"
                  required
                />
              </div>
              <div className="form-group">
                <label>Section</label>
                <input
                  type="text"
                  value={newClass.section}
                  onChange={(e) => setNewClass({ ...newClass, section: e.target.value })}
                  placeholder="e.g., A"
                />
              </div>
              <div className="form-group">
                <label>School Year *</label>
                <input
                  type="text"
                  value={newClass.schoolYear}
                  onChange={(e) => setNewClass({ ...newClass, schoolYear: e.target.value })}
                  placeholder="e.g., 2024-2025"
                  required
                />
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={newClass.description}
                  onChange={(e) => setNewClass({ ...newClass, description: e.target.value })}
                  placeholder="Class description..."
                  rows="3"
                />
              </div>
              <div className="form-group">
                <label>
                  <input
                    type="checkbox"
                    checked={newClass.isActive}
                    onChange={(e) => setNewClass({ ...newClass, isActive: e.target.checked })}
                  />
                  {" "}Active
                </label>
              </div>
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Create Class
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Manage Class Modal */}
      {showManageModal && selectedClass && (
        <div className="modal-overlay" onClick={() => setShowManageModal(false)}>
          <div className="modal-content modal-large" onClick={(e) => e.stopPropagation()}>
            <h2>Manage Class: {selectedClass.name} {selectedClass.section}</h2>
            <p><strong>School Year:</strong> {selectedClass.schoolYear}</p>
            {selectedClass.description && <p><strong>Description:</strong> {selectedClass.description}</p>}
            
            <div style={{ marginTop: "20px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3>Students ({classStudents.length})</h3>
                <button className="btn btn-primary btn-sm" onClick={openAddStudentModal}>
                  + Add Student
                </button>
              </div>
              
              {classStudents.length === 0 ? (
                <p>No students in this class yet.</p>
              ) : (
                <table className="class-table" style={{ marginTop: "10px" }}>
                  <thead>
                    <tr>
                      <th>Student ID</th>
                      <th>Name</th>
                      <th>Email</th>
                      <th>Phone</th>
                      <th>Team</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {classStudents.map((student) => (
                      <tr key={student.id}>
                        <td>{student.studentId}</td>
                        <td>{student.firstName} {student.lastName}</td>
                        <td>{student.email || "N/A"}</td>
                        <td>{student.phoneNumber || "N/A"}</td>
                        <td>{student.team?.name || "No Team"}</td>
                        <td>
                          <button 
                            className="btn btn-sm btn-danger"
                            onClick={() => handleRemoveStudent(student.id)}
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div style={{ marginTop: "30px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3>Questionnaires ({classQuestionnairesList.length})</h3>
                <button className="btn btn-primary btn-sm" onClick={openAddQuestionnaireModal}>
                  + Add Questionnaire
                </button>
              </div>
              
              {classQuestionnairesList.length === 0 ? (
                <p>No questionnaires assigned to this class yet.</p>
              ) : (
                <table className="class-table" style={{ marginTop: "10px" }}>
                  <thead>
                    <tr>
                      <th>Title</th>
                      <th>Description</th>
                      <th>Questions</th>
                      <th>Created Date</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {classQuestionnairesList.map((questionnaire) => (
                      <tr key={questionnaire.id}>
                        <td>{questionnaire.title}</td>
                        <td>{questionnaire.description || "N/A"}</td>
                        <td>{questionnaire.questionCount}</td>
                        <td>{new Date(questionnaire.createdAt).toLocaleDateString()}</td>
                        <td>
                          <button 
                            className="btn btn-sm btn-danger"
                            onClick={() => handleRemoveQuestionnaire(questionnaire.id)}
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className="modal-actions" style={{ marginTop: "20px" }}>
              <button className="btn btn-secondary" onClick={() => setShowManageModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Student Modal */}
      {showAddStudentModal && (
        <div className="modal-overlay" onClick={() => setShowAddStudentModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Add New Student</h2>
            <form onSubmit={handleAddStudent}>
              <div className="form-group">
                <label>Select Student *</label>
                <select
                  value={selectedStudentId}
                  onChange={(e) => setSelectedStudentId(e.target.value)}
                  required
                >
                  <option value="">-- Select a student --</option>
                  {availableStudents.map((student) => (
                    <option key={student.id} value={student.id}>
                      {student.studentId} - {student.firstName} {student.lastName}
                    </option>
                  ))}
                </select>
              </div>
              {availableStudents.length === 0 && (
                <p style={{ color: '#666', fontSize: '13px' }}>
                  No unassigned students available. All students are already assigned to classes.
                </p>
              )}
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowAddStudentModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!selectedStudentId}>
                  Add Student
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Questionnaire Modal */}
      {showAddQuestionnaireModal && (
        <div className="modal-overlay" onClick={() => setShowAddQuestionnaireModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Add Questionnaire to Class</h2>
            <form onSubmit={handleAddQuestionnaire}>
              <div className="form-group">
                <label>Select Questionnaire *</label>
                <select
                  value={selectedQuestionnaireId}
                  onChange={(e) => setSelectedQuestionnaireId(e.target.value)}
                  required
                >
                  <option value="">-- Select a questionnaire --</option>
                  {availableQuestionnaires.map((questionnaire) => (
                    <option key={questionnaire.id} value={questionnaire.id}>
                      {questionnaire.title} - {questionnaire.questionCount} questions
                    </option>
                  ))}
                </select>
              </div>
              {availableQuestionnaires.length === 0 && (
                <p style={{ color: '#666', fontSize: '13px' }}>
                  No available questionnaires. All questionnaires are already assigned to this class or no questionnaires exist.
                </p>
              )}
              <div className="modal-actions">
                <button type="button" className="btn btn-secondary" onClick={() => setShowAddQuestionnaireModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!selectedQuestionnaireId}>
                  Add Questionnaire
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
  );
};

export default Classes;

