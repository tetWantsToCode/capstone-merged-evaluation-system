# SRS & SDD — Summary of Content and Diagrams

This document summarizes the **Adviser and Peer Evaluation System** SRS and SDD (and all diagrams reviewed) from the attached Word documents. Extracted text and diagram descriptions are below.

---

## 1. SRS (Software Requirements Specification) — Summary

### 1.1 Purpose, scope, definitions

- **Purpose:** The Adviser and Peer Evaluation System is a web-based platform for fair, efficient class evaluations. It lets students and advisers/teachers submit, process, and analyze evaluation data and provides AI-generated summaries. It consolidates peer and adviser evaluation and uses Google APIs for auth and data.
- **Scope:** Secure web app for academic institutions with:
  - User/role authentication via Google OAuth2
  - Class and team management
  - Student and adviser evaluation modules
  - Excel (.xlsx) and Google Forms integration
  - AI summaries (BART/T5 or Gemini)
  - Reporting/export (PDF/CSV), score processing, RBAC
- **Definitions:** AES, SRS, SDD, API, AI, NLP, Gemini, BART/T5, OAuth2, RBAC, JWT, REST, DTO, ERD, CRUD, HTTPS, UAT, CIT-U.

### 1.2 Overall description

- **Product perspective:** Frontend (React), backend (Spring Boot), DB (MySQL), AI (Gemini), Google Forms + OAuth. Digital workflow for teachers (configure classes/teams), advisers (evaluate teams), AI (summaries), teachers (reports).
- **User characteristics:** Teacher (classes, students, teams, questionnaires, summaries); Adviser (assigned teams, evaluate); Student (Gmail, Google auth, peer/self evaluation).
- **Constraints:** Data Privacy Act 2012 (Philippines), internet, Google API dependency, AI in English, RBAC, API quotas, peak responsiveness.
- **Assumptions:** Google APIs stable, Gemini available, users have internet, Spring/JWT supported, teachers upload valid Excel with names/Gmail.

### 1.3 Specific requirements (high level)

- **External interfaces:** Hardware (server: quad-core, 8 GB RAM, 50 GB SSD; clients 1366×768+); software (Chrome/Edge/Firefox, Spring Boot 3+, React, MySQL 8+, Google Forms/OAuth, Gemini/OpenAI/Hugging Face, GCP/AWS/Azure); communications (REST/HTTPS, OAuth/tokens, JDBC).
- **Functional (by module):**
  - **Module 1 – Class creation:** Teacher creates/manages classes (name, section, school year, status); validation, DB storage, link to teacher.
  - **Module 1 – Student management:** Add/edit/remove students, assign to classes, unique student ID, validation.
  - **Module 1 – Mass export:** Export class data, team assignments, student records, evaluation results (Google Sheet/CSV).
  - **Module 2 – Adviser dashboard:** Shows assigned teams, evaluation status (Pending/Completed), access to evaluation interface.
  - **Module 2 – Evaluation & scoring:** Adviser evaluates team with internal questionnaire (scores per question), validation, save, sync to Google Sheets.
  - **Module 2 – Evaluation tracking:** View progress, pending/completed, view/update submitted (if allowed), re-sync.
  - **Module 3 – Performance ranking:** Teacher sees team rankings (averages, best/worst).
  - **Module 3 – AI-assisted summary:** Teacher triggers summary; AI returns strengths, weaknesses, patterns; display on report page.
  - **Module 3 – Teacher reporting:** Export PDF/CSV.
  - **Module 4 – Student list:** Teacher uploads Excel (.xlsx); system validates, extracts names/Gmail, stores whitelist; controls login and group display.
  - **Module 5 – Create evaluation session:** Teacher defines title, rubric criteria, deadline; stored; accessible to whitelisted students.
  - **Module 5 – Submit peer evaluation:** Student scores each member per criterion (0–10), unique score per criterion, mandatory comment; AI classifies comment (Constructive/Vague/Off-topic).
  - **Module 6 – Authentication & security:** Login, role-based access (Teacher/Adviser/Admin), secure data storage/transmission.
  - **Module 7 – Data import:** Import/update class, team, student, adviser via Google Sheets or CSV; validate, normalize, store; import/sync reports.
- **Non-functional:** Performance (e.g. 200 concurrent, AI 5–10 s, dashboard &lt;3 s), security (HTTPS, hashed credentials, RBAC, Data Privacy/GDPR, encryption), reliability (99.5% uptime, backups, retries, graceful failures).

---

## 2. SRS Diagrams (5 images) — Summary

### Image 1 — Use case: Authentication & Security (Module 4)

- **System boundary:** “Adviser Evaluation System (Module 4: Authentication & Security).”
- **Actors:** Teacher, Adviser, Admin, External APIs (Google, AI).
- **Use cases:** User Authentication (Login); Verify User Credentials; Grant Role-Based Access; Enforce Role Restrictions; Input Validation & Attack Prevention; Secure Data Storage & Transmission.
- **Relationships:** Teacher/Adviser/Admin → Login; Login includes Verify Credentials → Grant Role-Based Access → Enforce Role Restrictions; Input Validation includes Secure Data Storage; External APIs ↔ Secure Data Storage.
- **Relevance:** APEER must use existing login and RBAC; no backend auth changes.

### Image 2 — ERD: Evaluation activities, evaluations, comments

- **Entities:**  
  - **evaluation_activities:** activity_id (PK), title, deadline, is_active, rubric_criteria (JSON), created_by (FK → users).  
  - **evaluations:** eval_id (PK), evaluator_id, target_student_id, activity_id (FK), rubric_scores (JSON), comment_content, submitted_at.  
  - **comments:** comment_id (PK), eval_id (FK), content, ai_tag, tagged_at.
- **Relationships:** evaluation_activities “contains” evaluations (1:N); evaluations “has comment” comments (1:N).
- **Relevance:** API/service layer must align with these tables; no schema changes.

### Image 3 — Use case: Data Import Module (Module 1)

- **Actors:** Teacher, Google Sheets API.
- **Use cases:** Import Data (includes Store Data, Process & Map Data, Validate Data, Generate Import Report, Upload Structured Sheet); Re-Import/Update Data (includes Upload Sheet, Generate Update Report, Compare Records, Sync to Google Sheets); Export Data (includes Select Data Type, Download File, Generate Export File, Update/Create/Deactivate Records, Save to Google Sheets).
- **Relevance:** APEER uses existing import/export/sync APIs; no backend changes.

### Image 4 — Use case: Adviser Evaluation (Module 2)

- **Actors:** Adviser, Google Sheets API.
- **Use cases:** Track Evaluation Progress; View Assigned Teams; View/Update Submitted Evaluation; Recompute Total Score; Evaluate Team; Submit Evaluation Scores; View Questionnaire; Re-sync/Sync to Google Sheets; Compute Total Score. Includes/extends show Evaluate Team → Submit Scores → Compute Total → Sync; View/Update → Recompute → Re-sync.
- **Relevance:** APEER consumes existing evaluation and sync endpoints.

### Image 5 — Use case: Reporting & AI Summarization (Module 3)

- **Actors:** Teacher, AI Service (Gemini/OpenAI).
- **Use cases:** View AI Insights & Patterns; Export Reports (PDF/CSV); Calculate Team Rankings; View Performance Rankings; Generate AI Summary; Analyze Evaluation Data. Teacher triggers View Rankings (includes Calculate Rankings), Generate Summary (includes Analyze Data); AI Service performs Analyze Evaluation Data.
- **Relevance:** APEER teacher/report UI calls existing report and AI-summary APIs.

---

## 3. SDD (Software Design Description) — Summary

### 3.1 Architecture

- **Frontend:** React.js.  
- **Backend:** Spring Boot (REST API).  
- **Database:** MySQL.

### 3.2 Detailed design (by module/transaction)

- **Module 1**
  - **Class creation:** UI in `frontend/src/pages/DashboardTeacher/Classes.js` (table, “+ Create New Class”, modal with name, section, school year, description, active). Uses `classAPI.getAllClasses()`, `classAPI.createClass()` with teacherId from localStorage. Backend: SchoolClassController (`/api/classes`), SchoolClassService, SchoolClassRepository, SchoolClass entity.
  - **Student management:** UI in `Student.js` (table, Add/Edit/Delete, modal: classes, studentId, first/last name, email, phone). Uses studentAPI, classAPI. Backend: StudentController (`/api/students`), StudentService, StudentRepository.
  - **Team formation:** UI in `Teams.js` (table, Create New Team, Manage Team with members/advisers). Uses teamAPI, classAPI, studentAPI, authAPI. Backend: TeamController (`/api/teams`), TeamService, TeamRepository, Team entity (classId, memberIds, adviserIds).
  - **Adviser assignment:** Inside Manage Team modal; advisers from authAPI.getAllUsers() filtered by ADVISER; teamAPI.updateTeam with adviserIds.
  - **Questionnaire assignment:** Questionnaires.js (Assign to Classes modal) and Classes.js (Add/Remove questionnaire). questionnaireAPI.assignToClasses, unassignFromClasses. Backend: QuestionnaireController (`/api/questionnaires`), QuestionnaireService, QuestionnaireRepository, Questionnaire entity, AssignQuestionnaireRequest DTO.

- **Module 2 – Adviser evaluation**
  - **Adviser dashboard:** `Adviser.js` — summary cards (Teams Assigned, Completed, Pending), assigned teams table, teamAPI.getAllTeams() filtered by adviserIds. AdviserSidebar, SummaryCard. Backend: TeamController GET /api/teams.
  - **Evaluation & scoring:** `Evaluations.js` — pending evaluations table, Evaluate opens form; questionnaire items (NUMERIC_SCALE/RATING, TEXT, MULTIPLE_CHOICE), general comments, Submit/Save Draft. evaluationAPI. Backend: Evaluation entity (status IN_PROGRESS/SUBMITTED/REVIEWED), EvaluationScore, EvaluationRepository (findByAdviserId, findByTeamId, etc.), EvaluationScoreRepository, QuestionnaireItem.
  - **Evaluation tracking:** Adviser.js, Evaluations.js, Completed.js, Reports.js; evaluationAPI, reportAPI; filter by status.

- **Module 3 – Reporting & AI**
  - Performance ranking: Reports.js, Teacher.js; ReportRepository, EvaluationRepository, TeamRepository, Report entity.
  - AI-assisted summary: AISummaryPanel, reportAPI; QuestionnaireController, GoogleFormsService, QuestionnaireService, AISummaryService, Report.
  - Comment collection: TeacherInsightsPanel.jsx, summaryService.js — GET /summaries/student/{studentId}/activity/{actId}; SummaryController, CommentCollectionService, SummarizationService.

- **Module 4 – Student management (whitelist)**
  - Student list: Teacher uploads Excel; CreateActivityForm.jsx, activityService.js (POST /students/upload, GET /students/export), ClassOverview.jsx. Backend: StudentController (upload/export), ExcelParserService, StudentRecord POJO, WhitelistRepository.

- **Module 5 – Self and peer evaluation**
  - Create evaluation session: CreateActivityForm.jsx, activityService.js POST /activities/create; ActivityController, EvaluationActivity entity, ActivityDTO.
  - Submit peer evaluation: EvaluationSubmissionView.jsx, EvaluationForm.jsx, submissionService.js (GET /activities/{id}/members, POST /evaluations/submit); SubmissionController, EvaluationService, ScoreValidator, EvaluationSubmissionDTO, AIServiceIntegration (CommentClassifier), Evaluation and Comment entities.

---

## 4. SDD Diagrams (sample of 55) — Summary

### Image 1 — Questionnaire management (class/service diagram)

- **Entities:** Questionnaire (id, title, description, googleFormId/Url, isActive, createdByTeacher, assignedClasses, assignedTeams), SchoolClass (id, name, section, schoolYear, teacher, assignedQuestionnaires), User (id, firstName, lastName, email, role, createdQuestionnaires), UserRole (TEACHER, ADVISER).
- **Controller/Service:** QuestionnaireController (assignToClasses, removeFromClasses, getUserFromToken), AssignQuestionnaireRequest (classIds), QuestionnaireService (assignToClasses, removeFromClasses), QuestionnaireRepository, SchoolClassRepository.
- **Relevance:** APEER uses existing questionnaire assign/unassign APIs and JWT auth.

### Image 2 — ERD: AI-Assisted Summary (Module 3.2)

- **Entities:** SchoolClass (id, name), Evaluation (id, generalComments, team_id), Report (id, aiSummary, generatedAt, class_id), EvaluationScore (id, numericScore, textResponse, evaluation_id).
- **Relationships:** SchoolClass has evaluations; SchoolClass summarized_in reports; Evaluation includes EvaluationScore.
- **Relevance:** Report and evaluation data shape for APEER report/summary UI.

### Image 3 — Adviser / Team (component and backend)

- **Frontend:** Adviser component (useState loading/error/assignedTeams, useMemo currentUser, useEffect load, render); uses teamAPI → TeamController; displays SummaryCard, AdviserSidebar.
- **Backend:** TeamController.getAllTeams(), TeamService.getAllTeams(), TeamRepository.findAll(); Team entity (id, name, description, isActive, advisers, getAdviserIds()), User (advisedTeams), UserRole.
- **Relevance:** Same pattern for APEER: call GET /api/teams, filter by adviser client-side if needed.

### Image 4 — ERD: Users, Students, Classes, Teams

- **Tables:** USERS (id, first_name, last_name, email, password, role, is_active, timestamps), STUDENTS (id, student_id UK, first_name, last_name, email, phone_number, timestamps), CLASSES (id, name, section, school_year, description, is_active, teacher_id FK, timestamps), TEAMS (id, name, description, is_active, class_id FK, timestamps).
- **Join tables:** STUDENT_CLASSES (student_id, class_id), STUDENT_TEAMS (student_id, team_id), TEAM_ADVISERS (team_id, adviser_id). Business rule: one team per class per student (TeamService).
- **Relevance:** Data model for all APEER API requests/responses; no schema changes.

### Image 5 — Sequence: Adviser dashboard / evaluations

- **Flow:** Adviser → Dashboard/Evaluations (React) → get currentUser from localStorage → evaluationAPI.getAllEvaluations() → GET /api/evaluations → EvaluationController → EvaluationService → EvaluationRepository (findByAdviserId) → DB → return evaluations; client filters IN_PROGRESS (Pending) and SUBMITTED/REVIEWED (Completed); display summary cards and lists. Evaluations page: getEvaluationsByAdviser(adviserId), server-side filter IN_PROGRESS.
- **Relevance:** APEER uses same endpoint and filtering pattern; EvaluationController/Service marked “Expected” in doc (implement or verify).

---

## 5. Extracted text files and images on disk

- **SRS text:** Extracted to `.docx-extract/srs-text.txt` (full SRS body).
- **SDD text:** Extracted to `.docx-extract/sdd-text.txt` (full SDD body).
- **SRS media:** `.docx-extract/srs/srs/word/media/` — image1.jpg, image2.png … image5.jpg (all 5 diagrams above).
- **SDD media:** `.docx-extract/sdd/sdd/word/media/` — image1.png … image55.png (55 diagrams; class diagrams, sequence diagrams, ERDs for each module/transaction).

---

## 6. Confirmation

- **SRS:** Full text read; all 5 SRS diagrams opened and summarized (use cases for Auth, Data Import, Adviser Evaluation, Reporting & AI; ERD for evaluation_activities/evaluations/comments).
- **SDD:** Full text read; sample of SDD diagrams (5 of 55) opened and summarized (questionnaire design, AI summary ERD, Adviser/Team component flow, core ERD Users/Students/Classes/Teams, Adviser evaluations sequence). Remaining SDD images (image6–image55) are in the same folder for reference; they follow the same module/transaction structure (class diagrams, sequence diagrams, ERDs per section).

If you want a summary focused on a specific module or diagram set (e.g. only Module 5 or only ERDs), say which section and I can expand that part.
