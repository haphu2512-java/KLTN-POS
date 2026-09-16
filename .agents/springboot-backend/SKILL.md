# Spring Boot Backend Skill

## Role

You are a Senior Java/Spring Boot Backend Engineer.

Build a modular monolith REST API for the retail POS system.

---

# Architecture

Use:

```text
Controller
    ↓
DTO
    ↓
Service
    ↓
Repository
    ↓
Entity
    ↓
MySQL
```

Business logic belongs primarily in Service.

Controllers should remain thin.

Repositories should focus on data access.

---

# Package Structure

Prefer:

```text
com.example.pos
├── config
├── security
├── common
├── user
├── product
├── inventory
├── sale
├── purchase
├── customer
├── branch
├── report
└── forecast
```

Feature-based organization is preferred over one huge global Controller/Service folder.

---

# DTO

Do not expose entities directly by default.

Use:

```text
Request DTO
Response DTO
```

Example:

```text
CreateProductRequest
UpdateProductRequest
ProductResponse
```

---

# REST

Use predictable endpoints:

```text
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

Use proper HTTP status codes.

---

# Validation

Use:

```text
jakarta.validation
```

Examples:

```text
@NotBlank
@NotNull
@Positive
@Size
```

Validate business rules in Service.

---

# Transactions

Use:

```text
@Transactional
```

for business operations that modify multiple records.

Examples:

```text
Complete sale
Receive purchase
Transfer inventory
Stock adjustment
Return
```

---

# Error Handling

Use centralized exception handling:

```text
@RestControllerAdvice
```

Return consistent responses.

Example:

```json
{
  "success": false,
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product not found"
}
```

Never return stack traces to clients.

---

# Concurrency

Inventory operations must consider concurrent sales.

Do not assume:

```text
read quantity
→ calculate
→ write quantity
```

is always safe.

Use appropriate:

* Transactions
* Locking
* Optimistic versioning
* Atomic update queries

depending on the situation.

---

# Scheduler

Spring Scheduler may be used for:

```text
Low-stock detection
Forecast generation
Report aggregation
Data cleanup
```

Do not use Scheduler for operations that require immediate user response.

---

# API Documentation

When appropriate, use OpenAPI/Swagger.

Every major endpoint should document:

* Request
* Response
* Authentication
* Errors
* Example payload
