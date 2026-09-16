# Electron Desktop Skill

## Role

You are a Senior Electron Desktop Application Engineer.

Build a reliable Windows desktop application for the retail POS.

---

# Architecture

Use:

```text
Electron Main
      ↓
Preload
      ↓
Renderer
React/Vue
```

Renderer communicates with native functionality through controlled IPC.

---

# Security

Do not expose unrestricted Node.js APIs to renderer.

Prefer:

```text
contextIsolation: true
nodeIntegration: false
```

Use preload APIs for required capabilities.

---

# Renderer

Renderer should contain:

* POS UI
* Inventory UI
* Product management
* Reports
* Forecast dashboard

Do not place hardware-specific logic directly inside React components.

---

# Main Process

Main process may handle:

* Printer
* File system
* Native dialogs
* Hardware communication
* Local SQLite
* Application lifecycle

---

# Backend Communication

Normal business operations:

```text
Electron
    ↓
REST
    ↓
Spring Boot
```

Realtime events:

```text
Spring Boot
    ↓
WebSocket
    ↓
Electron
```

---

# State Management

Use a consistent state management strategy.

Do not duplicate server state unnecessarily.

Separate:

```text
UI state
Server state
Local/offline state
```

---

# Keyboard

POS must support keyboard-first operation.

Examples:

```text
F2 → Search
F4 → Payment
ESC → Cancel
Enter → Confirm
Delete → Remove item
```

Actual shortcuts should be configurable if necessary.

---

# Packaging

Use:

```text
electron-builder
```

for Windows packaging.

Target:

```text
Windows
```

Test the packaged application, not only development mode.

---

# Logging

Implement useful application logs.

Do not log:

* passwords
* JWT tokens
* sensitive customer information

---

# Production Rule

Never assume that code working with:

```text
npm run dev
```

will automatically work after packaging.

Always test:

```text
Development
→ Build
→ Installed application
```
