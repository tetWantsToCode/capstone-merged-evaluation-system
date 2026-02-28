import { create } from "zustand";

const MOCK_STUDENTS = [
  { id: "s1", studentName: "Maria Santos", gmail: "maria.santos@gmail.com", groupNumber: 1 },
  { id: "s2", studentName: "Juan Dela Cruz", gmail: "juan.delacruz@gmail.com", groupNumber: 1 },
  { id: "s3", studentName: "Ana Reyes", gmail: "ana.reyes@gmail.com", groupNumber: 1 },
  { id: "s4", studentName: "Carlos Bautista", gmail: "carlos.bautista@gmail.com", groupNumber: 1 },
  { id: "s5", studentName: "Isabel Garcia", gmail: "isabel.garcia@gmail.com", groupNumber: 2 },
];

const MOCK_ACTIVITIES = [
  {
    id: "a1",
    title: "Capstone Project Sprint 1 Evaluation",
    deadline: "2026-03-15T23:59:00",
    isActive: true,
    rubricCriteria: [
      { id: "c1", name: "Attendance", description: "Member has a complete attendance in all meetings" },
      { id: "c2", name: "Team Cooperation", description: "Member is actively participating during group discussions" },
      { id: "c3", name: "Respect", description: "Member treats everyone with respect and fairness" },
      { id: "c4", name: "Knowledge", description: "Applies relevant knowledge and concepts to contribute to the project" },
    ],
    createdBy: "teacher1",
    createdAt: "2026-02-20T10:00:00",
  },
];

export const useAppStore = create((set, get) => ({
  currentUser: null,
  isAuthenticated: false,
  login: (role) => {
    const user =
      role === "teacher"
        ? { id: "teacher1", name: "Dr. Rodriguez", email: "rodriguez@cit.edu", role: "teacher" }
        : { id: "s1", name: "Maria Santos", email: "maria.santos@gmail.com", role: "student" };
    set({ currentUser: user, isAuthenticated: true });
  },
  logout: () => set({ currentUser: null, isAuthenticated: false }),

  students: MOCK_STUDENTS,
  setStudents: (students) => set({ students }),
  addStudents: (students) => set((s) => ({ students: [...s.students, ...students] })),

  activities: MOCK_ACTIVITIES,
  addActivity: (activity) => set((s) => ({ activities: [...s.activities, activity] })),

  submissions: [],
  addSubmission: (submission) => set((s) => ({ submissions: [...s.submissions, submission] })),
  hasSubmitted: (activityId, evaluatorId) =>
    get().submissions.some((s) => s.activityId === activityId && s.evaluatorId === evaluatorId),

  summaries: [
    {
      studentId: "s1",
      studentName: "Maria Santos",
      strengths:
        "Consistently demonstrates strong leadership skills and actively contributes to group discussions. Shows excellent time management and reliability.",
      weaknesses: "Could improve in delegating tasks more effectively to other team members.",
      areasForImprovement:
        "Consider taking on more challenging technical tasks to expand skill set. Practice giving constructive feedback to peers.",
      generatedAt: "2026-02-25T14:30:00",
    },
  ],

  finalScores: [
    { studentId: "s1", studentName: "Maria Santos", adjustedAverage: 8.2, totalEvaluators: 4, rawScores: [7, 8, 9, 8, 9] },
    { studentId: "s2", studentName: "Juan Dela Cruz", adjustedAverage: 7.5, totalEvaluators: 4, rawScores: [6, 7, 8, 8, 9] },
    { studentId: "s3", studentName: "Ana Reyes", adjustedAverage: 8.8, totalEvaluators: 4, rawScores: [8, 9, 9, 10, 8] },
    { studentId: "s4", studentName: "Carlos Bautista", adjustedAverage: 7.0, totalEvaluators: 4, rawScores: [6, 7, 7, 8, 7] },
  ],
}));
