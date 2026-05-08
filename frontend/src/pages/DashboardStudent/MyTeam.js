import React, { useEffect, useState } from "react";
import StudentSidebar from "../../components/Sidebar/StudentSidebar";
import { useToast } from "../../contexts/ToastContext";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/Pagination/Pagination";
import "../DashboardTeacher/Teacher.css";
import "./StudentResponsive.css";

const API_BASE_URL = "http://localhost:8080";

const MyTeam = () => {
    const [team, setTeam] = useState(null);
    const [loading, setLoading] = useState(true);
    const toast = useToast();

    const members = team?.members || [];
    const { currentPage, totalPages, paginatedData, goToPage } = usePagination(members, 10);

    useEffect(() => {
        const fetchTeam = async () => {
            try {
                const userStr = localStorage.getItem('user');
                const token = userStr ? JSON.parse(userStr).token : '';
                
                const response = await fetch(`${API_BASE_URL}/api/student/team`, {
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                });
                
                if (!response.ok) throw new Error("Failed to fetch team information");
                const data = await response.json();
                setTeam(data);
            } catch (err) {
                toast.error(err.message);
            } finally {
                setLoading(false);
            }
        };
        
        fetchTeam();
    }, [toast]);

    if (loading) return (
        <div className="teacher-container">
            <StudentSidebar />
            <div className="teacher-content">
                <h1 className="teacher-page-title">My Team</h1>
                <div className="section">Loading team information...</div>
            </div>
        </div>
    );

    return (
        <div className="teacher-container">
            <StudentSidebar />
            <div className="teacher-content">
                <h1 className="teacher-page-title">My Team</h1>
                
                {!team || team.message ? (
                    <div className="section">
                        <div className="empty-state">
                            <span style={{ fontSize: '48px' }}>👤</span>
                            <h3>No Team Assigned</h3>
                            <p>{team?.message || "You are not currently assigned to any team. Please contact your teacher if this is an error."}</p>
                        </div>
                    </div>
                ) : (
                    <div className="section">
                        <div className="teacher-hero" style={{ marginBottom: 30 }}>
                            <div className="teacher-hero-content">
                                <p className="teacher-hero-kicker">Team Profile</p>
                                <h1 className="teacher-hero-title">{team.name}</h1>
                                <p className="teacher-hero-text">{team.description || "No description provided for this team."}</p>
                            </div>
                        </div>

                        <div className="classes-header">
                            <h2>Team Members</h2>
                            <div className="status-badge" style={{ background: 'rgba(242, 201, 76, 0.1)', color: 'var(--dtm-gold)', border: '1px solid rgba(242, 201, 76, 0.3)' }}>
                                {team.members?.length || 0} Members
                            </div>
                        </div>

                        <div className="table-responsive">
                            <table className="class-table">
                                <thead>
                                <tr>
                                    <th>Name</th>
                                    <th>Email</th>
                                    <th>Position</th>
                                    <th>Role</th>
                                </tr>
                            </thead>
                            <tbody>
                                {paginatedData.map((member) => (
                                    <tr key={member.id} style={member.isMe ? { background: 'rgba(242, 201, 76, 0.05)' } : {}}>
                                        <td data-label="Name">
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', justifyContent: 'center' }}>
                                                {member.name}
                                                {member.isMe && <span className="status-badge status-active" style={{ fontSize: '10px', padding: '2px 6px' }}>YOU</span>}
                                            </div>
                                        </td>
                                        <td data-label="Email">{member.email}</td>
                                        <td data-label="Position">{member.position || "Member"}</td>
                                        <td data-label="Role">Student</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                            <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={goToPage} />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default MyTeam;
