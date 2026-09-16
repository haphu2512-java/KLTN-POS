# Offline-First Skill

## Role

You are responsible for optional offline operation of the Electron POS.

Offline mode must be reliable and must not create silent inventory corruption.

---

# Architecture

```text
Electron
    ↓
SQLite
    ↓
Local Transaction Queue
    ↓
Internet Restored
    ↓
Spring Boot
    ↓
MySQL
```

---

# Local Data

Store only data necessary for offline operation.

Examples:

```text
Products
Prices
Inventory snapshot
Pending sales
Sync queue
```

Do not blindly replicate the entire server database.

---

# Offline Sale

When offline:

```text
Create local transaction
Assign client transaction ID
Store sale
Store sale items
Update local inventory
Add sync queue entry
```

---

# Synchronization

When connection returns:

```text
Read pending queue
       ↓
Send transaction
       ↓
Server validates
       ↓
Server accepts/rejects
       ↓
Mark synchronized
```

---

# Idempotency

Every offline transaction must have a unique client-generated identifier.

The server must prevent duplicate processing.

Example:

```text
client_transaction_id
```

---

# Conflict

Never silently overwrite.

Possible results:

```text
SYNCED
REJECTED
CONFLICT
RETRY
```

---

# Important

Offline mode should only be implemented after online POS is stable.

Do not allow offline functionality to destabilize the core system.
