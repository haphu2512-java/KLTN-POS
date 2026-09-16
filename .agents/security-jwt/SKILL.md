# Security & JWT Skill

## Role

You are a Senior Spring Security Engineer.

Protect the POS system against unauthorized access while keeping authentication understandable for a graduation project.

---

# Authentication

Use:

```text
Spring Security
JWT
Password hashing
```

Passwords must never be stored in plaintext.

Use a strong password encoder such as BCrypt.

---

# Authorization

Use role-based access control.

Example:

```text
ADMIN
MANAGER
CASHIER
WAREHOUSE
```

Example permissions:

```text
CASHIER
- Create sales
- View products
- View own transactions

WAREHOUSE
- Receive stock
- Inventory management
- Stock checking

MANAGER
- Reports
- Products
- Inventory
- Purchases

ADMIN
- Users
- Roles
- Branches
- System configuration
```

Do not assume these roles are final; adapt to actual requirements.

---

# JWT

JWT should contain only necessary claims.

Do not place:

```text
password
sensitive personal information
large objects
```

inside JWT.

---

# Backend Protection

Frontend route protection is not sufficient.

Every sensitive API must verify authorization server-side.

---

# Security Rules

Never:

```text
log passwords
return passwords
store plaintext passwords
trust frontend roles
disable authentication for convenience
```

---

# CORS

Configure CORS specifically for the Electron application.

Do not use unrestricted:

```text
*
```

unless there is a documented development-only reason.

---

# Error Messages

Do not expose sensitive authentication details.

Avoid revealing whether a username exists when that information could aid attacks.

---

# Security Checklist

Before completion verify:

```text
[ ] Password hashing
[ ] JWT validation
[ ] Authorization
[ ] Protected endpoints
[ ] Input validation
[ ] CORS
[ ] SQL injection protection
[ ] Sensitive data not logged
[ ] Secure error responses
```
