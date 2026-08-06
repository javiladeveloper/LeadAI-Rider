---
name: Jala Design System
colors:
  surface: '#f9f9f7'
  surface-dim: '#dadad8'
  surface-bright: '#f9f9f7'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f4f2'
  surface-container: '#eeeeec'
  surface-container-high: '#e8e8e6'
  surface-container-highest: '#e2e3e1'
  on-surface: '#1a1c1b'
  on-surface-variant: '#45474c'
  inverse-surface: '#2f3130'
  inverse-on-surface: '#f1f1ef'
  outline: '#76777c'
  outline-variant: '#c6c6cc'
  surface-tint: '#585e6c'
  primary: '#191f2a'
  on-primary: '#ffffff'
  primary-container: '#2e3440'
  on-primary-container: '#969cab'
  inverse-primary: '#c1c6d6'
  secondary: '#7b5900'
  on-secondary: '#ffffff'
  secondary-container: '#fdbf35'
  on-secondary-container: '#6e4f00'
  tertiary: '#2f1a00'
  on-tertiary: '#ffffff'
  tertiary-container: '#4c2d00'
  on-tertiary-container: '#d98b0c'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dde2f2'
  primary-fixed-dim: '#c1c6d6'
  on-primary-fixed: '#161c27'
  on-primary-fixed-variant: '#414753'
  secondary-fixed: '#ffdea4'
  secondary-fixed-dim: '#fabd32'
  on-secondary-fixed: '#261900'
  on-secondary-fixed-variant: '#5d4200'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb960'
  on-tertiary-fixed: '#2b1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f9f9f7'
  on-background: '#1a1c1b'
  surface-variant: '#e2e3e1'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 26px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  margin-mobile: 16px
  margin-desktop: 48px
  gutter: 16px
---

## Brand & Style
The design system is built for a Peruvian ride-sharing and logistics context, capturing the energetic and communal spirit of "Jala." The personality is casual, optimistic, and highly legible, optimized for high-glare outdoor environments and rapid interaction.

The style combines **Modern Corporate** reliability with **Tactile** warmth. It utilizes generous spacing, heavy-weight typography for visibility, and high-contrast color pairings. The interface prioritizes clarity for drivers and riders on the move, using a "street-smart" aesthetic that feels both professional and approachable.

## Colors
This design system utilizes a high-contrast palette designed for legibility.

- **Primary (Charcoal):** Used for core navigation, primary buttons, and heavy headings to ensure maximum contrast against the light background.
- **Secondary (Golden Yellow):** The brand's signature "energy" color. Used for earnings, rider highlights, and primary brand accents. **Constraint:** Always pair with Charcoal text/icons for AA+ accessibility.
- **Tertiary (Amber):** Specifically reserved for "Cash-to-Carry" amounts and attention-required states that aren't yet critical errors.
- **Semantic Colors:** Green for successful completion and Red for urgent warnings like low balance or expiring documents.
- **Backgrounds:** A Warm Off-white is used for the global background to reduce glare, while pure White is reserved for elevated interactive cards.

## Typography
The system uses **Plus Jakarta Sans** exclusively. Its soft, rounded terminals provide a friendly "street" vibe while maintaining a geometric structure that ensures readability under direct sunlight.

- **Headlines:** Use Bold or ExtraBold (700-800) weights to create a clear hierarchy.
- **Emphasis:** Critical data points (like fare prices or arrival times) should use `display-lg` or `headline-lg` to ensure they are the first thing a user sees.
- **Legibility:** Never go below a 12px font size for any functional information.

## Layout & Spacing
The layout follows a **Fluid Grid** model centered on an 8px spacing rhythm.

- **Mobile:** 4-column grid with 16px side margins.
- **Touch Targets:** Minimum touch target size is 48px, but 56px is preferred for primary driving actions to accommodate vibration and rapid movement.
- **Content Stacking:** Components should use a vertical rhythm of 16px (`md`) to separate distinct logical groups, while internal card elements should use 8px (`sm`) spacing.

## Elevation & Depth
Depth is conveyed through **Tonal Layers** and soft **Ambient Shadows**.

- **Level 0 (Background):** Warm Off-white (#FAFAF8).
- **Level 1 (Cards/Containers):** Pure White (#FFFFFF) with a very soft, diffused shadow (0px 4px 12px rgba(46, 52, 64, 0.05)).
- **Level 2 (Active/Floating):** Primary actions (like the "Go" button) use a slightly deeper shadow (0px 8px 20px rgba(46, 52, 64, 0.12)) to suggest interactability.
- **Outlines:** Use 1px solid borders in #E5E9F0 for inactive states or secondary inputs rather than shadows.

## Shapes
The shape language is defined by a friendly, high-radius approach.

- **Cards & Primary Buttons:** Use a consistent **16px (rounded-2xl)** corner radius.
- **Input Fields:** Match the 16px radius for a cohesive, "squishy" feel that aligns with the "Jala" brand.
- **Icons:** Use geometric containers with circular backgrounds for status indicators.

## Components

### Buttons
- **Primary:** Charcoal background, White text. 16px radius, 56px height.
- **Accent (Earnings/Rider):** Golden Yellow background, Charcoal text.
- **Critical:** Red background, White text.

### Cards
- **Structure:** White background, 16px radius, soft shadow.
- **Content:** Use 16px internal padding. Headers within cards should use `title-md`.

### Inputs
- **Text Fields:** 16px radius, 1px Charcoal border (when active), 56px height.
- **Labels:** Floating labels using `label-sm` weight.

### Icons & Imagery
- **Functional Icons:** Use standard geometric line icons (2px stroke) for navigation.
- **Expressive Icons:** Use Emojis for feedback states (e.g., 🥳 for success, ⚠️ for attention, 💸 for earnings).
- **Logo:** The "»»" (Three forward chevrons) must always point right, signifying progress and movement.

### Chips & Badges
- **Status Badges:** Small 8px radius pills. Success (Green bg/White text), Warning (Amber bg/Charcoal text), Urgent (Red bg/White text).
