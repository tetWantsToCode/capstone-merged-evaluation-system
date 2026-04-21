# APEER Backend Implementation — Change Tracking Report

This document lists every addition and change made to the backend for the APEER (peer evaluation) system, per SRS/SDD Module 5, without modifying the other group's existing logic.

---

## 1. Summary

- **Objective:** Implement backend for APEER (Create Evaluation Session, Submit Peer Evaluation) per SRS and SDD.
- **Approach:** New package `group9.advisor_eval_system.apeer` with entities, repositories, DTOs, services, and controllers. Same database; new tables only. No AI features.
- **Existing backend:** Untouched (no edits to existing controllers, services, repositories, entities, or security logic except as noted below).

---

## 2. Files Created (All New)

### 2.1 Entities (`backend/src/main/java/group9/advisor_eval_system/apeer/entity/`)

| File | Purpose |
|------|--------|
| `EvaluationActivity.java` | Peer evaluation session (title, deadline, rubric criteria, created by teacher, optional class). Table: `apeer_evaluation_activities`. |
| `PeerEvaluation.java` | One peer evaluation submission (evaluator, target student, activity, rubric scores JSON, comment). Table: `apeer_peer_evaluations`. |
| `PeerEvaluationComment.java` | Comment for a peer evaluation (content, optional ai_tag for future AI). Table: `apeer_comments`. |

### 2.2 Repositories (`backend/src/main/java/group9/advisor_eval_system/apeer/repository/`)

| File | Purpose |
|------|--------|
| `EvaluationActivityRepository.java` | JPA repository for EvaluationActivity (by teacher, active). |
| `PeerEvaluationRepository.java` | JPA repository for PeerEvaluation (by activity, evaluator, target). |
| `PeerEvaluationCommentRepository.java` | JPA repository for PeerEvaluationComment (by evaluation). |

### 2.3 DTOs (`backend/src/main/java/group9/advisor_eval_system/apeer/dto/`)

| File | Purpose |
|------|--------|
| `CreateActivityRequest.java` | Request body for creating an activity (title, rubricCriteria, deadline, classId). |
| `EvaluationSubmissionRequest.java` | Request body for submitting one peer evaluation (evaluatorId, targetStudentId, activityId, rubricScores, commentContent). |
| `ActivityResponse.java` | Response for an activity (id, title, deadline, isActive, rubricCriteria, createdByTeacherId, classId, createdAt). |
| `MemberResponse.java` | One member in the list for an activity (studentId, firstName, lastName, email, orderIndex). |

### 2.4 Services (`backend/src/main/java/group9/advisor_eval_system/apeer/service/`)

| File | Purpose |
|------|--------|
| `ScoreValidator.java` | Validates rubric scores (0–10 per criterion). Uniqueness per criterion across members enforced in service. |
| `EvaluationActivityService.java` | Create activity, list by teacher, get by id. Uses existing User and SchoolClass. |
| `PeerEvaluationService.java` | Get members for activity (students in activity’s class), submit peer evaluation (with validation; no AI). |

### 2.5 Controllers (`backend/src/main/java/group9/advisor_eval_system/apeer/controller/`)

| File | Purpose |
|------|--------|
| `ApeerActivityController.java` | `POST /api/apeer/activities`, `GET /api/apeer/activities`, `GET /api/apeer/activities/{id}`. Resolves teacher from JWT. |
| `ApeerSubmissionController.java` | `GET /api/apeer/activities/{activityId}/members`, `POST /api/apeer/evaluations/submit`. |

---

## 3. Database Tables Added

| Table | Description |
|-------|-------------|
| `apeer_evaluation_activities` | activity_id (PK), title, deadline, is_active, rubric_criteria (JSON), created_by (FK → users), class_id (FK → classes), created_at. |
| `apeer_peer_evaluations` | id (PK), evaluator_id (FK → users), target_student_id (FK → students), activity_id (FK → apeer_evaluation_activities), rubric_scores (JSON), comment_content (TEXT), submitted_at, created_at. |
| `apeer_comments` | id (PK), eval_id (FK → apeer_peer_evaluations, unique), content (TEXT), ai_tag (VARCHAR, null for now), tagged_at, created_at. |

- **Existing tables:** Not altered. Naming avoids conflict with existing `evaluations` (adviser evaluations).

---

## 4. Existing Files Modified

**None.** No changes were made to existing controllers, services, repositories, entities, SecurityConfig, or JwtAuthenticationFilter. APEER endpoints are under `/api/apeer/**` and are secured by the existing `anyRequest().authenticated()` rule.

---

## 5. API Endpoints (APEER)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/apeer/activities` | Create evaluation activity (teacher; JWT). |
| GET | `/api/apeer/activities` | List activities for current teacher (JWT). |
| GET | `/api/apeer/activities/{id}` | Get activity by id. |
| GET | `/api/apeer/activities/{activityId}/members` | Get ordered list of members (students in activity’s class). |
| POST | `/api/apeer/evaluations/submit` | Submit one peer evaluation (body: evaluatorId, targetStudentId, activityId, rubricScores, commentContent). |

---

## 6. What Was Not Implemented (By Design)

- **AI:** No comment classification (Constructive/Vague/Off-topic). `ai_tag` and `tagged_at` exist on `apeer_comments` but are left null for the final phase.
- **Modifications to existing code:** No changes to the other group’s packages.

---

## 7. Testing Checklist

- [ ] Original backend starts and existing endpoints (e.g. `/api/classes`, `/api/teams`, `/api/auth/google/login`) still work.
- [ ] `POST /api/apeer/activities` with valid JWT creates an activity and returns 201.
- [ ] `GET /api/apeer/activities` returns the teacher’s activities.
- [ ] `GET /api/apeer/activities/{id}/members` returns students in the activity’s class.
- [ ] `POST /api/apeer/evaluations/submit` with valid body persists evaluation and comment and returns 201.
- [ ] Score validation: score outside 0–10 or duplicate score per criterion returns 400 with message.
- [ ] Shared database: both existing and APEER tables work on the same MySQL instance.

---

*Report generated after APEER backend implementation per SRS/SDD Module 5.*
