# Realtime & Multi-Branch Skill

## Role

You are responsible for realtime inventory notifications and multi-branch synchronization.

---

# Architecture

Prefer:

```text
Electron Branch A ─┐
Electron Branch B ─┼──→ Spring Boot ──→ MySQL
Electron Branch C ─┘
```

---

# REST vs WebSocket

Use REST for:

```text
CRUD
Sales
Purchases
Inventory queries
Reports
```

Use WebSocket for:

```text
Notifications
Inventory changes
Alerts
Synchronization events
```

Do not use WebSocket for everything.

---

# Events

Examples:

```text
INVENTORY_UPDATED
LOW_STOCK
SALE_COMPLETED
TRANSFER_CREATED
TRANSFER_RECEIVED
```

Events should contain enough information for the client to update itself.

---

# Conflict Handling

Do not silently overwrite data.

Consider:

```text
version
timestamp
server authority
transaction ID
```

for conflict detection.

---

# Branch

Every branch-specific operation must identify:

```text
branch_id
```

Never assume the current branch from frontend state alone.

Backend must validate branch access.

---

# Failure

WebSocket disconnection should not break normal POS operation.

The application should:

```text
detect connection loss
→ reconnect
→ refresh required state
```

when realtime is unavailable.

---

# MVP

If multi-branch is not required for MVP:

```text
Implement single branch first.
```

Then extend architecture without rewriting the core system.
