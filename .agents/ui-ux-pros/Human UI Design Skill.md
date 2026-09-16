# Human UI Design Skill

## Purpose

Design and refine web interfaces so they feel **human-designed, intentional, practical, trustworthy, and production-ready**, rather than AI-generated, overly polished, futuristic, or generic SaaS.

The goal is NOT to make the interface less beautiful.

The goal is to make the interface feel like it was designed by an experienced human designer who understands:
- the product
- the users
- visual hierarchy
- usability
- brand identity
- content
- real-world constraints

---

# Core Principle

Always follow:

> **Human-designed > AI-perfect**

A good interface does not need every element to look impressive.

Prefer:
- clarity over decoration
- hierarchy over symmetry
- usability over visual effects
- consistency over repetition
- subtlety over spectacle
- information over decoration
- personality over generic trends

---

# 1. Detect AI-Generated UI Patterns

Before modifying the interface, inspect the existing design and identify excessive use of:

- large centered hero sections
- oversized headings
- excessive gradients
- purple/blue futuristic palettes
- excessive orange/red gradients
- glassmorphism
- excessive blur
- glowing elements
- neon effects
- huge rounded cards
- excessive drop shadows
- floating cards
- excessive icons
- icon + circle + card patterns
- every section centered
- every element perfectly symmetrical
- excessive whitespace
- excessive animations
- excessive hover effects
- gradient text everywhere
- identical cards repeated throughout the page
- excessive badges/pills
- decorative elements without functional purpose
- generic "AI SaaS" layouts

If several of these patterns exist, reduce them systematically.

---

# 2. Visual Philosophy

Use a visual language similar to a mature production website.

The interface should feel:

- professional
- credible
- practical
- slightly imperfect
- structured
- readable
- restrained
- brand-specific
- human

Avoid making every section look like a Dribbble/Behance concept.

The final result should look like a real product that has evolved through actual design decisions.

---

# 3. Layout

Do NOT automatically center everything.

Prefer:
- left-aligned headings where appropriate
- asymmetric layouts when useful
- varied section structures
- natural content widths
- realistic spacing
- strong visual hierarchy

Do not force every section into:

Heading
↓
Subtitle
↓
3 Cards
↓
CTA

Avoid repetitive page structures.

Allow some sections to be:
- text-heavy
- data-heavy
- compact
- visually quiet
- cardless
- border-based

---

# 4. Spacing

Do not use excessive whitespace just because modern UI trends encourage it.

Use spacing based on content hierarchy.

Typical principles:

- related elements → smaller spacing
- separate groups → larger spacing
- major sections → clearly larger spacing
- dense information → compact spacing

Avoid making the page feel empty.

---

# 5. Border Radius

Do not round everything excessively.

Prefer realistic values:

- small controls: 6–8px
- buttons: 6–10px
- inputs: 6–8px
- cards: 8–14px
- large containers: 12–16px

Do not automatically use:
- 24px
- 32px
- 40px
- pill shapes

Use rounded corners only where they improve usability or hierarchy.

---

# 6. Shadows

Use shadows sparingly.

Prefer:

```css
border: 1px solid #e5e7eb;
```

over:

```css
box-shadow: 0 20px 60px rgba(...);
```

Use subtle shadows only when an element genuinely needs elevation.

Avoid making every card appear to float.

---

# 7. Colors

Use a restrained color system.

Recommended structure:

```text
Primary brand color
Secondary accent
Main text
Secondary text
Border
Background
Surface
Success
Warning
Error
```

Do not introduce random colors simply to make the page visually interesting.

Avoid excessive:
- purple
- cyan
- blue glow
- neon
- multi-color gradients

If the brand uses orange, for example, orange should feel intentional rather than appearing everywhere.

---

# 8. Gradients

Gradients are allowed but should be treated as an accent.

Good:

- one hero accent
- one important heading
- one primary CTA
- subtle background decoration

Bad:

- every card has gradient
- every button has gradient
- gradient borders
- gradient background everywhere
- gradient text everywhere

When a gradient does not provide meaningful visual hierarchy, remove it.

---

# 9. Typography

Typography should feel editorial and professional.

Do not rely on extremely large font sizes to create impact.

Prefer:
- readable body text
- moderate heading sizes
- meaningful font-weight hierarchy
- comfortable line-height
- realistic letter-spacing

Avoid making every heading:
- extremely bold
- uppercase
- gradient
- huge

Typography hierarchy should communicate information, not decoration.

---

# 10. Icons

Icons must have a purpose.

Before adding an icon, ask:

> Does this icon improve comprehension?

If not, do not add it.

Avoid:

```text
Icon
+
Colored circle
+
Rounded card
+
Gradient
```

repeated across the page.

Use icons primarily for:
- actions
- navigation
- status
- categorization
- visual scanning

---

# 11. Cards

Cards should represent actual information groups.

Do not create cards purely because "modern websites use cards."

Use:
- simple sections
- borders
- tables
- lists
- dividers
- typography
when they communicate information better.

Card styles should not all be identical.

---

# 12. Buttons

Buttons should look functional.

Avoid excessive:
- gradients
- glow
- pill shapes
- animation
- oversized padding

Prefer clear hierarchy:

```text
Primary action
Secondary action
Tertiary/text action
```

The primary button should be visually obvious without looking futuristic.

---

# 13. Animation

Animation should communicate state or improve interaction.

Good:
- subtle hover transition
- loading state
- progress animation
- small entrance transition
- subtle heading animation

Bad:
- everything floating
- everything moving
- excessive parallax
- glowing animation
- continuous card movement
- excessive scaling
- decorative particles

Use fewer animations than you initially think are necessary.

---

# 14. Imperfection Rule

Do not intentionally make the interface ugly.

Instead, introduce **natural variation**.

Examples:

- different content lengths
- different card structures
- varied spacing where appropriate
- non-identical section layouts
- restrained decoration
- realistic data presentation
- natural text hierarchy

The interface should feel designed, not algorithmically repeated.

---

# 15. Real Product Rule

Whenever the interface displays data, prioritize real information.

For example, instead of:

```text
96
Excellent
```

prefer:

```text
SSL Certificate

Status       Valid
Issuer       Let's Encrypt
Expires      18 Oct 2026
Remaining    37 days

SSL Score    96/100
```

Realistic information architecture makes a product feel significantly more authentic.

---

# 16. Dashboard / Tool Interfaces

For tools such as:
- SEO audit
- website checker
- domain checker
- analytics
- security tools
- performance tools

prioritize:

1. Information
2. Status
3. Result
4. Explanation
5. Action

Do not prioritize decorative cards.

The UI should feel like a real professional tool.

---

# 17. Responsive Design

Do not simply shrink desktop UI.

Mobile should be intentionally designed.

Check:
- spacing
- text wrapping
- button width
- navigation
- card density
- tables
- forms
- hierarchy

Avoid unnecessary horizontal scrolling.

---

# 18. Accessibility

Maintain:
- readable contrast
- visible focus states
- keyboard usability
- appropriate button labels
- semantic HTML
- reasonable text size

Do not sacrifice usability for visual aesthetics.

---

# 19. Existing Project Rule

When modifying an existing project:

1. Understand the current architecture first.
2. Identify the existing design system.
3. Preserve business logic.
4. Preserve PHP/MVC logic.
5. Preserve JavaScript behavior.
6. Preserve API integrations.
7. Preserve existing IDs/classes when they are dependencies.
8. Modify only what is necessary.
9. Remove redundant CSS carefully.
10. Do not rewrite the entire project unnecessarily.

The goal is **refinement**, not destruction.

---

# 20. Final Human-UI Checklist

Before finishing, evaluate the interface:

### Visual
- [ ] Does it look like a real website?
- [ ] Is the color palette restrained?
- [ ] Are gradients limited?
- [ ] Are shadows subtle?
- [ ] Are cards not excessively rounded?
- [ ] Is the interface free from excessive glow?
- [ ] Are icons used intentionally?

### Layout
- [ ] Is everything not centered?
- [ ] Are section layouts varied?
- [ ] Is whitespace reasonable?
- [ ] Is hierarchy clear?
- [ ] Does the page avoid repetitive card patterns?

### Typography
- [ ] Are headings not excessively huge?
- [ ] Are weights varied naturally?
- [ ] Is body text comfortable to read?
- [ ] Is typography appropriate for the brand?

### Interaction
- [ ] Are animations subtle?
- [ ] Do hover effects have a purpose?
- [ ] Are buttons functional-looking?
- [ ] Are focus/error/loading states handled?

### Authenticity
- [ ] Does the interface avoid the generic AI/SaaS look?
- [ ] Does it have its own visual personality?
- [ ] Does information look realistic?
- [ ] Does the design feel intentional rather than algorithmically generated?

---

# Golden Rule

Before adding any visual effect, ask:

> **"Would an experienced human designer add this because it improves the product, or am I adding it because AI-generated interfaces usually look like this?"**

If the answer is the second one, remove it.

The final interface should feel:

**Professional + Human + Practical + Brand-specific + Production-ready**

and NOT:

**AI + Futuristic + Generic SaaS + Over-designed**