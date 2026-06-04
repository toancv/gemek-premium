-- =============================================================================
-- scripts/seed-demo-local.sql
-- Demo seed data for local manual testing — deploy/local branch
--
-- Idempotency technique: fixed UUID primary keys + INSERT ... ON CONFLICT (id) DO NOTHING
--   Exception: amenity_bookings rows use WHERE NOT EXISTS (amenity_id resolved by name subquery)
--
-- UUID namespace (all hex-valid, no collisions with gen_random_uuid()):
--   0001-xxxx = users, 0002-xxxx = blocks, 0003-xxxx = apartments
--   0004-xxxx = residents, 0005-xxxx = vehicles, 0006-xxxx = contractors
--   0007-xxxx = contracts, 0008-xxxx = tickets, 0009-xxxx = bookings
--   0010-xxxx = announcements
--
-- BCrypt hash for 'Demo@2026' (BCryptPasswordEncoder strength=12):
--   $2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za
--   Verified: length=60, prefix=$2a$12
--
-- DO NOT add to Flyway migrations or any production deployment.
-- Pre-requisite: fresh DB (docker compose down -v && docker compose up -d) if state is dirty.
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. USERS — 1 TECHNICIAN + 25 RESIDENTS  (26 rows)
-- ---------------------------------------------------------------------------
INSERT INTO users (id, email, phone, full_name, password_hash, role, is_active)
VALUES
  ('00000000-0000-0000-0001-000000000000','tech01@demo.local',     '0901000000','Nguyen Van Ky Thuat', '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','TECHNICIAN',TRUE),
  ('00000000-0000-0000-0001-000000000001','resident01@demo.local', '0901000001','Tran Thi An',         '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000002','resident02@demo.local', '0901000002','Le Van Binh',         '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000003','resident03@demo.local', '0901000003','Pham Thi Cam',        '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000004','resident04@demo.local', '0901000004','Hoang Van Dung',      '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000005','resident05@demo.local', '0901000005','Nguyen Thi Em',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000006','resident06@demo.local', '0901000006','Bui Van Phong',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000007','resident07@demo.local', '0901000007','Vu Thi Giang',        '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000008','resident08@demo.local', '0901000008','Do Van Hai',          '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000009','resident09@demo.local', '0901000009','Cao Thi Huong',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000010','resident10@demo.local', '0901000010','Nguyen Van Khoa',     '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000011','resident11@demo.local', '0901000011','Tran Thi Lan',        '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000012','resident12@demo.local', '0901000012','Le Van Mai',          '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000013','resident13@demo.local', '0901000013','Pham Thi Ngoc',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000014','resident14@demo.local', '0901000014','Hoang Van Oanh',      '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000015','resident15@demo.local', '0901000015','Nguyen Thi Phuong',   '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000016','resident16@demo.local', '0901000016','Bui Van Quang',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000017','resident17@demo.local', '0901000017','Vu Thi Thanh Huong',  '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000018','resident18@demo.local', '0901000018','Do Van Son',          '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000019','resident19@demo.local', '0901000019','Cao Thi Thu',         '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000020','resident20@demo.local', '0901000020','Nguyen Van Uong',     '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000021','resident21@demo.local', '0901000021','Tran Thi Viet',       '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000022','resident22@demo.local', '0901000022','Le Van Xuan',         '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000023','resident23@demo.local', '0901000023','Pham Thi Yen',        '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000024','resident24@demo.local', '0901000024','Hoang Van Phuc',      '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE),
  ('00000000-0000-0000-0001-000000000025','resident25@demo.local', '0901000025','Nguyen Thi Quynh',    '$2a$12$xm6CtEn9XdGc7Wkw3URZke61hY4PVsBLDZ.zq.zSe.Zenlex0j5za','RESIDENT',TRUE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. BLOCKS (3)
-- ---------------------------------------------------------------------------
INSERT INTO blocks (id, name, description)
VALUES
  ('00000000-0000-0000-0002-000000000001','Block A','Tower A — east wing, floors 1–10'),
  ('00000000-0000-0000-0002-000000000002','Block B','Tower B — west wing, floors 1–10'),
  ('00000000-0000-0000-0002-000000000003','Block C','Tower C — north wing, floors 1–8')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. APARTMENTS (30)
--    Status mix: 20 OCCUPIED, 7 AVAILABLE, 3 MAINTENANCE
-- ---------------------------------------------------------------------------
INSERT INTO apartments (id, block_id, floor, unit_number, area_sqm, status)
VALUES
  -- Block A (10)
  ('00000000-0000-0000-0003-000000000001','00000000-0000-0000-0002-000000000001',1,'A101', 75.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000002','00000000-0000-0000-0002-000000000001',1,'A102', 75.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000003','00000000-0000-0000-0002-000000000001',2,'A201', 90.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000004','00000000-0000-0000-0002-000000000001',2,'A202', 90.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000005','00000000-0000-0000-0002-000000000001',3,'A301', 85.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000006','00000000-0000-0000-0002-000000000001',3,'A302', 85.00,'MAINTENANCE'),
  ('00000000-0000-0000-0003-000000000007','00000000-0000-0000-0002-000000000001',4,'A401',100.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000008','00000000-0000-0000-0002-000000000001',4,'A402',100.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000009','00000000-0000-0000-0002-000000000001',5,'A501',110.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000010','00000000-0000-0000-0002-000000000001',5,'A502', 75.00,'OCCUPIED'),
  -- Block B (10)
  ('00000000-0000-0000-0003-000000000011','00000000-0000-0000-0002-000000000002',1,'B101', 80.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000012','00000000-0000-0000-0002-000000000002',1,'B102', 80.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000013','00000000-0000-0000-0002-000000000002',2,'B201', 95.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000014','00000000-0000-0000-0002-000000000002',2,'B202', 95.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000015','00000000-0000-0000-0002-000000000002',3,'B301', 88.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000016','00000000-0000-0000-0002-000000000002',3,'B302', 88.00,'MAINTENANCE'),
  ('00000000-0000-0000-0003-000000000017','00000000-0000-0000-0002-000000000002',4,'B401',105.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000018','00000000-0000-0000-0002-000000000002',4,'B402',105.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000019','00000000-0000-0000-0002-000000000002',5,'B501', 78.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000020','00000000-0000-0000-0002-000000000002',5,'B502', 78.00,'AVAILABLE'),
  -- Block C (10)
  ('00000000-0000-0000-0003-000000000021','00000000-0000-0000-0002-000000000003',1,'C101', 82.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000022','00000000-0000-0000-0002-000000000003',1,'C102', 82.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000023','00000000-0000-0000-0002-000000000003',2,'C201', 92.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000024','00000000-0000-0000-0002-000000000003',2,'C202', 92.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000025','00000000-0000-0000-0002-000000000003',3,'C301', 86.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000026','00000000-0000-0000-0002-000000000003',3,'C302', 86.00,'MAINTENANCE'),
  ('00000000-0000-0000-0003-000000000027','00000000-0000-0000-0002-000000000003',4,'C401', 98.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000028','00000000-0000-0000-0002-000000000003',4,'C402', 98.00,'AVAILABLE'),
  ('00000000-0000-0000-0003-000000000029','00000000-0000-0000-0002-000000000003',5,'C501',112.00,'OCCUPIED'),
  ('00000000-0000-0000-0003-000000000030','00000000-0000-0000-0002-000000000003',5,'C502', 72.00,'OCCUPIED')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. RESIDENTS (25)
--    Apartments with 2 residents (1 OWNER + 1 TENANT): apt03, apt07, apt11, apt13, apt23
--    uq_residents_active_user enforced: each user_id appears at most once (move_out_date IS NULL)
-- ---------------------------------------------------------------------------
INSERT INTO residents (id, user_id, apartment_id, type, move_in_date, is_primary_contact)
VALUES
  ('00000000-0000-0000-0004-000000000001','00000000-0000-0000-0001-000000000001','00000000-0000-0000-0003-000000000001','OWNER', '2024-01-15',TRUE),
  ('00000000-0000-0000-0004-000000000002','00000000-0000-0000-0001-000000000002','00000000-0000-0000-0003-000000000003','OWNER', '2024-02-01',TRUE),
  ('00000000-0000-0000-0004-000000000003','00000000-0000-0000-0001-000000000003','00000000-0000-0000-0003-000000000003','TENANT','2024-03-01',FALSE),
  ('00000000-0000-0000-0004-000000000004','00000000-0000-0000-0001-000000000004','00000000-0000-0000-0003-000000000004','OWNER', '2024-01-20',TRUE),
  ('00000000-0000-0000-0004-000000000005','00000000-0000-0000-0001-000000000005','00000000-0000-0000-0003-000000000005','OWNER', '2024-04-10',TRUE),
  ('00000000-0000-0000-0004-000000000006','00000000-0000-0000-0001-000000000006','00000000-0000-0000-0003-000000000007','OWNER', '2023-11-01',TRUE),
  ('00000000-0000-0000-0004-000000000007','00000000-0000-0000-0001-000000000007','00000000-0000-0000-0003-000000000007','TENANT','2024-05-15',FALSE),
  ('00000000-0000-0000-0004-000000000008','00000000-0000-0000-0001-000000000008','00000000-0000-0000-0003-000000000009','OWNER', '2023-09-01',TRUE),
  ('00000000-0000-0000-0004-000000000009','00000000-0000-0000-0001-000000000009','00000000-0000-0000-0003-000000000010','OWNER', '2024-06-01',TRUE),
  ('00000000-0000-0000-0004-000000000010','00000000-0000-0000-0001-000000000010','00000000-0000-0000-0003-000000000011','OWNER', '2023-08-15',TRUE),
  ('00000000-0000-0000-0004-000000000011','00000000-0000-0000-0001-000000000011','00000000-0000-0000-0003-000000000011','TENANT','2024-01-01',FALSE),
  ('00000000-0000-0000-0004-000000000012','00000000-0000-0000-0001-000000000012','00000000-0000-0000-0003-000000000013','OWNER', '2024-03-15',TRUE),
  ('00000000-0000-0000-0004-000000000013','00000000-0000-0000-0001-000000000013','00000000-0000-0000-0003-000000000013','TENANT','2024-04-01',FALSE),
  ('00000000-0000-0000-0004-000000000014','00000000-0000-0000-0001-000000000014','00000000-0000-0000-0003-000000000014','OWNER', '2023-12-01',TRUE),
  ('00000000-0000-0000-0004-000000000015','00000000-0000-0000-0001-000000000015','00000000-0000-0000-0003-000000000015','OWNER', '2024-02-15',TRUE),
  ('00000000-0000-0000-0004-000000000016','00000000-0000-0000-0001-000000000016','00000000-0000-0000-0003-000000000017','OWNER', '2024-01-10',TRUE),
  ('00000000-0000-0000-0004-000000000017','00000000-0000-0000-0001-000000000017','00000000-0000-0000-0003-000000000019','OWNER', '2024-07-01',TRUE),
  ('00000000-0000-0000-0004-000000000018','00000000-0000-0000-0001-000000000018','00000000-0000-0000-0003-000000000021','OWNER', '2024-05-20',TRUE),
  ('00000000-0000-0000-0004-000000000019','00000000-0000-0000-0001-000000000019','00000000-0000-0000-0003-000000000023','OWNER', '2023-10-01',TRUE),
  ('00000000-0000-0000-0004-000000000020','00000000-0000-0000-0001-000000000020','00000000-0000-0000-0003-000000000023','TENANT','2024-08-01',FALSE),
  ('00000000-0000-0000-0004-000000000021','00000000-0000-0000-0001-000000000021','00000000-0000-0000-0003-000000000024','OWNER', '2024-06-15',TRUE),
  ('00000000-0000-0000-0004-000000000022','00000000-0000-0000-0001-000000000022','00000000-0000-0000-0003-000000000025','OWNER', '2023-07-01',TRUE),
  ('00000000-0000-0000-0004-000000000023','00000000-0000-0000-0001-000000000023','00000000-0000-0000-0003-000000000027','OWNER', '2024-09-01',TRUE),
  ('00000000-0000-0000-0004-000000000024','00000000-0000-0000-0001-000000000024','00000000-0000-0000-0003-000000000029','OWNER', '2024-04-20',TRUE),
  ('00000000-0000-0000-0004-000000000025','00000000-0000-0000-0001-000000000025','00000000-0000-0000-0003-000000000030','OWNER', '2024-11-01',TRUE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. VEHICLES (8)
-- ---------------------------------------------------------------------------
INSERT INTO vehicles (id, resident_id, apartment_id, type, license_plate, brand, model, color, is_active)
VALUES
  ('00000000-0000-0000-0005-000000000001','00000000-0000-0000-0004-000000000001','00000000-0000-0000-0003-000000000001','CAR',      '51A-12345', 'Toyota', 'Camry',    'White', TRUE),
  ('00000000-0000-0000-0005-000000000002','00000000-0000-0000-0004-000000000002','00000000-0000-0000-0003-000000000003','MOTORBIKE', '51B-67890', 'Honda',  'Wave',     'Black', TRUE),
  ('00000000-0000-0000-0005-000000000003','00000000-0000-0000-0004-000000000006','00000000-0000-0000-0003-000000000007','CAR',      '29A-11111', 'Mazda',  'CX-5',     'Silver',TRUE),
  ('00000000-0000-0000-0005-000000000004','00000000-0000-0000-0004-000000000010','00000000-0000-0000-0003-000000000011','MOTORBIKE', '43A-22222', 'Yamaha', 'Exciter',  'Red',   TRUE),
  ('00000000-0000-0000-0005-000000000005','00000000-0000-0000-0004-000000000012','00000000-0000-0000-0003-000000000013','CAR',      '51C-33333', 'Hyundai','Tucson',    'Blue',  TRUE),
  ('00000000-0000-0000-0005-000000000006','00000000-0000-0000-0004-000000000018','00000000-0000-0000-0003-000000000021','MOTORBIKE', '51H-44444', 'Honda',  'SH',       'Gray',  TRUE),
  ('00000000-0000-0000-0005-000000000007','00000000-0000-0000-0004-000000000024','00000000-0000-0000-0003-000000000029','CAR',      '51D-55555', 'VinFast','VF8',       'White', TRUE),
  ('00000000-0000-0000-0005-000000000008','00000000-0000-0000-0004-000000000008','00000000-0000-0000-0003-000000000009','BICYCLE',  'BICYCLE-001',NULL,    'City Bike', 'Green', TRUE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. CONTRACTORS (3)
-- ---------------------------------------------------------------------------
INSERT INTO contractors (id, company_name, contact_person, phone, email, specialty, tax_code, rating, is_active)
VALUES
  ('00000000-0000-0000-0006-000000000001','Viet Dien Electric JSC', 'Nguyen Minh Duc','0281000001','vietdien@contractor.local','ELECTRICAL','0300123001',4.50,TRUE),
  ('00000000-0000-0000-0006-000000000002','Phuc Gia Plumbing Co.',  'Tran Van Hieu',  '0281000002','phucgia@contractor.local', 'PLUMBING',  '0300123002',4.20,TRUE),
  ('00000000-0000-0000-0006-000000000003','Sach Dep Cleaning Svc',  'Le Thi Bich',    '0281000003','sachdep@contractor.local', 'CLEANING',  '0300123003',4.80,TRUE)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. CONTRACTS (2)
-- ---------------------------------------------------------------------------
INSERT INTO contracts (id, contractor_id, title, scope, contract_value, currency, start_date, end_date, status, created_by_user_id)
VALUES
  ('00000000-0000-0000-0007-000000000001',
   '00000000-0000-0000-0006-000000000001',
   'Annual Electrical Maintenance 2026',
   'Routine inspection and repair of all electrical systems in Blocks A, B, C',
   120000000,'VND','2026-01-01','2026-12-31','ACTIVE',
   (SELECT id FROM users WHERE email = 'admin@gemek.vn')),
  ('00000000-0000-0000-0007-000000000002',
   '00000000-0000-0000-0006-000000000003',
   'Cleaning Services Contract Q2-Q4 2026',
   'Daily common area cleaning: lobbies, corridors, parking, pool deck',
   85000000,'VND','2026-04-01','2026-12-31','ACTIVE',
   (SELECT id FROM users WHERE email = 'admin@gemek.vn'))
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. TICKETS (15) — all 5 categories × all 5 statuses
--    SLA per category: MAINTENANCE_REPAIR=24h, COMPLAINT=48h,
--                      ADMINISTRATIVE=72h, SUGGESTION_FEEDBACK=168h, OTHER=48h
--    chk_tickets_contractor_category: contractor only when category=MAINTENANCE_REPAIR
--    chk_tickets_single_assignee: not both assigned_to_user_id AND assigned_to_contractor_id
-- ---------------------------------------------------------------------------
INSERT INTO tickets (id, apartment_id, submitted_by_user_id, category, title, description,
                     status, priority, assigned_to_user_id, assigned_to_contractor_id,
                     sla_deadline, completed_date, rating, rating_comment)
VALUES
  -- MAINTENANCE_REPAIR / URGENT / IN_PROGRESS → contractor (Viet Dien)
  ('00000000-0000-0000-0008-000000000001',
   '00000000-0000-0000-0003-000000000001','00000000-0000-0000-0001-000000000001',
   'MAINTENANCE_REPAIR','Power outlet sparking in bedroom',
   'Socket in master bedroom sparks when plugged in — fire risk.',
   'IN_PROGRESS','URGENT',NULL,'00000000-0000-0000-0006-000000000001',
   NOW() - INTERVAL '1 day' + INTERVAL '24 hours',NULL,NULL,NULL),

  -- MAINTENANCE_REPAIR / HIGH / ASSIGNED → technician
  ('00000000-0000-0000-0008-000000000002',
   '00000000-0000-0000-0003-000000000003','00000000-0000-0000-0001-000000000002',
   'MAINTENANCE_REPAIR','Air conditioner not cooling',
   'AC unit in living room runs but does not cool below 28°C.',
   'ASSIGNED','HIGH','00000000-0000-0000-0001-000000000000',NULL,
   NOW() - INTERVAL '12 hours' + INTERVAL '24 hours',NULL,NULL,NULL),

  -- MAINTENANCE_REPAIR / MEDIUM / DONE → contractor (Phuc Gia), rated 5
  ('00000000-0000-0000-0008-000000000003',
   '00000000-0000-0000-0003-000000000007','00000000-0000-0000-0001-000000000006',
   'MAINTENANCE_REPAIR','Leaking pipe under kitchen sink',
   'Water dripping steadily from U-bend — dampness causing cabinet damage.',
   'DONE','MEDIUM',NULL,'00000000-0000-0000-0006-000000000002',
   NOW() - INTERVAL '5 days' + INTERVAL '24 hours',
   NOW() - INTERVAL '3 days',5,'Fixed perfectly and cleaned up after — very professional.'),

  -- MAINTENANCE_REPAIR / LOW / CANCELLED → no assignee
  ('00000000-0000-0000-0008-000000000004',
   '00000000-0000-0000-0003-000000000011','00000000-0000-0000-0001-000000000010',
   'MAINTENANCE_REPAIR','Squeaky door hinge',
   'Front door hinge squeaks. Resident later fixed it themselves.',
   'CANCELLED','LOW',NULL,NULL,
   NOW() - INTERVAL '10 days' + INTERVAL '24 hours',NULL,NULL,NULL),

  -- MAINTENANCE_REPAIR / MEDIUM / NEW → unassigned
  ('00000000-0000-0000-0008-000000000005',
   '00000000-0000-0000-0003-000000000013','00000000-0000-0000-0001-000000000012',
   'MAINTENANCE_REPAIR','Bathroom exhaust fan broken',
   'Fan does not spin; bathroom gets very humid after shower.',
   'NEW','MEDIUM',NULL,NULL,
   NOW() + INTERVAL '24 hours',NULL,NULL,NULL),

  -- COMPLAINT / HIGH / IN_PROGRESS → technician
  ('00000000-0000-0000-0008-000000000006',
   '00000000-0000-0000-0003-000000000004','00000000-0000-0000-0001-000000000004',
   'COMPLAINT','Noise from upstairs apartment after 11 PM',
   'Loud music and stomping from A202 every weekend night after 11 PM.',
   'IN_PROGRESS','HIGH','00000000-0000-0000-0001-000000000000',NULL,
   NOW() - INTERVAL '2 days' + INTERVAL '48 hours',NULL,NULL,NULL),

  -- COMPLAINT / MEDIUM / DONE → technician, rated 4
  ('00000000-0000-0000-0008-000000000007',
   '00000000-0000-0000-0003-000000000005','00000000-0000-0000-0001-000000000005',
   'COMPLAINT','Garbage left in corridor',
   'Bags of rubbish left outside unit A301 for over 24 hours.',
   'DONE','MEDIUM','00000000-0000-0000-0001-000000000000',NULL,
   NOW() - INTERVAL '7 days' + INTERVAL '48 hours',
   NOW() - INTERVAL '6 days',4,'Issue resolved quickly after the first notice.'),

  -- COMPLAINT / LOW / NEW → unassigned
  ('00000000-0000-0000-0008-000000000008',
   '00000000-0000-0000-0003-000000000009','00000000-0000-0000-0001-000000000008',
   'COMPLAINT','Elevator button sticking on floor 5',
   'B5 elevator call button requires hard press to register.',
   'NEW','LOW',NULL,NULL,
   NOW() + INTERVAL '48 hours',NULL,NULL,NULL),

  -- ADMINISTRATIVE / MEDIUM / ASSIGNED → technician
  ('00000000-0000-0000-0008-000000000009',
   '00000000-0000-0000-0003-000000000010','00000000-0000-0000-0001-000000000009',
   'ADMINISTRATIVE','Request for parking card replacement',
   'Lost parking card — request issuance of replacement for slot B1-045.',
   'ASSIGNED','MEDIUM','00000000-0000-0000-0001-000000000000',NULL,
   NOW() + INTERVAL '72 hours',NULL,NULL,NULL),

  -- ADMINISTRATIVE / LOW / CANCELLED
  ('00000000-0000-0000-0008-000000000010',
   '00000000-0000-0000-0003-000000000014','00000000-0000-0000-0001-000000000014',
   'ADMINISTRATIVE','Change registered vehicle license plate',
   'Need to update vehicle record from old plate to new plate after renewal.',
   'CANCELLED','LOW',NULL,NULL,
   NOW() - INTERVAL '15 days' + INTERVAL '72 hours',NULL,NULL,NULL),

  -- SUGGESTION_FEEDBACK / LOW / NEW → no assignee (admin review queue)
  ('00000000-0000-0000-0008-000000000011',
   '00000000-0000-0000-0003-000000000015','00000000-0000-0000-0001-000000000015',
   'SUGGESTION_FEEDBACK','Suggestion: add EV charging stations in basement',
   'Many residents now own EVs. Requesting 4-6 charging points in basement parking.',
   'NEW','LOW',NULL,NULL,
   NOW() + INTERVAL '168 hours',NULL,NULL,NULL),

  -- SUGGESTION_FEEDBACK / LOW / IN_PROGRESS → technician (review in progress)
  ('00000000-0000-0000-0008-000000000012',
   '00000000-0000-0000-0003-000000000017','00000000-0000-0000-0001-000000000016',
   'SUGGESTION_FEEDBACK','Suggestion: dedicated bicycle parking cage',
   'No secure bicycle storage. Requesting a locked cage near Block B entrance.',
   'IN_PROGRESS','LOW','00000000-0000-0000-0001-000000000000',NULL,
   NOW() - INTERVAL '5 days' + INTERVAL '168 hours',NULL,NULL,NULL),

  -- OTHER / MEDIUM / ASSIGNED → technician
  ('00000000-0000-0000-0008-000000000013',
   '00000000-0000-0000-0003-000000000019','00000000-0000-0000-0001-000000000017',
   'OTHER','Lost access card — need temporary badge',
   'Resident locked out. Needs a temporary access card until permanent is issued.',
   'ASSIGNED','MEDIUM','00000000-0000-0000-0001-000000000000',NULL,
   NOW() + INTERVAL '48 hours',NULL,NULL,NULL),

  -- OTHER / LOW / NEW → unassigned
  ('00000000-0000-0000-0008-000000000014',
   '00000000-0000-0000-0003-000000000021','00000000-0000-0000-0001-000000000018',
   'OTHER','Query about move-in checklist documents',
   'First-time resident unsure which documents to submit for move-in registration.',
   'NEW','LOW',NULL,NULL,
   NOW() + INTERVAL '48 hours',NULL,NULL,NULL),

  -- OTHER / LOW / DONE → technician, rated 3
  ('00000000-0000-0000-0008-000000000015',
   '00000000-0000-0000-0003-000000000023','00000000-0000-0000-0001-000000000019',
   'OTHER','Mailbox key not provided at move-in',
   'Unit C201 mailbox key was missing from handover kit.',
   'DONE','LOW','00000000-0000-0000-0001-000000000000',NULL,
   NOW() - INTERVAL '20 days' + INTERVAL '48 hours',
   NOW() - INTERVAL '18 days',3,'Took longer than expected but was eventually resolved.')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. AMENITY BOOKINGS (5)
--    resident_id → residents.id  |  apartment_id must match resident's apartment
--    Amenity IDs resolved by name (seeded by Flyway V3 migration)
--    Idempotency: WHERE NOT EXISTS on fixed booking UUID
-- ---------------------------------------------------------------------------
INSERT INTO amenity_bookings (id, amenity_id, resident_id, apartment_id, booking_date, start_time, end_time, status)
SELECT '00000000-0000-0000-0009-000000000001',
       (SELECT id FROM amenities WHERE name = 'Gym / Fitness Center'),
       '00000000-0000-0000-0004-000000000001','00000000-0000-0000-0003-000000000001',
       '2026-06-10','07:00','08:00','PENDING'
WHERE NOT EXISTS (SELECT 1 FROM amenity_bookings WHERE id = '00000000-0000-0000-0009-000000000001');

INSERT INTO amenity_bookings (id, amenity_id, resident_id, apartment_id, booking_date, start_time, end_time, status, approved_by_user_id, approved_at)
SELECT '00000000-0000-0000-0009-000000000002',
       (SELECT id FROM amenities WHERE name = 'Swimming Pool'),
       '00000000-0000-0000-0004-000000000002','00000000-0000-0000-0003-000000000003',
       '2026-06-11','08:00','09:30','APPROVED',
       (SELECT id FROM users WHERE email = 'admin@gemek.vn'), NOW() - INTERVAL '2 days'
WHERE NOT EXISTS (SELECT 1 FROM amenity_bookings WHERE id = '00000000-0000-0000-0009-000000000002');

INSERT INTO amenity_bookings (id, amenity_id, resident_id, apartment_id, booking_date, start_time, end_time, status, approved_by_user_id, approved_at)
SELECT '00000000-0000-0000-0009-000000000003',
       (SELECT id FROM amenities WHERE name = 'BBQ Area'),
       '00000000-0000-0000-0004-000000000006','00000000-0000-0000-0003-000000000007',
       '2026-05-25','17:00','21:00','COMPLETED',
       (SELECT id FROM users WHERE email = 'admin@gemek.vn'), NOW() - INTERVAL '12 days'
WHERE NOT EXISTS (SELECT 1 FROM amenity_bookings WHERE id = '00000000-0000-0000-0009-000000000003');

INSERT INTO amenity_bookings (id, amenity_id, resident_id, apartment_id, booking_date, start_time, end_time, status, rejection_reason)
SELECT '00000000-0000-0000-0009-000000000004',
       (SELECT id FROM amenities WHERE name = 'Meeting Room A'),
       '00000000-0000-0000-0004-000000000012','00000000-0000-0000-0003-000000000013',
       '2026-06-08','10:00','12:00','REJECTED',
       'Slot already reserved for building management meeting.'
WHERE NOT EXISTS (SELECT 1 FROM amenity_bookings WHERE id = '00000000-0000-0000-0009-000000000004');

INSERT INTO amenity_bookings (id, amenity_id, resident_id, apartment_id, booking_date, start_time, end_time, status)
SELECT '00000000-0000-0000-0009-000000000005',
       (SELECT id FROM amenities WHERE name = 'Swimming Pool'),
       '00000000-0000-0000-0004-000000000018','00000000-0000-0000-0003-000000000021',
       '2026-06-15','07:00','08:00','CANCELLED'
WHERE NOT EXISTS (SELECT 1 FROM amenity_bookings WHERE id = '00000000-0000-0000-0009-000000000005');

-- ---------------------------------------------------------------------------
-- 10. ANNOUNCEMENTS (2, published)
-- ---------------------------------------------------------------------------
INSERT INTO announcements (id, title, content, type, scope, target_block_id, send_push, created_by_user_id, published_at)
VALUES
  ('00000000-0000-0000-0010-000000000001',
   'Water Supply Interruption 2026-06-07',
   'Dear residents, water supply will be interrupted on Saturday 2026-06-07 from 08:00 to 12:00 for routine maintenance of the main pump. Please store adequate water in advance. We apologise for the inconvenience.',
   'MAINTENANCE','ALL',NULL,TRUE,
   (SELECT id FROM users WHERE email = 'admin@gemek.vn'),
   NOW() - INTERVAL '1 day'),
  ('00000000-0000-0000-0010-000000000002',
   'Block A: Lift Maintenance 2026-06-09',
   'The passenger lift in Block A will be under scheduled maintenance on Monday 2026-06-09 from 09:00 to 11:00. Please use the stairs or the freight lift during this window.',
   'MAINTENANCE','BLOCK','00000000-0000-0000-0002-000000000001',TRUE,
   (SELECT id FROM users WHERE email = 'admin@gemek.vn'),
   NOW() - INTERVAL '12 hours')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- VERIFICATION QUERIES (run manually after load to confirm counts)
-- ---------------------------------------------------------------------------
-- SELECT 'demo_users'       , COUNT(*) FROM users            WHERE email LIKE '%@demo.local';
-- SELECT 'blocks'           , COUNT(*) FROM blocks           WHERE name IN ('Block A','Block B','Block C');
-- SELECT 'apartments'       , COUNT(*) FROM apartments       WHERE id::text LIKE '00000000-0000-0000-0003%';
-- SELECT 'residents'        , COUNT(*) FROM residents        WHERE id::text LIKE '00000000-0000-0000-0004%';
-- SELECT 'vehicles'         , COUNT(*) FROM vehicles         WHERE id::text LIKE '00000000-0000-0000-0005%';
-- SELECT 'contractors'      , COUNT(*) FROM contractors      WHERE id::text LIKE '00000000-0000-0000-0006%';
-- SELECT 'contracts'        , COUNT(*) FROM contracts        WHERE id::text LIKE '00000000-0000-0000-0007%';
-- SELECT 'tickets'          , COUNT(*) FROM tickets          WHERE id::text LIKE '00000000-0000-0000-0008%';
-- SELECT 'amenity_bookings' , COUNT(*) FROM amenity_bookings WHERE id::text LIKE '00000000-0000-0000-0009%';
-- SELECT 'announcements'    , COUNT(*) FROM announcements    WHERE id::text LIKE '00000000-0000-0000-0010%';
