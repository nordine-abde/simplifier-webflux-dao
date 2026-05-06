--liquibase formatted sql

--changeset users-app:create-users-table
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    city VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_app_user_visible_email ON app_user (deleted, email);
CREATE INDEX idx_app_user_visible_status_updated ON app_user (deleted, status, updated_at, id);
CREATE INDEX idx_app_user_visible_updated_id ON app_user (deleted, updated_at, id);
CREATE INDEX idx_app_user_visible_city ON app_user (deleted, city);

--changeset users-app:seed-users
INSERT INTO app_user (
    id,
    email,
    display_name,
    role,
    status,
    city,
    created_at,
    updated_at,
    deleted,
    deleted_at
) VALUES
('00000000-0000-0000-0000-000000000001', 'ava.ross@example.com', 'Ava Ross', 'ADMIN', 'ACTIVE', 'Rome', CURRENT_TIMESTAMP - INTERVAL '40 days', CURRENT_TIMESTAMP - INTERVAL '40 days', false, NULL),
('00000000-0000-0000-0000-000000000002', 'leo.martin@example.com', 'Leo Martin', 'MEMBER', 'ACTIVE', 'Milan', CURRENT_TIMESTAMP - INTERVAL '39 days', CURRENT_TIMESTAMP - INTERVAL '39 days', false, NULL),
('00000000-0000-0000-0000-000000000003', 'mia.greco@example.com', 'Mia Greco', 'MANAGER', 'ACTIVE', 'Turin', CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '38 days', false, NULL),
('00000000-0000-0000-0000-000000000004', 'noah.conti@example.com', 'Noah Conti', 'VIEWER', 'INVITED', 'Naples', CURRENT_TIMESTAMP - INTERVAL '37 days', CURRENT_TIMESTAMP - INTERVAL '37 days', false, NULL),
('00000000-0000-0000-0000-000000000005', 'sofia.ferrari@example.com', 'Sofia Ferrari', 'MEMBER', 'ACTIVE', 'Bologna', CURRENT_TIMESTAMP - INTERVAL '36 days', CURRENT_TIMESTAMP - INTERVAL '36 days', false, NULL),
('00000000-0000-0000-0000-000000000006', 'liam.russo@example.com', 'Liam Russo', 'MEMBER', 'SUSPENDED', 'Florence', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '35 days', false, NULL),
('00000000-0000-0000-0000-000000000007', 'emma.ricci@example.com', 'Emma Ricci', 'MANAGER', 'ACTIVE', 'Venice', CURRENT_TIMESTAMP - INTERVAL '34 days', CURRENT_TIMESTAMP - INTERVAL '34 days', false, NULL),
('00000000-0000-0000-0000-000000000008', 'oliver.moretti@example.com', 'Oliver Moretti', 'VIEWER', 'INVITED', 'Genoa', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '33 days', false, NULL),
('00000000-0000-0000-0000-000000000009', 'giulia.galli@example.com', 'Giulia Galli', 'MEMBER', 'ACTIVE', 'Palermo', CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '32 days', false, NULL),
('00000000-0000-0000-0000-000000000010', 'ethan.lombardi@example.com', 'Ethan Lombardi', 'ADMIN', 'ACTIVE', 'Rome', CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '31 days', false, NULL),
('00000000-0000-0000-0000-000000000011', 'isabella.costa@example.com', 'Isabella Costa', 'MEMBER', 'ACTIVE', 'Milan', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days', false, NULL),
('00000000-0000-0000-0000-000000000012', 'lucas.bruno@example.com', 'Lucas Bruno', 'VIEWER', 'SUSPENDED', 'Turin', CURRENT_TIMESTAMP - INTERVAL '29 days', CURRENT_TIMESTAMP - INTERVAL '29 days', false, NULL),
('00000000-0000-0000-0000-000000000013', 'amelia.romano@example.com', 'Amelia Romano', 'MANAGER', 'ACTIVE', 'Naples', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days', false, NULL),
('00000000-0000-0000-0000-000000000014', 'matteo.mancini@example.com', 'Matteo Mancini', 'MEMBER', 'INVITED', 'Bologna', CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '27 days', false, NULL),
('00000000-0000-0000-0000-000000000015', 'charlotte.marino@example.com', 'Charlotte Marino', 'VIEWER', 'ACTIVE', 'Florence', CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '26 days', false, NULL),
('00000000-0000-0000-0000-000000000016', 'jacob.leone@example.com', 'Jacob Leone', 'MEMBER', 'ACTIVE', 'Venice', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days', false, NULL),
('00000000-0000-0000-0000-000000000017', 'harper.colombo@example.com', 'Harper Colombo', 'MANAGER', 'SUSPENDED', 'Genoa', CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '24 days', false, NULL),
('00000000-0000-0000-0000-000000000018', 'daniel.giordano@example.com', 'Daniel Giordano', 'MEMBER', 'ACTIVE', 'Palermo', CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days', false, NULL),
('00000000-0000-0000-0000-000000000019', 'evelyn.rizzo@example.com', 'Evelyn Rizzo', 'VIEWER', 'INVITED', 'Rome', CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '22 days', false, NULL),
('00000000-0000-0000-0000-000000000020', 'benjamin.fontana@example.com', 'Benjamin Fontana', 'MEMBER', 'ACTIVE', 'Milan', CURRENT_TIMESTAMP - INTERVAL '21 days', CURRENT_TIMESTAMP - INTERVAL '21 days', false, NULL),
('00000000-0000-0000-0000-000000000021', 'luna.bianchi@example.com', 'Luna Bianchi', 'ADMIN', 'ACTIVE', 'Turin', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', false, NULL),
('00000000-0000-0000-0000-000000000022', 'samuel.esposito@example.com', 'Samuel Esposito', 'MEMBER', 'SUSPENDED', 'Naples', CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '19 days', false, NULL),
('00000000-0000-0000-0000-000000000023', 'aria.vitale@example.com', 'Aria Vitale', 'MANAGER', 'ACTIVE', 'Bologna', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days', false, NULL),
('00000000-0000-0000-0000-000000000024', 'henry.santoro@example.com', 'Henry Santoro', 'VIEWER', 'ACTIVE', 'Florence', CURRENT_TIMESTAMP - INTERVAL '17 days', CURRENT_TIMESTAMP - INTERVAL '17 days', false, NULL),
('00000000-0000-0000-0000-000000000025', 'grace.martini@example.com', 'Grace Martini', 'MEMBER', 'INVITED', 'Venice', CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days', false, NULL),
('00000000-0000-0000-0000-000000000026', 'alexander.longo@example.com', 'Alexander Longo', 'MEMBER', 'ACTIVE', 'Genoa', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days', false, NULL),
('00000000-0000-0000-0000-000000000027', 'ella.barbieri@example.com', 'Ella Barbieri', 'MANAGER', 'ACTIVE', 'Palermo', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days', false, NULL),
('00000000-0000-0000-0000-000000000028', 'sebastian.farina@example.com', 'Sebastian Farina', 'VIEWER', 'SUSPENDED', 'Rome', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '13 days', false, NULL),
('00000000-0000-0000-0000-000000000029', 'victoria.rinaldi@example.com', 'Victoria Rinaldi', 'MEMBER', 'ACTIVE', 'Milan', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days', false, NULL),
('00000000-0000-0000-0000-000000000030', 'jack.caruso@example.com', 'Jack Caruso', 'ADMIN', 'ACTIVE', 'Turin', CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days', false, NULL),
('00000000-0000-0000-0000-000000000031', 'scarlett.amato@example.com', 'Scarlett Amato', 'MEMBER', 'INVITED', 'Naples', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days', false, NULL),
('00000000-0000-0000-0000-000000000032', 'william.sala@example.com', 'William Sala', 'VIEWER', 'ACTIVE', 'Bologna', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days', false, NULL),
('00000000-0000-0000-0000-000000000033', 'zoe.riva@example.com', 'Zoe Riva', 'MANAGER', 'ACTIVE', 'Florence', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days', false, NULL),
('00000000-0000-0000-0000-000000000034', 'james.guerra@example.com', 'James Guerra', 'MEMBER', 'SUSPENDED', 'Venice', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '7 days', false, NULL),
('00000000-0000-0000-0000-000000000035', 'nora.damico@example.com', 'Nora D''Amico', 'VIEWER', 'ACTIVE', 'Genoa', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '6 days', false, NULL),
('00000000-0000-0000-0000-000000000036', 'michael.pellegrini@example.com', 'Michael Pellegrini', 'MEMBER', 'ACTIVE', 'Palermo', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days', false, NULL),
('00000000-0000-0000-0000-000000000037', 'chloe.fiore@example.com', 'Chloe Fiore', 'MANAGER', 'INVITED', 'Rome', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days', false, NULL),
('00000000-0000-0000-0000-000000000038', 'david.grassi@example.com', 'David Grassi', 'VIEWER', 'ACTIVE', 'Milan', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', false, NULL),
('00000000-0000-0000-0000-000000000039', 'layla.basile@example.com', 'Layla Basile', 'MEMBER', 'ACTIVE', 'Turin', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', false, NULL),
('00000000-0000-0000-0000-000000000040', 'andrea.palumbo@example.com', 'Andrea Palumbo', 'ADMIN', 'ACTIVE', 'Naples', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day', false, NULL);
