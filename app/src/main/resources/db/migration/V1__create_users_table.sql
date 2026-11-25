CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255),
    date_of_birth DATE,
    avatar_url VARCHAR(500),
    created_date TIMESTAMPTZ NOT NULL
);
