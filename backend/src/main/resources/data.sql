-- Fix class_id column to allow NULL values
-- ALTER TABLE students MODIFY COLUMN class_id BIGINT NULL;

-- Update users role enum to include STUDENT role
-- ALTER TABLE users MODIFY COLUMN role ENUM('TEACHER','ADVISER','STUDENT') NOT NULL;

-- Note: The above ALTER statements are commented out because they're meant for
-- modifying an existing schema. When using ddl-auto=update, Hibernate will
-- handle schema updates from entity definitions automatically.
