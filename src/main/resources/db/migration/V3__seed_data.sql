-- Demo seed data so the app is immediately explorable.
-- One city, two movies, a theater with one screen, 20 seats, and one active show
-- whose show_seats are all AVAILABLE. Timestamps use now() to satisfy NOT NULL audit cols.

INSERT INTO cities (id, name, state, created_at, updated_at)
VALUES (1, 'Bengaluru', 'Karnataka', now(), now());

INSERT INTO users (id, email, password_hash, first_name, last_name, phone, created_at, updated_at)
VALUES (1, 'demo@bms.com',
        -- BCrypt hash of "password"
        '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Fx5tXtHV5Q0dGqYbUeZ8dQ9F5nq',
        'Demo', 'User', '9999999999', now(), now());

INSERT INTO movies (id, title, description, duration_minutes, genre, language, release_date,
                    rating, poster_url, trailer_url, is_active, created_at, updated_at)
VALUES (1, 'Avengers: Endgame', 'The Avengers assemble once more.', 181, 'Action', 'English',
        DATE '2019-04-26', 'PG-13', NULL, NULL, TRUE, now(), now()),
       (2, 'Inception', 'A thief who steals corporate secrets through dream-sharing.', 148,
        'Sci-Fi', 'English', DATE '2010-07-16', 'PG-13', NULL, NULL, TRUE, now(), now());

INSERT INTO theaters (id, name, city_id, address, latitude, longitude, total_screens,
                      created_at, updated_at)
VALUES (1, 'PVR Phoenix', 1, 'Whitefield, Bengaluru', 12.996560, 77.696370, 1, now(), now());

INSERT INTO screens (id, theater_id, name, total_seats, created_at, updated_at)
VALUES (1, 1, 'IMAX', 20, now(), now());

-- 20 seats: rows A and B, seats 1..10. Row B is PREMIUM.
INSERT INTO seats (id, screen_id, row_num, seat_number, seat_type, created_at, updated_at)
VALUES (1, 1, 'A', 1, 'REGULAR', now(), now()),
       (2, 1, 'A', 2, 'REGULAR', now(), now()),
       (3, 1, 'A', 3, 'REGULAR', now(), now()),
       (4, 1, 'A', 4, 'REGULAR', now(), now()),
       (5, 1, 'A', 5, 'REGULAR', now(), now()),
       (6, 1, 'A', 6, 'REGULAR', now(), now()),
       (7, 1, 'A', 7, 'REGULAR', now(), now()),
       (8, 1, 'A', 8, 'REGULAR', now(), now()),
       (9, 1, 'A', 9, 'REGULAR', now(), now()),
       (10, 1, 'A', 10, 'REGULAR', now(), now()),
       (11, 1, 'B', 1, 'PREMIUM', now(), now()),
       (12, 1, 'B', 2, 'PREMIUM', now(), now()),
       (13, 1, 'B', 3, 'PREMIUM', now(), now()),
       (14, 1, 'B', 4, 'PREMIUM', now(), now()),
       (15, 1, 'B', 5, 'PREMIUM', now(), now()),
       (16, 1, 'B', 6, 'PREMIUM', now(), now()),
       (17, 1, 'B', 7, 'PREMIUM', now(), now()),
       (18, 1, 'B', 8, 'PREMIUM', now(), now()),
       (19, 1, 'B', 9, 'PREMIUM', now(), now()),
       (20, 1, 'B', 10, 'PREMIUM', now(), now());

INSERT INTO shows (id, movie_id, screen_id, show_date, show_time, end_time, base_price,
                   available_seats, total_seats, status, created_at, updated_at)
VALUES (1, 1, 1, CURRENT_DATE, TIME '19:30', TIME '22:31', 250.00, 20, 20, 'ACTIVE',
        now(), now());

-- show_seats: REGULAR at base 250, PREMIUM at base+50 = 300, all AVAILABLE.
INSERT INTO show_seats (id, show_id, seat_id, price, status, locked_at, locked_by, booking_id,
                        version, created_at, updated_at)
SELECT s.id, 1, s.id,
       CASE WHEN s.seat_type = 'PREMIUM' THEN 300.00 ELSE 250.00 END,
       'AVAILABLE', NULL, NULL, NULL, 0, now(), now()
FROM seats s
WHERE s.screen_id = 1;

-- Keep IDENTITY sequences ahead of the manually-inserted ids.
SELECT setval(pg_get_serial_sequence('cities', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('users', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('movies', 'id'), 2, true);
SELECT setval(pg_get_serial_sequence('theaters', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('screens', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('seats', 'id'), 20, true);
SELECT setval(pg_get_serial_sequence('shows', 'id'), 1, true);
SELECT setval(pg_get_serial_sequence('show_seats', 'id'), 20, true);
