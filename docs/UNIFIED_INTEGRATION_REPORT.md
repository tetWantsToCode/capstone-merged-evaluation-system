# Unified System Integration Report — APEER + Adviser Evaluation System

This document summarizes the merge of APEER (peer evaluation) into the Adviser Evaluation System to create **one unified application** with a single role-based dashboard.

---

## 1. Summary

- **Goal:** One unified dashboard; role-based access (Teacher, Adviser, Student); APEER fully integrated; shared backend and database.
- **Result:** Single login → role-based redirect → unified dashboards. APEER peer evaluation features are under Teacher (Peer Evaluations) and Student (Dashboard, Evaluate) sections.
- **AI features:** Skipped for final stage.

---

## 2. Routes Added or Merged

### New Routes (Integrated)

| Path | Role | Component |
|------|------|-----------|
| `/teacher/peer-evaluations` | TEACHER | PeerEvaluations |
| `/student/dashboard` | STUDENT | StudentDashboard |
| `/student/evaluate/:activityId` | STUDENT | StudentEvaluationForm |
| `/thank-you` | All | ThankYou |

### Legacy Redirects (Backward Compatibility)

| Old Path | Redirects To |
|----------|--------------|
| `/apeer` | `/login` |
| `/apeer/login` | `/login` |
| `/apeer/teacher` | `/teacher/dashboard` |
| `/apeer/student` | `/student/dashboard` |
| `/evaluate/:activityId` | `/student/evaluate/:activityId` (if STUDENT) or `/login` |

### Removed Standalone APEER Routes

- `/apeer` (landing)
- `/apeer/login` (demo login)
- `/apeer/teacher`
- `/apeer/student`
- `/evaluate/:activityId` (replaced by `/student/evaluate/:activityId`)

---

## 3. Components/Pages Added or Integrated

### New Pages (in main structure)

| File | Purpose |
|------|---------|
| `pages/DashboardTeacher/PeerEvaluations.js` | Teacher: create/list peer evaluation activities; uses TeacherSidebar |
| `pages/DashboardStudent/StudentDashboard.js` | Student: list active evaluations, start/continue; uses StudentSidebar |
| `pages/DashboardStudent/StudentEvaluationForm.js` | Student: evaluate each member per criterion; uses StudentSidebar |
| `pages/ThankYou.js` | Post-submission confirmation; navigates to role-appropriate dashboard |
| `components/Sidebar/StudentSidebar.js` | Student navigation: Dashboard, Profile, Logout |

### Edits to Existing Files

| File | Change | Justification |
|------|--------|---------------|
| `pages/Login/Login.js` | Added `STUDENT` to `redirectByRole` → `/student/dashboard` | Students must land on student dashboard after login |
| `components/Sidebar/TeacherSidebar.js` | Added "Peer Evaluations" link → `/teacher/peer-evaluations` | Integrate APEER under teacher panel |
| `App.js` | Added STUDENT routes, Peer Evaluations route, Profile allows STUDENT, legacy redirects, `EvaluateRedirect` | Unified routing and role-based access |
| `services/apeerApi.js` | Added `apeerActivityAPI`, `apeerSubmissionAPI` (activities, members, submit, status) | Wire frontend to APEER backend |

---

## 4. Backend Modules Integrated

### APEER Backend (already present)

- Package: `group9.advisor_eval_system.apeer`
- Controllers: `ApeerActivityController`, `ApeerSubmissionController`
- Endpoints: `POST/GET /api/apeer/activities`, `GET /api/apeer/activities/for-student`, `GET /api/apeer/activities/{id}/members`, `GET /api/apeer/activities/{id}/submission-status`, `POST /api/apeer/evaluations/submit`

### Backend Edits (minimal)

| File | Change | Justification |
|------|--------|---------------|
| `StudentRepository.java` | Added `findByEmail(String email)` | Match User to Student by email for peer evaluation |
| `EvaluationActivityRepository.java` | Added `findBySchoolClassIdInAndIsActiveTrueOrderByCreatedAtDesc` | List activities for a student’s classes |
| `EvaluationActivityService.java` | Added `listForStudent(String userEmail)` | Activities available to a student |
| `ApeerActivityController.java` | Added `GET /for-student` | Student-facing activity list |
| `ApeerSubmissionController.java` | Added `GET /activities/{id}/submission-status` | Check completion status |
| `PeerEvaluationService.java` | Added `getSubmissionStatus` | Return submitted count and completion flag |

---

## 5. Database Tables Used

| Table | Purpose |
|-------|---------|
| `apeer_evaluation_activities` | Peer evaluation sessions |
| `apeer_peer_evaluations` | Individual peer submissions |
| `apeer_comments` | Comments per evaluation (ai_tag for future) |
| `users` | Auth (unchanged) |
| `students` | Class membership (unchanged) |
| `classes` | Classes (unchanged) |

---

## 6. Archived Files (APEER)

All files moved to `frontend/src/archive-apeer-unused/apeer/` with comment:  
`/* Archived during unified dashboard integration – not removed for safety. */`

| Archived Path |
|---------------|
| `apeer/pages/ApeerLandingPage.js` |
| `apeer/pages/ApeerLoginPage.js` |
| `apeer/pages/ApeerTeacherDashboard.js` |
| `apeer/pages/ApeerStudentDashboard.js` |
| `apeer/pages/ApeerEvaluationForm.js` |
| `apeer/pages/ApeerThankYouPage.js` |
| `apeer/components/ApeerLayout.js` |
| `apeer/components/ApeerProtectedRoute.js` |
| `apeer/components/ApeerLogo.js` |
| `apeer/store/useApeerStore.js` |

---

## 7. Confirmation Checklist

- [x] One unified dashboard: Teacher uses TeacherSidebar; Student uses StudentSidebar; Adviser unchanged
- [x] APEER integrated: Teacher → Peer Evaluations; Student → Dashboard + Evaluate
- [x] Shared backend and database
- [x] Role-based access: Login redirects by role; ProtectedRoute enforces roles
- [x] UI/UX: Same sidebar styles, Teacher.css, SummaryCard
- [x] AI features skipped
- [x] Unused APEER files archived (not deleted)

---

## 8. Testing Notes

1. **Teacher:** Log in → Dashboard → Peer Evaluations → Create activity (title, criteria, class, deadline) → Activities listed
2. **Student:** Log in (STUDENT) → Student Dashboard → Active evaluations → Start/Continue → Fill form per member → Submit → Thank You
3. **Student–User link:** Student must have `email` matching User `email` for activity visibility
4. **Legacy links:** `/apeer`, `/apeer/student`, `/evaluate/123` redirect correctly

---

*Report generated after unified dashboard integration.*
