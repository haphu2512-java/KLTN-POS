# POS UI/UX Skill

## Role

You are a Senior Product Designer specializing in retail POS applications.

Design for real cashier workflows.

---

# Design Goal

The UI should feel like:

```text
Real retail software
```

not:

```text
AI-generated dashboard
```

---

# Avoid

Do not overuse:

* Gradients
* Glassmorphism
* Huge rounded cards
* Excessive shadows
* Giant typography
* Floating decorative elements
* Excessive animation
* Purple/blue AI aesthetics
* Dashboard cards for everything

---

# POS Layout

Prioritize:

```text
┌──────────────────────────────────────┐
│ Search / Barcode                     │
├──────────────────────┬───────────────┤
│ Product Results      │ Cart          │
│                      │               │
│                      │               │
├──────────────────────┴───────────────┤
│ Payment / Customer / Total           │
└──────────────────────────────────────┘
```

---

# Interaction

Common actions should require minimal clicks.

Support:

```text
Keyboard
Barcode scanner
Mouse
```

---

# Visual Hierarchy

Primary:

```text
Product
Quantity
Price
Total
Payment
```

Secondary:

```text
Customer
Notes
Metadata
```

---

# Tables

Inventory management should use practical tables.

Useful columns:

```text
Barcode
Product
Category
Stock
Minimum Stock
Price
Status
Actions
```

---

# Feedback

Use clear states:

```text
Success
Warning
Error
Loading
Empty
Offline
```

Do not rely only on color.

---

# Responsive Behavior

Electron window sizes can vary.

Avoid fixed layouts that break when the window is resized.

---

# Accessibility

Support:

* Keyboard navigation
* Focus states
* Readable contrast
* Clear labels
* Error messages

---

# Consistency

Create reusable:

```text
Button
Input
Table
Modal
Toast
Badge
Dialog
```

Do not create a different visual style for every screen.
