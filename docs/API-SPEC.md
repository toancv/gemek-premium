# API Specification — Apartment Management System

**Version:** 1.0  
**Date:** 2026-05-29  
**Base URL:** `https://{host}/api`  
**Auth:** Bearer token (JWT) in `Authorization: Bearer <token>` header, unless noted as Public.

---

## Global Conventions

### Standard Error Response

```json
{
  "error": "ERROR_CODE",
  "message": "Human readable description",
  "timestamp": "2026-05-29T10:00:00Z",
  "path": "/api/maintenance/requests"
}
```

Common error codes:

| Code | HTTP | Meaning |
|------|------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `FORBIDDEN` | 403 | Valid token but insufficient role |
| `NOT_FOUND` | 404 | Resource does not exist |
| `VALIDATION_ERROR` | 400 | Request body fails validation |
| `CONFLICT` | 409 | Duplicate / state conflict |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `RATE_LIMITED` | 429 | Too many requests |

### Standard Paginated Response

```json
{
  "data": [],
  "page": 0,
  "size": 20,
  "total": 100,
  "totalPages": 5
}
```

Pagination query params (all endpoints returning lists unless noted):
- `page` — 0-based page index (default: 0)
- `size` — page size (default: 20, max: 100)
- `sort` — field name (default varies per endpoint)
- `direction` — `asc` or `desc` (default: `desc`)

### Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Building manager — full control |
| `TECHNICIAN` | Internal maintenance staff — assigned tickets |
| `RESIDENT` | Apartment resident — personal portal |
| `BOARD_MEMBER` | Read-only reports and dashboard |

---

## 1. Auth

### POST /api/auth/login

**Auth:** Public  
**Description:** Authenticate user and receive tokens.

Request:
```json
{
  "email": "user@example.com",
  "password": "string"
}
```

Response `200 OK`:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "fullName": "Nguyen Van A",
    "role": "RESIDENT",
    "avatarUrl": null
  }
}
```

Errors: `401 UNAUTHORIZED` (wrong credentials), `400 VALIDATION_ERROR`

---

### POST /api/auth/refresh

**Auth:** Public (refresh token in body)  
**Description:** Exchange a valid refresh token for a new access token.

Request:
```json
{
  "refreshToken": "eyJ..."
}
```

Response `200 OK`:
```json
{
  "accessToken": "eyJ...",
  "expiresIn": 900
}
```

Errors: `401 UNAUTHORIZED` (expired/invalid refresh token)

---

### POST /api/auth/logout

**Auth:** Any authenticated role  
**Description:** Invalidate the current access token (added to Redis blocklist). Client should discard tokens.

Request: (no body)

Response `204 No Content`

---

### GET /api/auth/me

**Auth:** Any authenticated role  
**Description:** Return the current authenticated user's profile.

Response `200 OK`:
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "fullName": "Nguyen Van A",
  "phone": "0901234567",
  "role": "RESIDENT",
  "avatarUrl": "string|null",
  "isActive": true,
  "lastLoginAt": "2026-05-29T08:00:00Z"
}
```

---

### PUT /api/auth/me/password

**Auth:** Any authenticated role  
**Description:** Change own password.

Request:
```json
{
  "currentPassword": "string",
  "newPassword": "string (min 8 chars, must include upper, lower, digit)"
}
```

Response `204 No Content`

Errors: `400 VALIDATION_ERROR` (weak password), `401 UNAUTHORIZED` (wrong current password)

---

## 2. Users

### GET /api/users

**Auth:** ADMIN  
**Description:** List all users with optional filters.

Query params: `role`, `isActive` (bool), `search` (name/email substring)

Response `200 OK` — paginated list:
```json
{
  "data": [
    {
      "id": "uuid",
      "email": "string",
      "fullName": "string",
      "phone": "string|null",
      "role": "RESIDENT",
      "isActive": true,
      "createdAt": "ISO8601"
    }
  ],
  "page": 0, "size": 20, "total": 50, "totalPages": 3
}
```

---

### POST /api/users

**Auth:** ADMIN  
**Description:** Create a new user (staff or resident account).

Request:
```json
{
  "email": "string",
  "fullName": "string",
  "phone": "string|null",
  "role": "RESIDENT|TECHNICIAN|ADMIN|BOARD_MEMBER",
  "password": "string"
}
```

Response `201 Created`:
```json
{
  "id": "uuid",
  "email": "string",
  "fullName": "string",
  "role": "string",
  "isActive": true,
  "createdAt": "ISO8601"
}
```

Errors: `409 CONFLICT` (email already exists)

---

### GET /api/users/{id}

**Auth:** ADMIN  
**Description:** Get a single user by ID.

Response `200 OK` — same shape as single item from list.

---

### PUT /api/users/{id}

**Auth:** ADMIN  
**Description:** Update user profile or role.

Request:
```json
{
  "fullName": "string",
  "phone": "string|null",
  "role": "string",
  "isActive": true
}
```

Response `200 OK` — updated user object.

---

### DELETE /api/users/{id}

**Auth:** ADMIN  
**Description:** Deactivate (soft-delete) a user account. Sets `is_active = false`. Cannot delete users with active resident assignments.

Response `204 No Content`

Errors: `409 CONFLICT` (user has active resident assignment)

---

### PUT /api/users/{id}/reset-password

**Auth:** ADMIN  
**Description:** Admin resets another user's password.

Request:
```json
{
  "newPassword": "string"
}
```

Response `204 No Content`

---

## 3. Blocks & Apartments

### GET /api/blocks

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** List all blocks.

Response `200 OK`:
```json
{
  "data": [
    { "id": "uuid", "name": "Block A", "description": "string|null" }
  ]
}
```

---

### POST /api/blocks

**Auth:** ADMIN

Request:
```json
{ "name": "Block A", "description": "string|null" }
```

Response `201 Created` — block object.

---

### PUT /api/blocks/{id}

**Auth:** ADMIN  
Request/Response: same shape as POST.

---

### DELETE /api/blocks/{id}

**Auth:** ADMIN  
Response `204 No Content`  
Errors: `409 CONFLICT` (block has apartments)

---

### GET /api/apartments

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** List apartments with optional filters.

Query params: `blockId`, `floor`, `status`, `search` (unit number)

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "block": { "id": "uuid", "name": "Block A" },
      "floor": 3,
      "unitNumber": "A301",
      "areaSqm": 75.5,
      "status": "OCCUPIED",
      "currentResident": {
        "id": "uuid",
        "fullName": "string",
        "type": "OWNER",
        "phone": "string"
      }
    }
  ],
  "page": 0, "size": 20, "total": 200, "totalPages": 10
}
```

---

### POST /api/apartments

**Auth:** ADMIN

Request:
```json
{
  "blockId": "uuid",
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "notes": "string|null"
}
```

Response `201 Created` — apartment object.  
Errors: `409 CONFLICT` (unit number already exists in block)

---

### GET /api/apartments/{id}

**Auth:** ADMIN, BOARD_MEMBER, RESIDENT (own apartment only)  
**Description:** Full apartment detail including current residents and vehicles.

Response `200 OK`:
```json
{
  "id": "uuid",
  "block": { "id": "uuid", "name": "Block A" },
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "status": "OCCUPIED",
  "notes": "string|null",
  "residents": [
    {
      "id": "uuid",
      "user": { "id": "uuid", "fullName": "string", "phone": "string", "email": "string" },
      "type": "OWNER",
      "moveInDate": "2024-01-01",
      "moveOutDate": null,
      "isPrimaryContact": true
    }
  ],
  "vehicles": [
    {
      "id": "uuid",
      "licensePlate": "51A-123.45",
      "type": "CAR",
      "brand": "Toyota",
      "color": "White"
    }
  ]
}
```

---

### PUT /api/apartments/{id}

**Auth:** ADMIN

Request:
```json
{
  "floor": 3,
  "unitNumber": "A301",
  "areaSqm": 75.5,
  "status": "OCCUPIED",
  "notes": "string|null"
}
```

Response `200 OK` — updated apartment object.

---

### DELETE /api/apartments/{id}

**Auth:** ADMIN  
Response `204 No Content`  
Errors: `409 CONFLICT` (apartment has active residents)

---

## 4. Residents

### GET /api/residents

**Auth:** ADMIN  
Query params: `apartmentId`, `type` (OWNER/TENANT), `isActive` (bool — filters by move_out_date IS NULL)

Response `200 OK` — paginated list of resident objects (same shape as apartment detail).

---

### POST /api/residents

**Auth:** ADMIN  
**Description:** Assign a user as a resident of an apartment.

Request:
```json
{
  "userId": "uuid",
  "apartmentId": "uuid",
  "type": "OWNER|TENANT",
  "moveInDate": "2024-01-01",
  "isPrimaryContact": false,
  "notes": "string|null"
}
```

Response `201 Created` — resident object.  
Errors: `409 CONFLICT` (user already active resident of another apartment)

---

### GET /api/residents/{id}

**Auth:** ADMIN, RESIDENT (own record only)

Response `200 OK` — full resident detail with apartment and user info.

---

### PUT /api/residents/{id}

**Auth:** ADMIN

Request:
```json
{
  "type": "OWNER|TENANT",
  "isPrimaryContact": true,
  "notes": "string|null"
}
```

Response `200 OK`

---

### POST /api/residents/{id}/move-out

**Auth:** ADMIN  
**Description:** Record a move-out event. Sets move_out_date, appends to resident_history.

Request:
```json
{
  "moveOutDate": "2025-12-31",
  "notes": "string|null"
}
```

Response `200 OK`

---

### GET /api/residents/{id}/history

**Auth:** ADMIN  
**Description:** Return the change history for a resident record.

Response `200 OK` — paginated list:
```json
{
  "data": [
    {
      "id": "uuid",
      "event": "MOVED_IN",
      "eventDate": "2024-01-01",
      "type": "OWNER",
      "changedBy": { "id": "uuid", "fullName": "string" },
      "notes": "string|null",
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### GET /api/apartments/{apartmentId}/history

**Auth:** ADMIN  
**Description:** Full resident change history for an apartment (all residents over time).

Response: same shape as above.

---

## 5. Vehicles

### GET /api/vehicles

**Auth:** ADMIN  
Query params: `apartmentId`, `residentId`, `type`, `licensePlate`, `isActive`

Response `200 OK` — paginated.

---

### POST /api/vehicles

**Auth:** ADMIN, RESIDENT (own apartment)

Request:
```json
{
  "residentId": "uuid",
  "apartmentId": "uuid",
  "type": "CAR|MOTORBIKE|BICYCLE|OTHER",
  "licensePlate": "51A-123.45",
  "brand": "string|null",
  "model": "string|null",
  "color": "string|null",
  "notes": "string|null"
}
```

Response `201 Created` — vehicle object.  
Errors: `409 CONFLICT` (license plate already registered)

---

### PUT /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)  
Request: same as POST minus `residentId`/`apartmentId`.  
Response `200 OK`.

---

### DELETE /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)  
**Description:** Deactivate vehicle (`is_active = false`). Also ends active parking assignment if any.  
Response `204 No Content`

---

## 6. Maintenance

### GET /api/maintenance/categories

**Auth:** ADMIN, TECHNICIAN, RESIDENT  
**Description:** List maintenance categories (for dropdown when submitting).

Response `200 OK`:
```json
{
  "data": [
    { "id": "uuid", "name": "Electrical", "slaHours": 8, "priorityDefault": "HIGH" }
  ]
}
```

---

### POST /api/maintenance/categories

**Auth:** ADMIN

Request:
```json
{
  "name": "string",
  "slaHours": 24,
  "priorityDefault": "MEDIUM"
}
```

Response `201 Created`

---

### PUT /api/maintenance/categories/{id}

**Auth:** ADMIN  
Response `200 OK`

---

### GET /api/maintenance/requests

**Auth:** ADMIN, TECHNICIAN (assigned to them), RESIDENT (own apartment), BOARD_MEMBER  
**Description:** List maintenance requests. Residents see only their apartment's requests. Technicians see assigned + all unassigned.

Query params: `status`, `priority`, `categoryId`, `apartmentId`, `assignedToUserId`, `assignedToContractorId`, `from` (date), `to` (date), `slaBreached` (bool), `search`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
      "submittedBy": { "id": "uuid", "fullName": "string" },
      "category": { "id": "uuid", "name": "Electrical" },
      "title": "Lights not working in bedroom",
      "status": "NEW",
      "priority": "HIGH",
      "assignedToUser": null,
      "assignedToContractor": null,
      "slaDeadline": "2026-05-30T10:00:00Z",
      "slaBreached": false,
      "rating": null,
      "createdAt": "2026-05-29T10:00:00Z",
      "updatedAt": "2026-05-29T10:00:00Z"
    }
  ]
}
```

---

### POST /api/maintenance/requests

**Auth:** ADMIN, RESIDENT  
**Description:** Submit a new maintenance request.

Request:
```json
{
  "apartmentId": "uuid",
  "categoryId": "uuid|null",
  "title": "string",
  "description": "string|null",
  "priority": "MEDIUM"
}
```

Response `201 Created` — request object (same shape as list item).

---

### GET /api/maintenance/requests/{id}

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own apartment)

Response `200 OK` — full detail:
```json
{
  "id": "uuid",
  "apartment": { ... },
  "submittedBy": { ... },
  "category": { ... },
  "title": "string",
  "description": "string",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assignedToUser": { "id": "uuid", "fullName": "string", "phone": "string" },
  "assignedToContractor": null,
  "scheduledDate": "2026-05-30",
  "completedDate": null,
  "rating": null,
  "ratingComment": null,
  "slaDeadline": "2026-05-30T10:00:00Z",
  "slaBreached": false,
  "resolutionNotes": null,
  "photos": [
    {
      "id": "uuid",
      "phase": "BEFORE",
      "presignedUrl": "https://minio.../...",
      "fileName": "photo1.jpg",
      "uploadedAt": "ISO8601"
    }
  ],
  "statusHistory": [
    {
      "oldStatus": null,
      "newStatus": "NEW",
      "changedBy": { "id": "uuid", "fullName": "string" },
      "notes": null,
      "changedAt": "ISO8601"
    }
  ],
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/maintenance/requests/{id}/assign

**Auth:** ADMIN  
**Description:** Assign request to a technician or contractor (mutually exclusive).

Request:
```json
{
  "assignedToUserId": "uuid|null",
  "assignedToContractorId": "uuid|null",
  "scheduledDate": "2026-05-30",
  "notes": "string|null"
}
```

Response `200 OK` — updated request object.  
Errors: `400 VALIDATION_ERROR` (both assignees provided at once)

---

### PUT /api/maintenance/requests/{id}/status

**Auth:** ADMIN, TECHNICIAN (own assigned requests)  
**Description:** Update status of a request. Residents cannot call this endpoint.

Request:
```json
{
  "status": "IN_PROGRESS|DONE|CANCELLED",
  "notes": "string|null",
  "resolutionNotes": "string|null"
}
```

Response `200 OK`  
Errors: `409 CONFLICT` (invalid status transition)

---

### POST /api/maintenance/requests/{id}/photos

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own, BEFORE phase only)  
**Description:** Upload photo(s) to a maintenance request.

Request: `multipart/form-data`
- `files` — one or more image files (JPEG/PNG, max 10 MB each)
- `phase` — `BEFORE|AFTER|PROGRESS`

Response `201 Created`:
```json
{
  "uploaded": [
    { "id": "uuid", "phase": "BEFORE", "fileName": "photo.jpg", "uploadedAt": "ISO8601" }
  ]
}
```

Errors: `400 VALIDATION_ERROR` (unsupported file type), `413` (file too large)

---

### POST /api/maintenance/requests/{id}/rate

**Auth:** RESIDENT (own apartment request, only when status = DONE)  
**Description:** Submit satisfaction rating after resolution.

Request:
```json
{
  "rating": 4,
  "comment": "string|null"
}
```

Response `200 OK`  
Errors: `409 CONFLICT` (already rated, or request not DONE)

---

### GET /api/maintenance/sla-report

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** SLA performance report grouped by category and time period.

Query params: `from` (date), `to` (date), `categoryId`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "summary": {
    "total": 150,
    "completed": 120,
    "slaBreached": 12,
    "slaBreachRate": 0.10,
    "avgResolutionHours": 18.5
  },
  "byCategory": [
    {
      "category": "Electrical",
      "total": 30,
      "completed": 28,
      "slaBreached": 2,
      "avgResolutionHours": 6.2
    }
  ]
}
```

---

## 7. Amenities

### GET /api/amenities

**Auth:** Any authenticated role  
**Description:** List all active amenities.

Response `200 OK`:
```json
{
  "data": [
    {
      "id": "uuid",
      "name": "Swimming Pool",
      "description": "string",
      "location": "Ground Floor",
      "capacity": 20,
      "openingTime": "06:00",
      "closingTime": "22:00",
      "maxDailyBookingsPerResident": 1,
      "requiresApproval": false,
      "isActive": true
    }
  ]
}
```

---

### POST /api/amenities

**Auth:** ADMIN

Request:
```json
{
  "name": "string",
  "description": "string|null",
  "location": "string|null",
  "capacity": 20,
  "openingTime": "06:00",
  "closingTime": "22:00",
  "maxDailyBookingsPerResident": 1,
  "requiresApproval": false
}
```

Response `201 Created`

---

### PUT /api/amenities/{id}

**Auth:** ADMIN  
Request: same as POST.  
Response `200 OK`

---

### DELETE /api/amenities/{id}

**Auth:** ADMIN  
**Description:** Deactivates amenity. Cancels any pending future bookings.  
Response `204 No Content`

---

### GET /api/amenities/{id}/availability

**Auth:** Any authenticated role  
**Description:** Return booked time slots for a given date.

Query params: `date` (required, ISO date)

Response `200 OK`:
```json
{
  "amenityId": "uuid",
  "date": "2026-06-01",
  "bookedSlots": [
    { "startTime": "09:00", "endTime": "10:00" },
    { "startTime": "14:00", "endTime": "15:30" }
  ],
  "remainingCapacity": 3
}
```

---

### GET /api/amenities/{id}/bookings

**Auth:** ADMIN  
Query params: `status`, `from`, `to`

Response `200 OK` — paginated list of bookings.

---

### GET /api/amenity-bookings

**Auth:** ADMIN (all), RESIDENT (own)  
Query params: `amenityId`, `status`, `from`, `to`, `residentId` (ADMIN only)

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "amenity": { "id": "uuid", "name": "Swimming Pool" },
      "resident": { "id": "uuid", "user": { "fullName": "string" } },
      "apartment": { "id": "uuid", "unitNumber": "A301" },
      "bookingDate": "2026-06-01",
      "startTime": "09:00",
      "endTime": "10:00",
      "status": "APPROVED",
      "notes": "string|null",
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### POST /api/amenity-bookings

**Auth:** RESIDENT  
**Description:** Submit a booking request.

Request:
```json
{
  "amenityId": "uuid",
  "bookingDate": "2026-06-01",
  "startTime": "09:00",
  "endTime": "10:00",
  "notes": "string|null"
}
```

Response `201 Created` — booking object.  
Errors: `409 CONFLICT` (time slot unavailable or daily limit reached)

---

### GET /api/amenity-bookings/{id}

**Auth:** ADMIN, RESIDENT (own)

Response `200 OK` — booking detail.

---

### PUT /api/amenity-bookings/{id}/approve

**Auth:** ADMIN

Request:
```json
{ "notes": "string|null" }
```

Response `200 OK`  
Errors: `409 CONFLICT` (booking no longer PENDING)

---

### PUT /api/amenity-bookings/{id}/reject

**Auth:** ADMIN

Request:
```json
{ "reason": "string" }
```

Response `200 OK`

---

### PUT /api/amenity-bookings/{id}/cancel

**Auth:** ADMIN, RESIDENT (own, only if status = PENDING or APPROVED)

Request:
```json
{ "reason": "string|null" }
```

Response `200 OK`

---

## 8. Contractors & Contracts

### GET /api/contractors

**Auth:** ADMIN, BOARD_MEMBER  
Query params: `specialty`, `isActive`, `search`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "companyName": "ABC Cleaning Co.",
      "contactPerson": "string",
      "phone": "string",
      "email": "string",
      "specialty": "CLEANING",
      "rating": 4.2,
      "isActive": true
    }
  ]
}
```

---

### POST /api/contractors

**Auth:** ADMIN

Request:
```json
{
  "companyName": "string",
  "contactPerson": "string|null",
  "phone": "string|null",
  "email": "string|null",
  "address": "string|null",
  "specialty": "CLEANING",
  "taxCode": "string|null",
  "notes": "string|null"
}
```

Response `201 Created`

---

### GET /api/contractors/{id}

**Auth:** ADMIN, BOARD_MEMBER

Response `200 OK` — full contractor detail including active contracts count and average rating.

---

### PUT /api/contractors/{id}

**Auth:** ADMIN  
Request: same as POST.  
Response `200 OK`

---

### DELETE /api/contractors/{id}

**Auth:** ADMIN  
**Description:** Deactivate contractor (`is_active = false`).  
Response `204 No Content`  
Errors: `409 CONFLICT` (contractor has active contracts)

---

### GET /api/contractors/{id}/work-history

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** Maintenance requests assigned to this contractor.

Response `200 OK` — paginated list of maintenance request summaries.

---

### GET /api/contracts

**Auth:** ADMIN, BOARD_MEMBER  
Query params: `contractorId`, `status`, `expiringWithinDays` (int — for expiry alerts), `from`, `to`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "contractor": { "id": "uuid", "companyName": "string" },
      "title": "Cleaning Service Contract 2026",
      "contractValue": 120000000,
      "currency": "VND",
      "startDate": "2026-01-01",
      "endDate": "2026-12-31",
      "status": "ACTIVE",
      "daysToExpiry": 216
    }
  ]
}
```

---

### POST /api/contracts

**Auth:** ADMIN

Request:
```json
{
  "contractorId": "uuid",
  "title": "string",
  "scope": "string|null",
  "contractValue": 120000000,
  "currency": "VND",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "notes": "string|null"
}
```

Response `201 Created`

---

### GET /api/contracts/{id}

**Auth:** ADMIN, BOARD_MEMBER

Response `200 OK` — full contract detail including payments and schedules.

---

### PUT /api/contracts/{id}

**Auth:** ADMIN  
Request: same as POST minus `contractorId`.  
Response `200 OK`

---

### POST /api/contracts/{id}/attachment

**Auth:** ADMIN  
**Description:** Upload or replace the PDF attachment for a contract.

Request: `multipart/form-data` — field `file` (PDF, max 20 MB)

Response `200 OK`:
```json
{ "attachmentUrl": "string (presigned URL, 1h expiry)" }
```

---

### GET /api/contracts/{id}/attachment

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** Get a fresh presigned download URL for the contract attachment.

Response `200 OK`:
```json
{ "presignedUrl": "string", "expiresAt": "ISO8601" }
```

---

### POST /api/contracts/{id}/payments

**Auth:** ADMIN  
**Description:** Record a payment against a contract.

Request:
```json
{
  "amount": 10000000,
  "paymentDate": "2026-05-01",
  "description": "string|null",
  "referenceNumber": "string|null"
}
```

Response `201 Created`:
```json
{
  "id": "uuid",
  "contractId": "uuid",
  "amount": 10000000,
  "paymentDate": "2026-05-01",
  "description": "string",
  "referenceNumber": "string",
  "recordedBy": { "id": "uuid", "fullName": "string" },
  "createdAt": "ISO8601"
}
```

---

### GET /api/contracts/{id}/payments

**Auth:** ADMIN, BOARD_MEMBER  
Response `200 OK` — paginated list of payments.

---

### GET /api/contracts/{id}/schedules

**Auth:** ADMIN  
Response `200 OK` — list of maintenance schedules for this contract.

---

### POST /api/contracts/{id}/schedules

**Auth:** ADMIN

Request:
```json
{
  "title": "Monthly HVAC Inspection",
  "description": "string|null",
  "frequency": "MONTHLY",
  "nextDueDate": "2026-06-01",
  "notes": "string|null"
}
```

Response `201 Created`

---

### PUT /api/maintenance-schedules/{id}

**Auth:** ADMIN

Request:
```json
{
  "title": "string",
  "frequency": "MONTHLY",
  "nextDueDate": "2026-07-01",
  "lastDoneDate": "2026-06-01",
  "isActive": true,
  "notes": "string|null"
}
```

Response `200 OK`

---

## 9. Parking

### GET /api/parking/slots

**Auth:** ADMIN  
Query params: `type`, `status`, `zone`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "slotNumber": "B1-001",
      "zone": "B1",
      "type": "CAR",
      "status": "OCCUPIED",
      "currentAssignment": {
        "vehicle": { "licensePlate": "51A-123.45", "brand": "Toyota" },
        "apartment": { "unitNumber": "A301" },
        "parkingCardNumber": "PC-0012"
      }
    }
  ]
}
```

---

### POST /api/parking/slots

**Auth:** ADMIN

Request:
```json
{
  "slotNumber": "B1-001",
  "zone": "B1",
  "type": "CAR",
  "notes": "string|null"
}
```

Response `201 Created`

---

### PUT /api/parking/slots/{id}

**Auth:** ADMIN  
Request: same as POST minus `slotNumber`.  
Response `200 OK`

---

### POST /api/parking/assignments

**Auth:** ADMIN  
**Description:** Assign a parking slot to a vehicle.

Request:
```json
{
  "parkingSlotId": "uuid",
  "vehicleId": "uuid",
  "apartmentId": "uuid",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "parkingCardNumber": "string|null"
}
```

Response `201 Created` — assignment object.  
Errors: `409 CONFLICT` (slot already occupied)

---

### GET /api/parking/assignments

**Auth:** ADMIN  
Query params: `slotId`, `vehicleId`, `apartmentId`, `isActive` (bool — filters by end_date IS NULL)

Response `200 OK` — paginated.

---

### PUT /api/parking/assignments/{id}/end

**Auth:** ADMIN  
**Description:** End a parking assignment (vehicle leaves slot).

Request:
```json
{ "endDate": "2026-06-01", "notes": "string|null" }
```

Response `200 OK`

---

### GET /api/parking/guest-vehicles

**Auth:** ADMIN  
Query params: `apartmentId`, `from`, `to`, `licensePlate`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "licensePlate": "51A-999.99",
      "ownerName": "string",
      "hostApartment": { "id": "uuid", "unitNumber": "A301" },
      "entryTime": "ISO8601",
      "exitTime": "ISO8601|null",
      "purpose": "string|null"
    }
  ]
}
```

---

### POST /api/parking/guest-vehicles

**Auth:** ADMIN, TECHNICIAN  
**Description:** Log a guest vehicle entry.

Request:
```json
{
  "licensePlate": "51A-999.99",
  "ownerName": "string|null",
  "hostApartmentId": "uuid",
  "purpose": "string|null",
  "notes": "string|null"
}
```

Response `201 Created`

---

### PUT /api/parking/guest-vehicles/{id}/exit

**Auth:** ADMIN, TECHNICIAN  
**Description:** Record guest vehicle exit time.

Request:
```json
{ "exitTime": "ISO8601|null (defaults to now)" }
```

Response `200 OK`

---

## 10. Announcements

### GET /api/announcements

**Auth:** Any authenticated role  
**Description:** Residents see only announcements targeted to their block/floor/all. Admins see all.

Query params: `type`, `from`, `to`, `isPublished` (bool, default: true), `blockId`, `floor`, `search`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Scheduled Water Shutdown",
      "type": "MAINTENANCE",
      "targetScope": "BLOCK",
      "targetBlock": { "id": "uuid", "name": "Block A" },
      "targetFloor": null,
      "publishedAt": "ISO8601",
      "createdBy": { "id": "uuid", "fullName": "string" },
      "isRead": false,
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### POST /api/announcements

**Auth:** ADMIN

Request:
```json
{
  "title": "string",
  "content": "string",
  "type": "GENERAL|URGENT|MAINTENANCE|AMENITY|EVENT",
  "targetScope": "ALL|BLOCK|FLOOR",
  "targetBlockId": "uuid|null",
  "targetFloor": "number|null",
  "sendPush": true,
  "sendEmail": false,
  "sendSms": false,
  "publishNow": true
}
```

Response `201 Created` — announcement object.  
**Side effect when `publishNow: true`:** FCM push / email / SMS sent to target audience.

---

### GET /api/announcements/{id}

**Auth:** Any authenticated role (same targeting rules as list)

Response `200 OK` — full announcement detail including full `content` text.

---

### PUT /api/announcements/{id}

**Auth:** ADMIN  
**Description:** Update a draft announcement. Cannot edit already-published announcements.

Request: same as POST minus `publishNow`.  
Response `200 OK`  
Errors: `409 CONFLICT` (announcement already published)

---

### POST /api/announcements/{id}/publish

**Auth:** ADMIN  
**Description:** Publish a draft announcement and trigger delivery.

Response `200 OK`

---

### POST /api/announcements/{id}/read

**Auth:** Any authenticated role  
**Description:** Mark announcement as read for the current user.

Response `204 No Content`

---

### GET /api/announcements/{id}/read-stats

**Auth:** ADMIN  
**Description:** Read receipt statistics.

Response `200 OK`:
```json
{
  "announcementId": "uuid",
  "targetAudienceCount": 200,
  "readCount": 145,
  "readRate": 0.725,
  "unreadUsers": [
    { "id": "uuid", "fullName": "string", "apartment": "A301" }
  ]
}
```

---

## 11. Reports & Dashboard

All report endpoints require `ADMIN` or `BOARD_MEMBER` role.

---

### GET /api/reports/dashboard

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** Summary KPIs for the dashboard landing page.

Response `200 OK`:
```json
{
  "apartments": {
    "total": 1000,
    "occupied": 875,
    "available": 100,
    "maintenance": 25
  },
  "maintenance": {
    "openRequests": 23,
    "inProgressRequests": 15,
    "overdueRequests": 3,
    "avgResolutionHoursLast30Days": 20.5
  },
  "amenities": {
    "bookingsThisMonth": 145,
    "pendingApproval": 5
  },
  "contracts": {
    "active": 8,
    "expiringIn30Days": 2,
    "expiringIn90Days": 4
  }
}
```

---

### GET /api/reports/maintenance

**Auth:** ADMIN, BOARD_MEMBER  
Query params: `from`, `to`, `groupBy` (month|category|status|assignee), `categoryId`, `apartmentId`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "summary": {
    "total": 150,
    "completed": 120,
    "cancelled": 5,
    "inProgress": 20,
    "new": 5,
    "slaBreachRate": 0.08,
    "avgRating": 4.1
  },
  "breakdown": [
    { "label": "2026-01", "total": 28, "completed": 25, "slaBreached": 1, "avgRating": 4.3 }
  ]
}
```

---

### GET /api/reports/amenity-usage

**Auth:** ADMIN, BOARD_MEMBER  
Query params: `from`, `to`, `amenityId`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "byAmenity": [
    {
      "amenity": { "id": "uuid", "name": "Swimming Pool" },
      "totalBookings": 85,
      "approvedBookings": 80,
      "rejectedBookings": 3,
      "cancelledBookings": 2,
      "peakDay": "2026-05-15",
      "utilizationRate": 0.62
    }
  ]
}
```

---

### GET /api/reports/contracts-expiring

**Auth:** ADMIN, BOARD_MEMBER  
Query params: `withinDays` (default: 90)

Response `200 OK`:
```json
{
  "asOf": "2026-05-29",
  "contracts": [
    {
      "id": "uuid",
      "title": "string",
      "contractor": { "id": "uuid", "companyName": "string" },
      "endDate": "2026-07-31",
      "daysToExpiry": 63,
      "contractValue": 120000000,
      "status": "ACTIVE"
    }
  ]
}
```

---

### GET /api/reports/residents

**Auth:** ADMIN, BOARD_MEMBER  
**Description:** Occupancy and resident demographics.

Query params: `blockId`

Response `200 OK`:
```json
{
  "totalApartments": 1000,
  "occupiedApartments": 875,
  "occupancyRate": 0.875,
  "totalResidents": 2100,
  "owners": 875,
  "tenants": 1225,
  "averageResidentsPerApartment": 2.4
}
```

---

## 12. Notifications

### GET /api/notifications

**Auth:** Any authenticated role  
**Description:** List the current user's notifications.

Query params: `isRead` (bool), `type`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Maintenance request #123 has been assigned",
      "body": "string",
      "type": "MAINTENANCE_ASSIGNED",
      "referenceId": "uuid",
      "referenceType": "MaintenanceRequest",
      "isRead": false,
      "createdAt": "ISO8601"
    }
  ],
  "unreadCount": 3
}
```

---

### PUT /api/notifications/{id}/read

**Auth:** Any authenticated role (own notifications only)  
**Description:** Mark a single notification as read.

Response `204 No Content`

---

### PUT /api/notifications/read-all

**Auth:** Any authenticated role  
**Description:** Mark all of the current user's notifications as read.

Response `204 No Content`

---

## 13. File Presign (internal utility)

### GET /api/files/presign

**Auth:** Any authenticated role  
**Description:** Get a short-lived presigned GET URL for a MinIO object. Used by frontend to display images/download PDFs.

Query params: `objectKey` (required)

Response `200 OK`:
```json
{
  "presignedUrl": "https://minio.internal/bucket/objectKey?X-Amz-...",
  "expiresAt": "2026-05-29T11:00:00Z"
}
```

Note: Backend validates the requesting user has permission to access the referenced object before issuing the URL.

---

## Appendix A: Status Transition Rules

### Maintenance Request

```
NEW → ASSIGNED (by ADMIN, when assignee set)
NEW → CANCELLED (by ADMIN or RESIDENT who submitted)
ASSIGNED → IN_PROGRESS (by TECHNICIAN or ADMIN)
ASSIGNED → CANCELLED (by ADMIN)
IN_PROGRESS → DONE (by TECHNICIAN or ADMIN)
IN_PROGRESS → ASSIGNED (by ADMIN — reassignment)
DONE → (terminal, rating can still be added)
CANCELLED → (terminal)
```

### Amenity Booking

```
PENDING → APPROVED (by ADMIN, or auto-approved if requiresApproval = false)
PENDING → REJECTED (by ADMIN)
PENDING → CANCELLED (by RESIDENT or ADMIN)
APPROVED → CANCELLED (by RESIDENT before booking_date, or ADMIN)
APPROVED → COMPLETED (by scheduler at end of booking window)
REJECTED → (terminal)
CANCELLED → (terminal)
COMPLETED → (terminal)
```

### Contract

```
PENDING → ACTIVE (by ADMIN after signing)
ACTIVE → EXPIRED (by scheduler when end_date passed)
ACTIVE → TERMINATED (by ADMIN)
EXPIRED → (terminal)
TERMINATED → (terminal)
```

---

## Appendix B: Rate Limiting

| Endpoint group | Limit |
|----------------|-------|
| `POST /api/auth/login` | 10 req/min per IP |
| `POST /api/auth/refresh` | 20 req/min per user |
| `POST /api/maintenance/requests/{id}/photos` | 10 req/min per user |
| All other endpoints | 120 req/min per authenticated user |
