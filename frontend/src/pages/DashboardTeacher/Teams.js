import React, { useState, useEffect } from "react";
import TeacherSidebar from "../../components/Sidebar/TeacherSidebar";
import { teamAPI, classAPI, studentAPI, authAPI } from "../../services/api";
import { useToast } from "../../contexts/ToastContext";
import ConfirmModal from "../../components/ConfirmModal/ConfirmModal";
import "./Teacher.css";

const Teams = () => {
  const toast = useToast();
  const [teams, setTeams] = useState([]);
  const [classes, setClasses] = useState([]);
  const [students, setStudents] = useState([]);
  const [advisers, setAdvisers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [confirmModal, setConfirmModal] = useState({
    isOpen: false,
    title: "",
    message: "",
    onConfirm: null
  });
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showManageModal, setShowManageModal] = useState(false);
  const [selectedTeam, setSelectedTeam] = useState(null);
  
  const [newTeam, setNewTeam] = useState({
    name: "",
    description: "",
    classId: "",
    isActive: true
  });

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        const [teacherClasses] = await Promise.all([
          loadClasses(),
          loadStudents(),
          loadAdvisers(),
        ]);

        if (teacherClasses && teacherClasses.length > 0) {
          await loadTeams(teacherClasses);
        } else {
          setTeams([]);
        }
      } finally {
        setLoading(false);
      }
    };

    loadInitialData();
  }, []);

  const loadTeams = async (classesSource = classes) => {
    try {
      const data = await teamAPI.getAllTeams();
      // Filter teams to only show those from this teacher's classes
      const classIds = classesSource.map(c => c.id);
      const teacherTeams = data.filter(t => classIds.includes(t.classId));
      setTeams(teacherTeams);
      setError(null);
    } catch (err) {
      setError("Failed to load teams: " + err.message);
      toast.error("Failed to load teams: " + err.message);
    }
  };

  const loadClasses = async () => {
    try {
      // Get logged-in teacher's ID
      const user = JSON.parse(localStorage.getItem('user'));
      if (!user || !user.id) {
        setError("User not logged in");
        return [];
      }
      
      // Fetch only this teacher's classes
      const data = await classAPI.getAllClasses();
      const teacherClasses = data.filter(c => c.teacherId === user.id);
      setClasses(teacherClasses);
      setError(null);
      return teacherClasses;
    } catch (err) {
      toast.error("Failed to load classes");
      return [];
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

  const loadAdvisers = async () => {
    try {
      const data = await authAPI.getAllUsers();
      // Filter only advisers
      const adviserList = data.filter(user => user.role === 'ADVISER');
      setAdvisers(adviserList);
    } catch (err) {
      toast.error("Failed to load advisers");
    }
  };

  const handleCreateTeam = async (e) => {
    e.preventDefault();
    if (!newTeam.classId) {
      toast.warning("Please select a class");
      return;
    }
    try {
      const teamData = {
        name: newTeam.name,
        description: newTeam.description,
        classId: parseInt(newTeam.classId),
        memberIds: [],
        adviserIds: [],
        isActive: newTeam.isActive
      };
      
      toast.info("Creating team...");
      await teamAPI.createTeam(teamData);
      setShowCreateModal(false);
      setNewTeam({
        name: "",
        description: "",
        classId: "",
        isActive: true
      });
      loadTeams();
      toast.success("Team created successfully!");
    } catch (err) {
      toast.error("Failed to create team: " + err.message);
    }
  };

  const handleDeleteTeam = (teamId) => {
    setConfirmModal({
      isOpen: true,
      title: "Delete Team",
      message: "Are you sure you want to delete this team?",
      onConfirm: async () => {
        setConfirmModal({ ...confirmModal, isOpen: false });
        try {
          await teamAPI.deleteTeam(teamId);
          loadTeams();
          toast.success("Team deleted successfully!");
        } catch (err) {
          toast.error("Failed to delete team: " + err.message);
        }
      }
    });
  };

  const handleManageTeam = (team) => {
    setSelectedTeam({
      ...team,
      memberIds: team.memberIds || [],
      adviserIds: team.adviserIds || []
    });
    setShowManageModal(true);
  };

  const handleAddMember = (studentId) => {
    if (!selectedTeam.memberIds.includes(parseInt(studentId))) {
      setSelectedTeam({
        ...selectedTeam,
        memberIds: [...selectedTeam.memberIds, parseInt(studentId)]
      });
    }
  };

  const handleRemoveMember = (studentId) => {
    setSelectedTeam({
      ...selectedTeam,
      memberIds: selectedTeam.memberIds.filter(id => id !== studentId)
    });
  };

  const handleAddAdviser = (adviserId) => {
    if (!selectedTeam.adviserIds.includes(parseInt(adviserId))) {
      setSelectedTeam({
        ...selectedTeam,
        adviserIds: [...selectedTeam.adviserIds, parseInt(adviserId)]
      });
    }
  };

  const handleRemoveAdviser = (adviserId) => {
    setSelectedTeam({
      ...selectedTeam,
      adviserIds: selectedTeam.adviserIds.filter(id => id !== adviserId)
    });
  };

  const handleSaveTeam = async () => {
    try {
      toast.info("Saving team changes...");
      await teamAPI.updateTeam(selectedTeam.id, {
        name: selectedTeam.name,
        description: selectedTeam.description,
        memberIds: selectedTeam.memberIds,
        adviserIds: selectedTeam.adviserIds,
        isActive: selectedTeam.isActive
      });
      setShowManageModal(false);
      loadTeams();
      toast.success("Team updated successfully!");
    } catch (err) {
      toast.error("Failed to update team: " + err.message);
    }
  };

  const getClassName = (classId) => {
    const cls = classes.find(c => c.id === classId);
    return cls ? `${cls.name} ${cls.section ? `- ${cls.section}` : ''}` : 'Unknown';
  };

  const getStudentName = (studentId) => {
    const student = students.find(s => s.id === studentId);
    return student ? `${student.firstName} ${student.lastName}` : 'Unknown';
  };

  const getAdviserName = (adviserId) => {
    const adviser = advisers.find(a => a.id === adviserId);
    return adviser ? `${adviser.firstName} ${adviser.lastName}` : 'Unknown';
  };

  const getAvailableStudents = () => {
    if (!selectedTeam) return [];
    // Get students from the same class who are not already in the team
    return students.filter(s => 
      s.classIds && 
      s.classIds.includes(selectedTeam.classId) && 
      !selectedTeam.memberIds.includes(s.id)
    );
  };

  const getAvailableAdvisers = () => {
    if (!selectedTeam) return [];
    return advisers.filter(a => !selectedTeam.adviserIds.includes(a.id));
  };

  return (
    <div className="teacher-container">
      <TeacherSidebar />
      <div className="teacher-content">
        <h1>Teams</h1>

        {error && <div className="error-message">{error}</div>}

        <div className="section">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
            <h2>Your Teams</h2>
            <button className="btn" onClick={() => setShowCreateModal(true)}>Create New Team</button>
          </div>
          {loading ? (
            <p>Loading teams...</p>
          ) : teams.length === 0 ? (
            <p>No teams found. Create your first team to get started.</p>
          ) : (
            <table className="class-table">
              <thead>
                <tr>
                  <th>Team Name</th>
                  <th>Class</th>
                  <th>Members</th>
                  <th>Advisers</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {teams.map((team) => (
                  <tr key={team.id}>
                    <td>{team.name}</td>
                    <td>{getClassName(team.classId)}</td>
                    <td>{team.memberIds?.length || 0} Members</td>
                    <td>{team.adviserIds?.length || 0} Advisers</td>
                    <td>
                      <span className={`status-badge ${team.isActive ? "status-active" : "status-inactive"}`}>
                        {team.isActive ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <div className="action-buttons">
                      <button 
                        className="btn btn-sm" 
                        onClick={() => handleManageTeam(team)}
                      >
                        Manage
                      </button>
                      <button 
                        className="btn btn-sm btn-danger" 
                        onClick={() => handleDeleteTeam(team.id)}
                      >
                        Delete
                      </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Create Team Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Create New Team</h2>
            <form onSubmit={handleCreateTeam}>
              <div className="form-group">
                <label>Team Name *</label>
                <input
                  type="text"
                  value={newTeam.name}
                  onChange={(e) => setNewTeam({ ...newTeam, name: e.target.value })}
                  placeholder="e.g., Alpha Team"
                  required
                />
              </div>
              <div className="form-group">
                <label>Class * (Cannot be changed later)</label>
                <select
                  value={newTeam.classId}
                  onChange={(e) => setNewTeam({ ...newTeam, classId: e.target.value })}
                  required
                >
                  <option value="">Select a class</option>
                  {classes.map((cls) => (
                    <option key={cls.id} value={cls.id}>
                      {cls.name} {cls.section ? `- ${cls.section}` : ''} ({cls.schoolYear})
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Description</label>
                <textarea
                  value={newTeam.description}
                  onChange={(e) => setNewTeam({ ...newTeam, description: e.target.value })}
                  placeholder="Brief description of the team"
                  rows="3"
                />
              </div>
              <div className="modal-actions">
                <button type="submit" className="btn btn-primary">Create Team</button>
                <button 
                  type="button" 
                  className="btn btn-secondary" 
                  onClick={() => setShowCreateModal(false)}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Manage Team Modal */}
      {showManageModal && selectedTeam && (
        <div className="modal-overlay" onClick={() => setShowManageModal(false)}>
          <div className="modal-content modal-large" onClick={(e) => e.stopPropagation()}>
            <h2>Manage Team: {selectedTeam.name}</h2>
            
            <div className="form-group">
              <label>Class (Cannot be changed)</label>
              <input
                type="text"
                value={getClassName(selectedTeam.classId)}
                disabled
                style={{ backgroundColor: '#f0f0f0' }}
              />
            </div>

            {/* Members Section */}
            <div className="team-section">
              <h3>Members ({selectedTeam.memberIds.length})</h3>
              {selectedTeam.memberIds.length === 0 ? (
                <p>No members yet.</p>
              ) : (
                <ul className="member-list">
                  {selectedTeam.memberIds.map(memberId => (
                    <li key={memberId}>
                      {getStudentName(memberId)}
                      <button 
                        className="btn btn-sm btn-danger"
                        onClick={() => handleRemoveMember(memberId)}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              
              <div className="add-member-section">
                <h4>Add Member</h4>
                <select
                  onChange={(e) => {
                    if (e.target.value) {
                      handleAddMember(e.target.value);
                      e.target.value = '';
                    }
                  }}
                  defaultValue=""
                >
                  <option value="">Select a student</option>
                  {getAvailableStudents().map(student => (
                    <option key={student.id} value={student.id}>
                      {student.firstName} {student.lastName} ({student.studentId})
                    </option>
                  ))}
                </select>
                {getAvailableStudents().length === 0 && (
                  <p className="info-text">All students from this class are already in the team.</p>
                )}
              </div>
            </div>

            {/* Advisers Section */}
            <div className="team-section">
              <h3>Advisers ({selectedTeam.adviserIds.length})</h3>
              {selectedTeam.adviserIds.length === 0 ? (
                <p>No advisers assigned yet.</p>
              ) : (
                <ul className="member-list">
                  {selectedTeam.adviserIds.map(adviserId => (
                    <li key={adviserId}>
                      {getAdviserName(adviserId)}
                      <button 
                        className="btn btn-sm btn-danger"
                        onClick={() => handleRemoveAdviser(adviserId)}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              
              <div className="add-member-section">
                <h4>Add Adviser</h4>
                <select
                  onChange={(e) => {
                    if (e.target.value) {
                      handleAddAdviser(e.target.value);
                      e.target.value = '';
                    }
                  }}
                  defaultValue=""
                >
                  <option value="">Select an adviser</option>
                  {getAvailableAdvisers().map(adviser => (
                    <option key={adviser.id} value={adviser.id}>
                      {adviser.firstName} {adviser.lastName}
                    </option>
                  ))}
                </select>
                {getAvailableAdvisers().length === 0 && (
                  <p className="info-text">All advisers are already assigned to this team.</p>
                )}
              </div>
            </div>

            <div className="modal-actions">
              <button className="btn btn-primary" onClick={handleSaveTeam}>
                Save Changes
              </button>
              <button 
                className="btn btn-secondary" 
                onClick={() => setShowManageModal(false)}
              >
                Cancel
              </button>
            </div>
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

export default Teams;

