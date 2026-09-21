-- src/main/resources/db/migration/V2__update_user_role.sql
ALTER TABLE users ADD CONSTRAINT check_user_role CHECK (role IN ('ADMIN', 'MEMBER'));