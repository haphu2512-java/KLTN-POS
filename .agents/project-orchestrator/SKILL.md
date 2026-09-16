# Project Orchestrator Skill

## Role

You are the Project Orchestrator for the graduation project:

"POS and Inventory Management System with AI Demand Forecasting for Small Retail Stores."

Your job is to determine which specialized skill should be applied to a task.

---

# Project Stack

```text
Electron + React/Vue
        ↓
REST / WebSocket
        ↓
Java Spring Boot
        ↓
MySQL
```

Optional:

```text
SQLite
WebSocket
Barcode Scanner
Phone Scanner
ESC/POS Printer
Forecasting Module
```

---

# Skill Routing

Use:

```text
Database/schema
        → database-design

Spring Boot/API/business logic
        → springboot-backend

Login/JWT/roles/security
        → security-jwt

Electron/IPC/native desktop
        → electron-desktop

Sales/cart/payment
        → pos-system

Barcode/printer/device
        → barcode-hardware

Stock/purchase/inventory
        → inventory-management

WebSocket/multi-branch
        → realtime-sync

Offline/SQLite/sync queue
        → offline-first

Forecasting/MAE/RMSE/reorder
        → ai-forecasting

Tests/bugs/QA
        → testing

UI/POS interface
        → ui-ux-pos

Reports/thesis/technical documentation
        → thesis-documentation
```

---

# Multiple Skills

A task may require multiple skills.

Example:

```text
"Add barcode scanning to POS"
```

Use:

```text
pos-system
+
barcode-hardware
+
electron-desktop
+
springboot-backend
```

Example:

```text
"Create low-stock AI recommendation"
```

Use:

```text
inventory-management
+
ai-forecasting
+
springboot-backend
+
database-design
+
ui-ux-pos
```

---

# Dependency Order

Respect:

```text
Database
   ↓
Backend
   ↓
Frontend
   ↓
Integration
   ↓
Testing
```

Do not implement frontend functionality against nonexistent APIs unless explicitly working with mocks.

---

# Change Impact

Before changing a shared component, identify:

```text
Database impact
Backend impact
Frontend impact
Hardware impact
Testing impact
Documentation impact
```

---

# MVP Priority

Prioritize:

```text
Authentication
→ Product
→ Inventory
→ POS
→ Sales
→ Receipt
→ Reports
```

Then:

```text
Barcode
→ Realtime
→ Forecast
→ Multi-branch
→ Offline
```

---

# Decision Rule

When several implementations are possible:

Prefer the solution that is:

1. Simplest
2. Reliable
3. Easy to test
4. Easy to explain
5. Compatible with the existing architecture
6. Appropriate for the project scope

Do not choose technology merely because it is fashionable.

---

# Before Coding

Always output internally:

```text
Task
Affected modules
Required skills
Files likely affected
Database impact
API impact
Testing impact
```

Then implement.

---

# After Coding

Verify:

```text
Compilation
Tests
API
Database
UI
Integration
```

Then provide:

```text
Changed files
What changed
Why
How to test
Known limitations
```
