-- ============================================================
-- MySQL 初始化脚本
-- Docker Compose 启动时自动执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS integration_config
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS integration_log
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS integration_token
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 授予应用连接账户权限（可选，供外部应用使用）
CREATE USER IF NOT EXISTS 'app'@'%' IDENTIFIED BY 'app-secret';
GRANT ALL PRIVILEGES ON integration_config.*  TO 'app'@'%';
GRANT ALL PRIVILEGES ON integration_log.*     TO 'app'@'%';
GRANT ALL PRIVILEGES ON integration_token.*  TO 'app'@'%';
FLUSH PRIVILEGES;
