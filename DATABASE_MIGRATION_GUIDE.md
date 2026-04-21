# Database Cleanup and Migration Guide

## Overview
This guide helps you remove the ADMIN role from your database and add the STUDENT role.

## ⚠️ Important: Backup First!
Before running any SQL commands, backup your database:
```sql
mysqldump -u root -p adviser_evaluation_db > backup_before_migration.sql
```

## Option 1: Using MySQL Workbench or Command Line

### Step 1: Connect to your database
```bash
mysql -u root -p
use adviser_evaluation_db;
```

### Step 2: Check existing ADMIN users
```sql
SELECT * FROM users WHERE role = 'ADMIN';
SELECT * FROM allowed_users WHERE assigned_role = 'ADMIN';
```

### Step 3: Delete ADMIN users (if you're sure!)
```sql
-- Delete the teacher@system.com user (old admin account)
DELETE FROM users WHERE email = 'teacher@system.com' AND role = 'ADMIN';

-- Delete any other ADMIN users
DELETE FROM users WHERE role = 'ADMIN';

-- Delete ADMIN from allowed_users
DELETE FROM allowed_users WHERE assigned_role = 'ADMIN';
```

### Step 4: Alter the enum column to add STUDENT and ensure ADMIN is removed
```sql
-- For users table
ALTER TABLE users MODIFY COLUMN role ENUM('TEACHER', 'ADVISER', 'STUDENT') NOT NULL;

-- For allowed_users table  
ALTER TABLE allowed_users MODIFY COLUMN assigned_role ENUM('TEACHER', 'ADVISER', 'STUDENT') NOT NULL;
```

### Step 5: Verify the changes
```sql
-- Should return empty (no ADMIN users)
SELECT * FROM users WHERE role = 'ADMIN';

-- Should show only TEACHER, ADVISER, STUDENT
SELECT DISTINCT role FROM users;
SELECT DISTINCT assigned_role FROM allowed_users;
```

## Option 2: Drop and Recreate (Clean Start)

If you want to start fresh:

### Step 1: Stop your Spring Boot application

### Step 2: Drop the database
```sql
DROP DATABASE adviser_evaluation_db;
CREATE DATABASE adviser_evaluation_db;
```

### Step 3: Restart Spring Boot
The application will automatically create tables with the new role structure (TEACHER, ADVISER, STUDENT).

The DataSeeder will create: `teacher@system.com` / `Teacher@123` with TEACHER role.

## After Migration

### Verify your setup:
1. Start the backend
2. Check the console for: "=== Default teacher admin account created: teacher@system.com / Teacher@123 ==="
3. Login with `teacher@system.com` / `Teacher@123`
4. Navigate to User Management
5. Upload a role sheet with STUDENT users

### Expected Role Structure:
- **TEACHER**: Full access including user management
- **ADVISER**: Evaluation and team management
- **STUDENT**: (Coming soon - will be implemented in future updates)

## Troubleshooting

### Issue: "Data truncation: Data truncated for column 'role'"
**Solution**: The enum still has the old values. Run the ALTER TABLE commands from Step 4 above.

### Issue: "Cannot delete or update a parent row: a foreign key constraint fails"
**Solution**: There are related records. Delete in this order:
1. `allowed_users` records first
2. Then `users` records

### Issue: Backend won't start after migration
**Solution**: 
1. Check application.properties for `spring.jpa.hibernate.ddl-auto=update`
2. Verify database connection string
3. Check console logs for specific errors

## Verification Script

Run this after migration to verify everything is correct:

```sql
-- Check role enum values
SHOW COLUMNS FROM users LIKE 'role';
SHOW COLUMNS FROM allowed_users LIKE 'assigned_role';

-- Check existing users
SELECT id, email, role FROM users;

-- Should NOT find any ADMIN
SELECT COUNT(*) as admin_count FROM users WHERE role = 'ADMIN';
-- Result should be 0

-- Verify teacher@system.com exists with TEACHER role
SELECT * FROM users WHERE email = 'teacher@system.com';
-- Should show role = 'TEACHER'
```

---
*Last Updated: March 2, 2026*
*Part of Admin Role Removal and Student Role Addition Migration*
