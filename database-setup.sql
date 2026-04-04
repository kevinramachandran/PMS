-- Create database
CREATE DATABASE IF NOT EXISTS brewery_pms;

-- Create app user
CREATE USER IF NOT EXISTS 'breweryAdmin123'@'localhost' IDENTIFIED BY 'Password@123';

-- Grant privileges
GRANT ALL PRIVILEGES ON brewery_pms.* TO 'breweryAdmin123'@'localhost';

-- Flush privileges
FLUSH PRIVILEGES;

-- Switch to the database
USE brewery_pms;
