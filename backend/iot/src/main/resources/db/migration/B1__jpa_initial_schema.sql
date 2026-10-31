-- Fresh databases start here instead of the legacy V1 hardware schema.
-- Keep V1 unchanged for environments with existing migration history.
-- V2 onwards add monitoring columns, tables and seed warehouses.
CREATE TABLE permission (
    permission_id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);

CREATE TABLE role (
    role_id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);

CREATE TABLE `user` (
    user_id VARCHAR(255) NOT NULL PRIMARY KEY,
    email VARCHAR(255),
    password VARCHAR(255),
    firstname VARCHAR(255),
    lastname VARCHAR(255),
    phone VARCHAR(255),
    status_user ENUM('ACTIVE', 'INACTIVE'),
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);

CREATE TABLE permission_role (
    role_id VARCHAR(255) NOT NULL,
    permission_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(role_id),
    FOREIGN KEY (permission_id) REFERENCES permission(permission_id)
);

CREATE TABLE role_user (
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES `user`(user_id),
    FOREIGN KEY (role_id) REFERENCES role(role_id)
);

CREATE TABLE product (
    product_id VARCHAR(255) NOT NULL PRIMARY KEY,
    sku VARCHAR(255),
    name VARCHAR(255),
    category VARCHAR(255),
    description TEXT,
    image_url TEXT,
    unit_weight DOUBLE NOT NULL,
    unit ENUM('ITEM', 'BOX', 'BOTTLE', 'CAN', 'PACK', 'BAG', 'KG', 'G', 'LITER', 'ML', 'PIECE'),
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);

CREATE TABLE warehouse (
    warehouse_id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    address VARCHAR(255),
    description TEXT,
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);
