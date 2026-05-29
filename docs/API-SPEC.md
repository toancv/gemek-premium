# API Specification — Apartment Management System

**Version:** 2.0
**Date:** 2026-05-29
**Base URL:** `https://{host}/api`
**Auth:** `Authorization: Bearer <accessToken>` header on all endpoints unless marked **Public**.

---

## Global Conventions

### Standard Error Response

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2026-05-29T10:00:00Z",
  "path": "/api/tickets"
}
```

Common error codes:

| Code | HTTP | Meaning |
|------|------|---------|
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `FORBIDDEN` | 403 | Valid token but insufficient role or resource ownership |
| `NOT_FOUND` | 404 | Resource does not exist |
| `VALIDATION_ERROR` | 400 | Request body or query param fails validation |
| `CONFLICT` | 409 | Duplicate or invalid state transition |
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

Pagination query params (all list endpoints unless noted):
- `page` — 0-based page index (default: `0`)
- `size` — page size (default: `20`, max: `100`)
- `sort` — field name (default varies per endpoint, documented inline)
- `direction` — `asc` or `desc` (default: `desc`)

### Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Building manager — full control over all modules |
| `TECHNICIAN` | Internal maintenance / operations staff — works assigned tickets |
| `RESIDENT` | Apartment resident — personal portal |
| `BOARD_MEMBER` | Read-only access to reports and dashboard |

---

## 1. Auth

### POST /api/auth/login

**Auth:** Public
**Rate limit:** 10 req/min per IP
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
**Rate limit:** 20 req/min per user
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

Errors: `401 UNAUTHORIZED` (expired or invalid refresh token)

---

### POST /api/auth/logout

**Auth:** Any authenticated role
**Description:** Invalidate the current access token (JTI added to Redis blocklist). Client must discard both tokens.

Request: (no body)

Response `204 No Content`

---

### GET /api/auth/me

**Auth:** Any authenticated role
**Description:** Return the authenticated user's own profile.

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
  "newPassword": "string (min 8 chars, must include upper, lower, digit, special)"
}
```

Response `204 No Content`

Errors: `400 VALIDATION_ERROR` (weak password), `401 UNAUTHORIZED` (wrong current password)

---

### PUT /api/auth/me/fcm-token

**Auth:** Any authenticated role
**Description:** Register or refresh the FCM device token for push notifications. Called on each app startup.

Request:
```json
{
  "fcmToken": "string"
}
```

Response `204 No Content`

---

## 2. Users

### GET /api/users

**Auth:** ADMIN
**Description:** List all users with optional filters.

Query params: `role`, `isActive` (bool), `search` (name or email substring)
Default sort: `createdAt desc`

Response `200 OK` — paginated:
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
**Description:** Create a new user account (staff or resident).

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

Response `200 OK` — same shape as list item.

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
**Description:** Deactivate user account (`is_active = false`). Cannot deactivate users with active resident assignments.

Response `204 No Content`

Errors: `409 CONFLICT` (user has active resident assignment)

---

### PUT /api/users/{id}/reset-password

**Auth:** ADMIN
**Description:** Admin forces a password reset for another user.

Request:
```json
{
  "newPassword": "string"
}
```

Response `204 No Content`

---

## 3. Blocks and Apartments

### GET /api/blocks

**Auth:** ADMIN, BOARD_MEMBER, TECHNICIAN

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
Request: same shape as POST.
Response `200 OK` — updated block object.

---

### DELETE /api/blocks/{id}

**Auth:** ADMIN
Response `204 No Content`
Errors: `409 CONFLICT` (block has apartments)

---

### GET /api/apartments

**Auth:** ADMIN, BOARD_MEMBER
**Description:** List apartments. Supports filtering.

Query params: `blockId`, `floor`, `status`, `search` (unit number substring)
Default sort: `block name asc, floor asc, unitNumber asc`

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
      "primaryContact": {
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
**Description:** Full apartment detail including current residents and registered vehicles.

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
      "color": "White",
      "isActive": true
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
Query params: `apartmentId`, `type` (`OWNER`/`TENANT`), `isActive` (bool — filters by `move_out_date IS NULL`)
Default sort: `createdAt desc`

Response `200 OK` — paginated list of resident objects.

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

Response `201 Created`:
```json
{
  "id": "uuid",
  "user": { "id": "uuid", "fullName": "string", "email": "string" },
  "apartment": { "id": "uuid", "unitNumber": "A301" },
  "type": "OWNER",
  "moveInDate": "2024-01-01",
  "moveOutDate": null,
  "isPrimaryContact": false,
  "createdAt": "ISO8601"
}
```

Errors: `409 CONFLICT` (user is already an active resident of another apartment)

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

Response `200 OK` — updated resident object.

---

### POST /api/residents/{id}/move-out

**Auth:** ADMIN
**Description:** Record a move-out event. Sets `move_out_date`, appends to `resident_history`.

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
**Description:** Return the change history for a specific resident record.

Response `200 OK` — paginated:
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
**Description:** Full resident change history for an apartment (all residents, all time).

Response: same shape as above.

---

## 5. Vehicles

### GET /api/vehicles

**Auth:** ADMIN
Query params: `apartmentId`, `residentId`, `type`, `licensePlate`, `isActive`
Default sort: `createdAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "resident": { "id": "uuid", "user": { "fullName": "string" } },
      "apartment": { "id": "uuid", "unitNumber": "A301" },
      "type": "CAR",
      "licensePlate": "51A-123.45",
      "brand": "Toyota",
      "model": "Camry",
      "color": "White",
      "isActive": true
    }
  ]
}
```

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
Request: same as POST minus `residentId` and `apartmentId`.
Response `200 OK` — updated vehicle object.

---

### DELETE /api/vehicles/{id}

**Auth:** ADMIN, RESIDENT (own vehicle)
**Description:** Deactivate vehicle (`is_active = false`). Ends any active parking assignment for this vehicle.

Response `204 No Content`

---

## 6. Tickets

The ticket module handles ALL resident request types. The `category` field determines routing and contractor assignment eligibility.

**Category values:** `MAINTENANCE_REPAIR`, `COMPLAINT`, `ADMINISTRATIVE`, `SUGGESTION_FEEDBACK`, `OTHER`

**Status flow:** `NEW` → `ASSIGNED` → `IN_PROGRESS` → `DONE` (also: `CANCELLED`)

**Contractor assignment rule:** `assignedToContractorId` may only be set when `category = MAINTENANCE_REPAIR`. All other categories may only be assigned to internal staff.

---

### GET /api/tickets

**Auth:** ADMIN (all), TECHNICIAN (assigned to them + NEW/unassigned), RESIDENT (own apartment), BOARD_MEMBER (all, read-only)
**Description:** List tickets with rich filtering. Results are scoped by role automatically on the server side.

Query params:
- `category` — `MAINTENANCE_REPAIR` | `COMPLAINT` | `ADMINISTRATIVE` | `SUGGESTION_FEEDBACK` | `OTHER`
- `status` — `NEW` | `ASSIGNED` | `IN_PROGRESS` | `DONE` | `CANCELLED`
- `priority` — `LOW` | `MEDIUM` | `HIGH` | `URGENT`
- `apartmentId` — UUID
- `assignedToUserId` — UUID
- `assignedToContractorId` — UUID
- `from` — ISO date (filter by `createdAt`)
- `to` — ISO date
- `slaBreached` — bool (tickets where `sla_deadline < NOW()` and status not terminal)
- `search` — substring search on `title`

Default sort: `createdAt desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
      "submittedBy": { "id": "uuid", "fullName": "string" },
      "category": "MAINTENANCE_REPAIR",
      "title": "Air conditioner not cooling",
      "status": "ASSIGNED",
      "priority": "HIGH",
      "assignedToUser": { "id": "uuid", "fullName": "Tran Van B" },
      "assignedToContractor": null,
      "slaDeadline": "2026-05-30T10:00:00Z",
      "slaBreached": false,
      "rating": null,
      "createdAt": "2026-05-29T10:00:00Z",
      "updatedAt": "2026-05-29T11:00:00Z"
    }
  ],
  "page": 0, "size": 20, "total": 45, "totalPages": 3
}
```

---

### POST /api/tickets

**Auth:** ADMIN, RESIDENT
**Description:** Submit a new ticket.

Request:
```json
{
  "apartmentId": "uuid",
  "category": "MAINTENANCE_REPAIR|COMPLAINT|ADMINISTRATIVE|SUGGESTION_FEEDBACK|OTHER",
  "title": "string (max 255)",
  "description": "string|null",
  "priority": "LOW|MEDIUM|HIGH|URGENT"
}
```

Response `201 Created` — ticket summary object (same shape as list item).

**Side effects:**
- `sla_deadline` computed from category default SLA and stored.
- Status history entry `null → NEW` created.
- Admin receives an in-app notification of new ticket.

---

### GET /api/tickets/{id}

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own apartment), BOARD_MEMBER

Response `200 OK` — full ticket detail:
```json
{
  "id": "uuid",
  "apartment": { "id": "uuid", "unitNumber": "A301", "block": { "name": "Block A" } },
  "submittedBy": { "id": "uuid", "fullName": "string", "phone": "string" },
  "category": "MAINTENANCE_REPAIR",
  "title": "string",
  "description": "string|null",
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
      "mimeType": "image/jpeg",
      "fileSizeBytes": 204800,
      "uploadedAt": "ISO8601"
    }
  ],
  "statusHistory": [
    {
      "id": "uuid",
      "oldStatus": null,
      "newStatus": "NEW",
      "changedBy": { "id": "uuid", "fullName": "string" },
      "notes": null,
      "changedAt": "ISO8601"
    },
    {
      "id": "uuid",
      "oldStatus": "NEW",
      "newStatus": "ASSIGNED",
      "changedBy": { "id": "uuid", "fullName": "Admin" },
      "notes": "Assigned to technician",
      "changedAt": "ISO8601"
    }
  ],
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/tickets/{id}/assign

**Auth:** ADMIN
**Description:** Assign a ticket to an internal staff member or (for MAINTENANCE_REPAIR only) a contractor. Mutually exclusive — only one assignee at a time.

Request:
```json
{
  "assignedToUserId": "uuid|null",
  "assignedToContractorId": "uuid|null",
  "scheduledDate": "2026-05-30",
  "notes": "string|null"
}
```

Response `200 OK` — updated ticket summary.

Errors:
- `400 VALIDATION_ERROR` — both `assignedToUserId` and `assignedToContractorId` provided simultaneously.
- `400 VALIDATION_ERROR` — `assignedToContractorId` provided but `category != MAINTENANCE_REPAIR`.
- `404 NOT_FOUND` — assignee not found.

**Side effects:**
- Status transitions to `ASSIGNED`.
- Assignee receives an in-app + push notification.

---

### PUT /api/tickets/{id}/status

**Auth:** ADMIN, TECHNICIAN (own assigned tickets only)
**Description:** Update ticket status. Residents cannot call this endpoint.

Request:
```json
{
  "status": "IN_PROGRESS|DONE|CANCELLED|ASSIGNED",
  "notes": "string|null",
  "resolutionNotes": "string|null"
}
```

Response `200 OK` — updated ticket summary.

Errors: `409 CONFLICT` (invalid status transition — see Appendix A)

**Side effects:**
- Status history entry created.
- Submitting resident notified of status change.
- On `DONE`: contractor rating prompt notification sent to resident.

---

### POST /api/tickets/{id}/photos

**Auth:** ADMIN, TECHNICIAN (assigned), RESIDENT (own apartment — BEFORE phase only)
**Description:** Upload one or more photos to a ticket.

Request: `multipart/form-data`
- `files` — one or more image files (`image/jpeg` or `image/png`, max 10 MB each, max 5 per request)
- `phase` — `BEFORE` | `PROGRESS` | `AFTER`

Response `201 Created`:
```json
{
  "uploaded": [
    {
      "id": "uuid",
      "phase": "BEFORE",
      "fileName": "photo.jpg",
      "fileSizeBytes": 102400,
      "uploadedAt": "ISO8601"
    }
  ]
}
```

Errors: `400 VALIDATION_ERROR` (unsupported MIME type), `413` (file exceeds size limit)

---

### POST /api/tickets/{id}/rate

**Auth:** RESIDENT (own apartment, only when `status = DONE`, one rating per ticket)
**Description:** Submit a satisfaction rating after the ticket is resolved.

Request:
```json
{
  "rating": 4,
  "comment": "string|null (max 500 chars)"
}
```

Response `200 OK`

Errors: `409 CONFLICT` (already rated, or ticket not in DONE status)

**Side effects:**
- If ticket was assigned to a contractor, contractor's average `rating` is recalculated.

---

### GET /api/tickets/sla-report

**Auth:** ADMIN, BOARD_MEMBER
**Description:** SLA performance report grouped by category.

Query params: `from` (ISO date), `to` (ISO date), `category`

Response `200 OK`:
```json
{
  "period": { "from": "2026-01-01", "to": "2026-05-29" },
  "summary": {
    "total": 150,
    "completed": 120,
    "slaBreached": 12,
    "slaBreachRate": 0.10,
    "avgResolutionHours": 18.5,
    "avgRating": 4.1
  },
  "byCategory": [
    {
      "category": "MAINTENANCE_REPAIR",
      "total": 60,
      "completed": 55,
      "slaBreached": 4,
      "slaBreachRate": 0.07,
      "avgResolutionHours": 20.3,
      "avgRating": 4.2
    },
    {
      "category": "COMPLAINT",
      "total": 30,
      "completed": 28,
      "slaBreached": 5,
      "slaBreachRate": 0.17,
      "avgResolutionHours": 36.0,
      "avgRating": 3.8
    },
    {
      "category": "ADMINISTRATIVE",
      "total": 25,
      "completed": 22,
      "slaBreached": 2,
      "slaBreachRate": 0.08,
      "avgResolutionHours": 48.0,
      "avgRating": 4.5
    },
    {
      "category": "SUGGESTION_FEEDBACK",
      "total": 20,
      "completed": 10,
      "slaBreached": 1,
      "slaBreachRate": 0.05,
      "avgResolutionHours": 96.0,
      "avgRating": null
    },
    {
      "category": "OTHER",
      "total": 15,
      "completed": 5,
      "slaBreached": 0,
      "slaBreachRate": 0.0,
      "avgResolutionHours": 24.0,
      "avgRating": 4.0
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
      "description": "Outdoor rooftop pool",
      "location": "Rooftop",
      "capacity": 30,
      "openingTime": "06:00",
      "closingTime": "21:00",
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

Response `201 Created` — amenity object.

---

### PUT /api/amenities/{id}

**Auth:** ADMIN
Request: same as POST.
Response `200 OK` — updated amenity object.

---

### DELETE /api/amenities/{id}

**Auth:** ADMIN
**Description:** Deactivate amenity (`is_active = false`). Cancels any pending future bookings with a system notification to affected residents.

Response `204 No Content`

---

### GET /api/amenities/{id}/availability

**Auth:** Any authenticated role
**Description:** Return booked time slots and remaining capacity for a given date.

Query params: `date` (required, ISO date)

Response `200 OK`:
```json
{
  "amenityId": "uuid",
  "date": "2026-06-01",
  "openingTime": "06:00",
  "closingTime": "21:00",
  "bookedSlots": [
    { "startTime": "09:00", "endTime": "10:00" },
    { "startTime": "14:00", "endTime": "15:30" }
  ],
  "remainingCapacity": 3
}
```

---

### GET /api/amenity-bookings

**Auth:** ADMIN (all bookings), RESIDENT (own bookings only)
Query params: `amenityId`, `status`, `from`, `to`, `residentId` (ADMIN only)
Default sort: `bookingDate desc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "amenity": { "id": "uuid", "name": "Swimming Pool" },
      "resident": { "id": "uuid", "user": { "id": "uuid", "fullName": "string" } },
      "apartment": { "id": "uuid", "unitNumber": "A301" },
      "bookingDate": "2026-06-01",
      "startTime": "09:00",
      "endTime": "10:00",
      "status": "APPROVED",
      "notes": "string|null",
      "approvedBy": { "id": "uuid", "fullName": "string" },
      "approvedAt": "ISO8601",
      "createdAt": "ISO8601"
    }
  ]
}
```

---

### POST /api/amenity-bookings

**Auth:** RESIDENT
**Description:** Submit a booking request. If `amenity.requiresApproval = false`, status is automatically set to `APPROVED`.

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

Errors:
- `409 CONFLICT` — time slot unavailable (conflict with existing APPROVED booking).
- `409 CONFLICT` — daily booking limit reached for this resident and amenity.

---

### GET /api/amenity-bookings/{id}

**Auth:** ADMIN, RESIDENT (own booking)

Response `200 OK` — full booking detail.

---

### PUT /api/amenity-bookings/{id}/approve

**Auth:** ADMIN

Request:
```json
{ "notes": "string|null" }
```

Response `200 OK`
Errors: `409 CONFLICT` (booking is not in PENDING status)

---

### PUT /api/amenity-bookings/{id}/reject

**Auth:** ADMIN

Request:
```json
{ "reason": "string (required)" }
```

Response `200 OK`
Errors: `409 CONFLICT` (booking is not in PENDING status)

---

### PUT /api/amenity-bookings/{id}/cancel

**Auth:** ADMIN, RESIDENT (own booking when status is PENDING or APPROVED and booking_date is in the future)

Request:
```json
{ "reason": "string|null" }
```

Response `200 OK`

---

## 8. Contractors and Contracts

### GET /api/contractors

**Auth:** ADMIN, BOARD_MEMBER
Query params: `specialty`, `isActive`, `search` (company name substring)
Default sort: `companyName asc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "companyName": "ABC Cleaning Co.",
      "contactPerson": "Nguyen Van C",
      "phone": "0909123456",
      "email": "contact@abc.vn",
      "specialty": "CLEANING",
      "rating": 4.20,
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
  "specialty": "CLEANING|SECURITY|ELEVATOR|FIRE_SAFETY|LANDSCAPING|PEST_CONTROL|ELECTRICAL|PLUMBING|OTHER",
  "taxCode": "string|null",
  "notes": "string|null"
}
```

Response `201 Created` — contractor object.

---

### GET /api/contractors/{id}

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Full contractor profile including active contract count and computed average rating.

Response `200 OK`:
```json
{
  "id": "uuid",
  "companyName": "string",
  "contactPerson": "string",
  "phone": "string",
  "email": "string",
  "address": "string",
  "specialty": "CLEANING",
  "taxCode": "string",
  "rating": 4.20,
  "notes": "string",
  "isActive": true,
  "activeContractsCount": 2,
  "totalTicketsAssigned": 15,
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/contractors/{id}

**Auth:** ADMIN
Request: same as POST.
Response `200 OK` — updated contractor object.

---

### DELETE /api/contractors/{id}

**Auth:** ADMIN
**Description:** Deactivate contractor (`is_active = false`). Cannot deactivate if there are active contracts.

Response `204 No Content`
Errors: `409 CONFLICT` (contractor has active contracts)

---

### GET /api/contractors/{id}/work-history

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Tickets that were assigned to this contractor.

Query params: `from`, `to`, `status`
Default sort: `createdAt desc`

Response `200 OK` — paginated list of ticket summaries (same shape as ticket list item).

---

### GET /api/contracts

**Auth:** ADMIN, BOARD_MEMBER
Query params: `contractorId`, `status`, `expiringWithinDays` (int), `from`, `to`
Default sort: `endDate asc`

Response `200 OK` — paginated:
```json
{
  "data": [
    {
      "id": "uuid",
      "contractor": { "id": "uuid", "companyName": "ABC Cleaning Co." },
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

Response `201 Created` — contract summary object.

---

### GET /api/contracts/{id}

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Full contract detail including payments and schedules.

Response `200 OK`:
```json
{
  "id": "uuid",
  "contractor": { "id": "uuid", "companyName": "string", "specialty": "CLEANING" },
  "title": "string",
  "scope": "string",
  "contractValue": 120000000,
  "currency": "VND",
  "startDate": "2026-01-01",
  "endDate": "2026-12-31",
  "status": "ACTIVE",
  "hasAttachment": true,
  "notes": "string",
  "createdBy": { "id": "uuid", "fullName": "string" },
  "totalPaid": 40000000,
  "schedulesCount": 3,
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

---

### PUT /api/contracts/{id}

**Auth:** ADMIN
Request: same as POST minus `contractorId`.
Response `200 OK` — updated contract object.

---

### POST /api/contracts/{id}/attachment

**Auth:** ADMIN
**Description:** Upload or replace the PDF attachment for a contract.

Request: `multipart/form-data` — field `file` (PDF only, max 20 MB)

Response `200 OK`:
```json
{ "objectKey": "contracts/<id>/attachment/contract.pdf" }
```

---

### GET /api/contracts/{id}/attachment

**Auth:** ADMIN, BOARD_MEMBER
**Description:** Get a short-lived presigned download URL for the contract attachment.

Response `200 OK`:
```json
{
  "presignedUrl": "https://minio.../...",
  "expiresAt": "2026-05-29T11:00:00Z"
}
```

Errors: `404 NOT_FOUND` (no attachment uploaded)

---

### POST /api/contracts/{id}/payments

**Auth:** ADMIN
**Description:** Record a payment against a contract (record-only, not a disbursement approval).

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
Default sort: `paymentDate desc`

Response `200 OK` — paginated list of payment objects.

---

### GET /api/contracts/{id}/schedules

**Auth:** ADMIN

Response `200 OK`:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Monthly HVAC Inspection",
      "description": "string",
      "frequency": "MONTHLY",
      "nextDueDate": "2026-06-01",
      "lastDoneDate": "2026-05-01",
      "isActive": true
    }
  ]
}
```

---

### POST /api/contracts/{id}/schedules

**Auth:** ADMIN

Request:
```json
{
  "title": "Monthly HVAC Inspection",
  "description": "string|null",
  "frequency": "DAILY|WEEKLY|MONTHLY|QUARTERLY|ANNUAL",
  "nextDueDate": "2026-06-01",
  "notes": "string|null"
}
```

Response `201 Created` — schedule object.

---

### PUT /api/maintenance-schedules/{id}

**Auth:** ADMIN

Request:
```json
{
  "title": "string",
  "description": "string|null",
  "frequency": "MONTHLY",
  "nextDueDate": "2026-07-01",
  "lastDoneDate": "2026-06-01",
  "isActive": true,
  "notes": "string|null"
}
```

Response `200 OK` — updated schedule object.

---

## 9. Parking

### GET /api/parking/slots

**Auth:** ADMIN
Query params: `type`, `status`, `zone`
Default sort: `slotNumber asc`

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
        "id": "uuid",
        "vehicle": { "licensePlate": "51A-123.45", "brand": "Toyota", "type": "CAR" },
        "apartment": { "id": "uuid", "unitNumber": "A301" },
        "parkingCardNumber": "PC-0012",
        "startDate": "2026-01-01"
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
  "type": "CAR|MOTORBIKE|BICYCLE",
  "notes": "string|null"
}
```

Response `201 Created` — parking slot object.
Errors: `409 CONFLICT` (slot number already exists)

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
Errors: `409 CONFLICT` (slot already has an active assignment)

---

### GET /api/parking/assignments

**Auth:** ADMIN
Query params: `slotId`, `vehicleId`, `apartmentId`, `isActive` (bool)
Default sort: `startDate desc`

Response `200 OK` — paginated list of assignment objects.

---

### PUT /api/parking/assignments/{id}/end

**Auth:** ADMIN
**Description:** End a parking assignment.

Request:
```json
{ "endDate": "2026-06-01", "notes": "string|null" }
```

Response `200 OK`

---

### GET /api/parking/guest-vehicles

**Auth:** ADMIN
Query params: `apartmentId`, `from`, `to`, `licensePlate`
Default sort: `entryTime desc`

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
      "purpose": "string|null",
      "recordedBy": { "id": "uuid", "fullName": "string" }
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

Response `201 Created` — guest vehicle object.

---

### PUT /api/parking/guest-vehicles/{id}/exit

**Auth:** ADMIN, TECHNICIAN
**Description:** Record guest vehicle exit.

Request:
```json
{ "exitTime": "ISO8601|null (defaults to server current time)" }
```

Response `200 OK`

---

## 10. Announcements

### GET /api/announcements

**Auth:** Any authenticated role
**Description:** Residents see only announcements targeted to their block/floor/all. Admins see all including drafts.

Query params: `type`, `from`, `to`, `isPublished` (bool, default: `true`), `blockId`, `floor`, `search`
Default sort: `publishedAt desc`

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

Response `201 Created` — announcement summary object.

**Side effects when `publishNow: true`:**
- `published_at` set to NOW().
- FCM push / email / SMS dispatched asynchronously to target audience.
- In-app notifications created for all targeted users.

---

### GET /api/announcements/{id}

**Auth:** Any authenticated role (same scoping rules as list)

Response `200 OK` — full announcement detail including full `content` text.

---

### PUT /api/announcements/{id}

**Auth:** ADMIN
**Description:** Update a draft announcement. Cannot modify already-published announcements.

Request: same as POST minus `publishNow`.
Response `200 OK`
Errors: `409 CONFLICT` (announcement is already published)

---

### POST /api/announcements/{id}/publish

**Auth:** ADMIN
**Description:** Publish a draft announcement and trigger notification delivery.

Request: (no body)
Response `200 OK`
Errors: `409 CONFLICT` (announcement already published)

---

### POST /api/announcements/{id}/read

**Auth:** Any authenticated role
**Description:** Mark announcement as read for the calling user.

Request: (no body)
Response `204 No Content`

---

### GET /api/announcements/{id}/read-stats

**Auth:** ADMIN
**Description:** Read receipt statistics for an announcement.

Response `200 OK`:
```json
{
  "announcementId": "uuid",
  "targetAudienceCount": 200,
  "readCount": 145,
  "readRate": 0.725,
  "unreadUsers": [
    { "id": "uuid", "fullName": "string", "apartment": "A301", "block": "Block A" }
  ]
}
```

---

## 11. Reports and Dashboard

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
    "maintenance": 25,
    "occupancyRate": 0.875
  },
  "tickets": {
    "openRequests": 23,
    "inProgressRequests": 15,
    "overdueRequests": 3,
    "avgResolutionHoursLast30Days": 20.5,
    "byCategory": {
      "MAINTENANCE_REPAIR": 10,
      "COMPLAINT": 5,
      "ADMINISTRATIVE": 3,
      "SUGGESTION_FEEDBACK": 2,
      "OTHER": 3
    }
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

### GET /api/reports/tickets

**Auth:** ADMIN, BOARD_MEMBER
Query params: `from`, `to`, `groupBy` (`month` | `category` | `status` | `assignee`), `category`, `apartmentId`

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
    {
      "label": "2026-01",
      "total": 28,
      "completed": 25,
      "slaBreached": 1,
      "avgRating": 4.3
    }
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
Query params: `withinDays` (default: `90`)

Response `200 OK`:
```json
{
  "asOf": "2026-05-29",
  "contracts": [
    {
      "id": "uuid",
      "title": "Cleaning Service Contract 2026",
      "contractor": { "id": "uuid", "companyName": "ABC Cleaning Co." },
      "endDate": "2026-07-31",
      "daysToExpiry": 63,
      "contractValue": 120000000,
      "currency": "VND",
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
  "totalActiveResidents": 2100,
  "owners": 875,
  "tenants": 1225,
  "averageResidentsPerApartment": 2.4
}
```

---

## 12. Notifications

### GET /api/notifications

**Auth:** Any authenticated role
**Description:** List the calling user's notifications.

Query params: `isRead` (bool), `type`
Default sort: `createdAt desc`

Response `200 OK` — paginated with unread count:
```json
{
  "data": [
    {
      "id": "uuid",
      "title": "Your ticket has been assigned",
      "body": "Ticket 'Air conditioner not cooling' has been assigned to Tran Van B.",
      "type": "TICKET_ASSIGNED",
      "referenceId": "uuid",
      "referenceType": "Ticket",
      "isRead": false,
      "createdAt": "ISO8601"
    }
  ],
  "page": 0, "size": 20, "total": 10, "totalPages": 1,
  "unreadCount": 3
}
```

---

### PUT /api/notifications/{id}/read

**Auth:** Any authenticated role (own notifications only)
**Description:** Mark a single notification as read.

Response `204 No Content`
Errors: `403 FORBIDDEN` (notification belongs to another user)

---

### PUT /api/notifications/read-all

**Auth:** Any authenticated role
**Description:** Mark all of the calling user's notifications as read.

Response `204 No Content`

---

## 13. Files

### GET /api/files/presign

**Auth:** Any authenticated role
**Description:** Get a short-lived presigned GET URL for a MinIO object. The server validates that the requesting user has permission to access the referenced object's parent entity before issuing the URL.

Query params: `objectKey` (required)

Response `200 OK`:
```json
{
  "presignedUrl": "https://minio.internal/bucket/tickets/.../photo.jpg?X-Amz-...",
  "expiresAt": "2026-05-29T11:00:00Z"
}
```

Errors: `403 FORBIDDEN` (user does not have access to the object's parent entity), `404 NOT_FOUND` (object does not exist in MinIO)

---

## Appendix A: Status Transition Rules

### Ticket

```
NEW        → ASSIGNED     (by ADMIN when assignee is set)
NEW        → CANCELLED    (by ADMIN or RESIDENT who submitted)
ASSIGNED   → IN_PROGRESS  (by assigned TECHNICIAN or ADMIN)
ASSIGNED   → ASSIGNED     (by ADMIN — reassign to different assignee)
ASSIGNED   → CANCELLED    (by ADMIN)
IN_PROGRESS → DONE        (by assigned TECHNICIAN or ADMIN)
IN_PROGRESS → ASSIGNED    (by ADMIN — reassignment)
IN_PROGRESS → CANCELLED   (by ADMIN only)
DONE        → (terminal — resident can still add rating)
CANCELLED   → (terminal)
```

**Note for SUGGESTION_FEEDBACK:** These tickets may go directly from `NEW` to `DONE` by an ADMIN without an ASSIGNED/IN_PROGRESS intermediate step, since there is no external assignee — the admin simply reviews and closes.

### Amenity Booking

```
PENDING   → APPROVED    (by ADMIN, or auto-approved if requiresApproval = false)
PENDING   → REJECTED    (by ADMIN)
PENDING   → CANCELLED   (by RESIDENT or ADMIN)
APPROVED  → CANCELLED   (by RESIDENT before booking_date, or ADMIN)
APPROVED  → COMPLETED   (by BookingCompletionScheduler at end of booking window)
REJECTED  → (terminal)
CANCELLED → (terminal)
COMPLETED → (terminal)
```

### Contract

```
PENDING    → ACTIVE      (by ADMIN after contract is signed and activated)
ACTIVE     → EXPIRED     (by ContractExpiryScheduler when end_date passes)
ACTIVE     → TERMINATED  (by ADMIN)
EXPIRED    → (terminal)
TERMINATED → (terminal)
```

---

## Appendix B: Ticket Category Routing Reference

| Category | Assign to staff | Assign to contractor | SLA default | Priority default |
|----------|----------------|---------------------|-------------|-----------------|
| `MAINTENANCE_REPAIR` | Yes | Yes | 24 hours | MEDIUM |
| `COMPLAINT` | Yes | No | 48 hours | MEDIUM |
| `ADMINISTRATIVE` | Yes (ADMIN/TECHNICIAN) | No | 72 hours | LOW |
| `SUGGESTION_FEEDBACK` | Admin review queue only | No | 168 hours | LOW |
| `OTHER` | Yes | No | 48 hours | LOW |

The CHECK constraint `chk_tickets_contractor_category` in the database enforces that `assigned_to_contractor_id` can only be non-null when `category = 'MAINTENANCE_REPAIR'`. The service layer also validates this before attempting to persist, returning `400 VALIDATION_ERROR` with message `CONTRACTOR_ASSIGNMENT_NOT_ALLOWED`.

---

## Appendix C: Rate Limiting

| Endpoint | Limit | Key |
|----------|-------|-----|
| `POST /api/auth/login` | 10 req/min | Per IP address |
| `POST /api/auth/refresh` | 20 req/min | Per user ID |
| `POST /api/tickets/{id}/photos` | 10 req/min | Per user ID |
| All other authenticated endpoints | 120 req/min | Per user ID |

Rate limit exceeded responses return `429 RATE_LIMITED` with header `Retry-After: <seconds>`.
