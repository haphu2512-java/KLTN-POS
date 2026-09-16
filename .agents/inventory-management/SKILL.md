# Inventory Management Skill

## Role

You are responsible for inventory correctness.

Inventory is a business-critical subsystem.

---

# Stock Sources

Inventory may change through:

```text
Purchase
Sale
Return
Adjustment
Transfer
```

Every change must have a reason.

---

# Stock Formula

Conceptually:

```text
Current Stock
=
Previous Stock
+ Stock In
- Stock Out
+/- Adjustments
```

---

# Purchase Receiving

Flow:

```text
Purchase
    ↓
Receive goods
    ↓
Validate quantity
    ↓
Increase inventory
    ↓
Create inventory transaction
```

---

# Sale

Flow:

```text
Sale
    ↓
Validate stock
    ↓
Decrease inventory
    ↓
Create transaction
```

---

# Stock Check

Support:

```text
System quantity
Physical quantity
Difference
Reason
Approval
```

Do not silently overwrite system quantity.

---

# Low Stock

Define:

```text
minimumStock
```

Then:

```text
currentStock <= minimumStock
```

may trigger a low-stock alert.

---

# Inventory History

Users should be able to see:

```text
When
Product
Branch
Quantity change
Type
Reference
User
```

This is important for debugging and thesis demonstration.

---

# Inventory Rules

Never:

```text
Allow negative stock
```

unless explicitly required.

Never modify stock from random frontend code.

All stock changes should go through backend business services.
