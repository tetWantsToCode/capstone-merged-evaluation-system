# Project Flow Guide

This document explains how the system runs from login to evaluation reporting.

## 1. High-Level Architecture

- Frontend: React app in frontend/src
- Backend: Spring Boot app in backend/src/main/java
- Database: MySQL (adviser_evaluation_db)
- Auth model: Google OAuth for sign-in + JWT for API authorization
- Core domain: Teacher creates class/team/questionnaire, adviser evaluates assigned teams, teacher reviews results

## 2. Runtime Startup Flow

1. Backend starts on port 8080 and connects to MySQL.
2. Spring Security is configured as stateless JWT auth.
3. Frontend starts on port 3000.
4. User opens login page and authenticates using Google OAuth.
5. Backend returns authenticated user info plus JWT token.
6. Frontend stores the auth payload in localStorage under user.
7. Protected routes allow access based on role and token.

## 3. Authentication and Authorization Flow

### Login Flow (Google OAuth)

1. Frontend Login page requests authorization URL from:
   - GET /api/auth/google/authorization-url
2. Frontend opens popup to Google consent screen.
3. Google redirects to frontend callback page with authorization code.
4. Callback page posts code to opener window via postMessage.
5. Frontend sends code to backend:
   - POST /api/auth/google/callback
6. Backend verifies Google identity and checks if email exists in User table.
7. Backend stores Google tokens on user record and issues JWT.
8. Frontend stores returned user payload + JWT.

### Protected API Flow

1. Frontend sends Authorization: Bearer <token>.
2. JwtAuthenticationFilter validates token.
3. Filter extracts userId and role and sets Spring Security context.
4. Controller methods then enforce role checks where needed.

## 4. Teacher Functional Flow

### A. Class and Team Setup

1. Teacher manages classes:
   - /api/classes
2. Teacher manages teams and adviser assignments:
   - /api/teams
3. Teacher manages students (manual or import):
   - /api/students
   - /api/students/import

### B. Google Linking for Form Creation

1. Teacher links Google account from Profile page:
   - GET /api/google-auth/authorization-url
   - POST /api/google-auth/callback
2. Link status is checked with:
   - GET /api/google-auth/status
3. Without linked Google account, questionnaire creation is blocked.

### C. Questionnaire Lifecycle

1. Teacher creates questionnaire with items:
   - POST /api/questionnaires
2. Backend creates Google Form through Google Forms API.
3. Questionnaire and items are persisted in DB.
4. Teacher assigns questionnaire to one or more classes:
   - POST /api/questionnaires/{id}/assign
5. Teacher can update, unassign, or soft-delete questionnaires.

### D. Report Viewing

1. Teacher fetches own questionnaires for report screen:
   - GET /api/teacher/reports/questionnaires
2. Teacher fetches all evaluations for one questionnaire:
   - GET /api/teacher/reports/questionnaire/{questionnaireId}/evaluations
3. Teacher opens one full evaluation detail:
   - GET /api/teacher/reports/evaluation/{evaluationId}

## 5. Adviser Functional Flow

1. Adviser dashboard loads assigned teams:
   - GET /api/adviser/teams
2. Adviser opens team and sees class questionnaires:
   - GET /api/adviser/teams/{teamId}/questionnaires
3. Adviser opens evaluation form:
   - GET /api/adviser/evaluation/{teamId}/{questionnaireId}
4. Backend returns existing draft or creates new IN_PROGRESS evaluation.
5. Adviser saves draft answers:
   - POST /api/adviser/evaluation/save
6. Adviser submits final evaluation:
   - POST /api/adviser/evaluation/submit/{evaluationId}
7. Status becomes SUBMITTED and editing is locked.
8. Adviser can view completed submissions:
   - GET /api/adviser/evaluations/completed

## 6. Data and Status Flow

- Evaluation starts as IN_PROGRESS.
- Draft save overwrites previous score entries for that evaluation.
- Submit sets:
  - status = SUBMITTED
  - submittedAt = now
  - allowEdit = false
- Teacher reports read submitted evaluation data and questionnaire context.

## 7. Frontend Navigation Flow

- Public routes:
  - /login
- Teacher routes (role: TEACHER):
  - /teacher/dashboard
  - /teacher/classes
  - /teacher/teams
  - /teacher/questionnaires
  - /teacher/reports
  - /teacher/reports/evaluation/:evaluationId
  - /teacher/students
  - /teacher/advisers
  - /teacher/user-management
- Adviser routes (role: ADVISER):
  - /adviser/dashboard
  - /adviser/evaluations/:teamId
  - /adviser/evaluate/:teamId/:questionnaireId
  - /adviser/completed
- Shared protected route:
  - /profile

## 8. End-to-End Business Sequence

1. Admin/teacher populates users.
2. Teacher logs in with Google.
3. Teacher creates classes, teams, and student records.
4. Teacher links Google account (if not linked yet).
5. Teacher creates questionnaire and assigns it to classes.
6. Adviser logs in and sees assigned teams.
7. Adviser evaluates team using assigned questionnaire.
8. Adviser submits evaluation.
9. Teacher opens reports and reviews evaluation results.

## 9. Optional AI Assistant Flow

- Endpoint: POST /api/ai/chat
- Access: Teacher only
- Service builds context from teacher classes, questionnaires, and submitted evaluations
- Gemini is used to generate assistant responses for report interpretation and questionnaire support
