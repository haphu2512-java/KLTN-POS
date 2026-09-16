# Barcode & Hardware Integration Skill

## Role

You are responsible for integrating barcode scanners, phones, and receipt printers with the Electron POS.

---

# Barcode Principle

Barcode is simply an identifier.

The business system should receive:

```text
8938505971234
```

and resolve it to a product.

Business logic must not depend on a particular hardware device.

---

# Supported Input Methods

The system should be designed to support:

```text
USB HID Scanner
Bluetooth Scanner
Phone Camera
Manual Keyboard Input
```

---

# USB HID

Treat a standard HID scanner similarly to keyboard input.

Typical flow:

```text
Scanner
   ↓
USB
   ↓
Windows
   ↓
Keyboard input
   ↓
Electron
   ↓
Barcode lookup
```

Do not implement low-level USB communication unless the actual device requires it.

---

# Scanner Input Buffer

The application should be able to detect a rapid sequence of characters and identify the barcode submission.

Handle:

```text
characters
Enter
timeout
focus
```

without interfering with normal text input.

---

# Phone Scanner

Phone scanning may use:

```text
Phone Camera
     ↓
Mobile Web/App
     ↓
HTTP/WebSocket
     ↓
Spring Boot
     ↓
Electron
```

The phone should send the barcode rather than directly modifying inventory.

---

# Printer

Printer integration belongs primarily to Electron.

Use an isolated service:

```text
PrinterService
```

Possible protocol:

```text
ESC/POS
```

Printer failures should not corrupt the sale transaction.

---

# Hardware Abstraction

Use interfaces/adapters where useful:

```text
BarcodeInput
PrinterService
```

Possible implementations:

```text
HidBarcodeInput
PhoneBarcodeInput
ThermalPrinterService
MockPrinterService
```

This allows hardware to be replaced during development/testing.

---

# Testing

Always provide a mock mode.

Development should work without physical hardware.

Example:

```text
Mock barcode
Mock printer
```

The project must not become impossible to develop because hardware is unavailable.
