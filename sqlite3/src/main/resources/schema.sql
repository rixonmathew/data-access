-- Create employees table
CREATE TABLE IF NOT EXISTS employees (
    id TEXT PRIMARY KEY,
    first_name TEXT,
    last_name TEXT,
    email TEXT,
    department TEXT
);

-- Insert sample data
INSERT INTO employees (id, first_name, last_name, email, department)
VALUES
    ('1', 'John', 'Doe', 'john.doe@example.com', 'Engineering'),
    ('2', 'Jane', 'Smith', 'jane.smith@example.com', 'Marketing'),
    ('3', 'Bob', 'Johnson', 'bob.johnson@example.com', 'Finance'),
    ('4', 'Alice', 'Williams', 'alice.williams@example.com', 'HR'),
    ('5', 'Charlie', 'Brown', 'charlie.brown@example.com', 'Engineering');