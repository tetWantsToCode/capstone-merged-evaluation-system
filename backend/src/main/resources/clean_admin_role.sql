-- SQL Script to Remove ADMIN Role and Add STUDENT Role
-- Run this script if you have an existing database with ADMIN role data

-- Step 1: Delete any users with ADMIN role (backup first if needed!)
-- This will delete the teacher@system.com user created by the old DataSeeder
DELETE FROM users WHERE role = 'ADMIN';

-- Step 2: Delete any allowed_users with ADMIN role
DELETE FROM allowed_users WHERE assigned_role = 'ADMIN';

-- Step 3: Modify the enum to add STUDENT and remove ADMIN
-- Note: MySQL doesn't allow direct enum modification, so we need to alter the column
ALTER TABLE users MODIFY COLUMN role ENUM('TEACHER', 'ADVISER', 'STUDENT') NOT NULL;
ALTER TABLE allowed_users MODIFY COLUMN assigned_role ENUM('TEACHER', 'ADVISER', 'STUDENT') NOT NULL;

-- Verification queries:
-- SELECT * FROM users WHERE role = 'ADMIN'; -- Should return empty
-- SELECT DISTINCT role FROM users; -- Should show only TEACHER, ADVISER, STUDENT
