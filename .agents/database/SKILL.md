# Database Design Skill

## Role

You are a Senior Database Engineer responsible for designing and maintaining the database of the retail POS and Inventory Management System.

Database:

* MySQL
* JPA/Hibernate
* Spring Data JPA

The database must prioritize:

* Data integrity
* Consistency
* Traceability
* Transaction safety
* Maintainability

---

## 1. Core Entities

The system may contain:

```text
User
Role
Branch
Product
Category
Brand
Supplier

Inventory
InventoryTransaction

Purchase
PurchaseItem

Sale
SaleItem

Customer
Payment
Promotion

StockTransfer
StockCheck

Forecast
RestockRecommendation
```

Do not create unnecessary tables.

Every table must represent a real business concept.

---

## 2. Relationships

Typical relationships:

```text
Branch
  └── Inventory

Product
  ├── Inventory
  ├── SaleItem
  ├── PurchaseItem
  └── InventoryTransaction

Sale
  └── SaleItem

Purchase
  └── PurchaseItem

Supplier
  └── Purchase
```

Use foreign keys to enforce relationships.

---

## 3. Primary Keys

Prefer:

```text
BIGINT
```

or UUID when there is a clear reason.

Do not mix ID strategies unnecessarily.

---

## 4. Money

Never use:

```text
float
double
```

for money.

Prefer:

```text
DECIMAL
```

Example:

```text
DECIMAL(15,2)
```

---

## 5. Inventory Modeling

Inventory is branch-specific.

Do NOT store only:

```text
Product.quantity
```

Instead:

```text
Product
     ↓
Inventory
     ├── Branch A → quantity
     ├── Branch B → quantity
     └── Branch C → quantity
```

Inventory should identify:

```text
product_id
branch_id
quantity
minimum_stock
```

Use a unique constraint:

```text
(product_id, branch_id)
```

---

## 6. Inventory Transactions

Every important inventory modification should be traceable.

Example:

```text
SALE
PURCHASE
RETURN
ADJUSTMENT
TRANSFER_IN
TRANSFER_OUT
```

Record:

```text
product
branch
quantity
transaction type
reference
created_at
```

Never modify inventory silently.

---

## 7. Indexes

Index frequently queried fields:

```text
barcode
product name
branch_id
created_at
sale date
supplier_id
```

Do not blindly index every column.

---

## 8. Soft Delete

Use soft deletion only where historical records must remain valid.

Products referenced by historical sales should generally not be physically deleted.

Prefer:

```text
active = false
```

when necessary.

---

## 9. Schema Changes

Never manually modify production schema without considering migration/versioning.

Prefer:

```text
Flyway
```

or another migration mechanism if the project uses one.

Each structural change should be reproducible.

---

## 10. Data Integrity

Use:

* NOT NULL
* UNIQUE
* FOREIGN KEY
* CHECK where supported
* appropriate data types

Do not rely entirely on application code to maintain database integrity.

---

## 11. Before Changing Database

Always inspect:

1. Existing tables
2. Existing entities
3. Existing relationships
4. Existing queries
5. Existing data

Never rename/delete a field without checking usages.

---

## 12. Database Change Report

Whenever modifying the schema, report:

```text
Changed tables:
Created tables:
Removed fields:
Added fields:
Added indexes:
Added constraints:
Migration required:
Potential compatibility issues:
```
