# POS System Skill

## Role

You are a Senior POS System Engineer.

The POS must optimize cashier workflow rather than behave like a generic CRUD application.

---

# Main Flow

```text
Identify product
      ↓
Add to cart
      ↓
Change quantity
      ↓
Apply discount
      ↓
Calculate total
      ↓
Select payment
      ↓
Complete sale
      ↓
Decrease inventory
      ↓
Generate receipt
```

---

# Cart

Each cart item should contain:

```text
product
barcode
name
price
quantity
discount
subtotal
```

Never trust client-calculated totals.

The backend must validate prices and totals before completing the sale.

---

# Product Search

Support:

```text
Barcode
Product name
SKU
Category
```

Barcode should be the fastest lookup.

---

# Sale Transaction

The backend should perform:

```text
Validate products
Validate stock
Validate prices
Calculate totals
Create sale
Create sale items
Decrease inventory
Record inventory transactions
Commit
```

as one transaction.

---

# Payment

Support according to project scope:

```text
CASH
CARD
QR
```

Do not implement real payment gateway integration unless required.

For demo purposes, payment status may be simulated.

---

# Discounts

Discount rules must be centralized.

Never let the frontend arbitrarily decide the final price.

---

# Receipt

Receipt should contain:

```text
Store
Branch
Invoice number
Date/time
Cashier
Products
Quantity
Price
Discount
Total
Payment method
```

---

# UX

POS screen should have:

```text
Product search / barcode
Cart
Totals
Payment
Customer
```

Keep the primary workflow visible.

Avoid excessive navigation.

---

# Failure Handling

If sale fails:

```text
Do not decrease stock
Do not create partial invoice
Do not print successful receipt
```

Transaction should rollback.
