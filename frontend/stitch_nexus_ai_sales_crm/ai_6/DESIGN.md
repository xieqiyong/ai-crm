---
name: AI Intelligent Sales Platform
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#5a4136'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#8e7164'
  outline-variant: '#e2bfb0'
  surface-tint: '#a04100'
  primary: '#a04100'
  on-primary: '#ffffff'
  primary-container: '#ff6b00'
  on-primary-container: '#572000'
  inverse-primary: '#ffb693'
  secondary: '#555f6f'
  on-secondary: '#ffffff'
  secondary-container: '#d6e0f3'
  on-secondary-container: '#596373'
  tertiary: '#494bd6'
  on-tertiary: '#ffffff'
  tertiary-container: '#8a8dff'
  on-tertiary-container: '#1100ab'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbcc'
  primary-fixed-dim: '#ffb693'
  on-primary-fixed: '#351000'
  on-primary-fixed-variant: '#7a3000'
  secondary-fixed: '#d9e3f6'
  secondary-fixed-dim: '#bdc7d9'
  on-secondary-fixed: '#121c2a'
  on-secondary-fixed-variant: '#3d4756'
  tertiary-fixed: '#e1e0ff'
  tertiary-fixed-dim: '#c0c1ff'
  on-tertiary-fixed: '#07006c'
  on-tertiary-fixed-variant: '#2f2ebe'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 24px
  gutter: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
---

## Brand & Style
The brand personality is high-energy, decisive, and forward-thinking. It targets high-performance sales teams and growth-oriented enterprises that require speed and data-driven insights. The UI is designed to evoke a sense of proactive momentum and technological sophistication.

The design system utilizes a **Corporate Modern** style with **Glassmorphic** accents for AI-driven modules. This approach balances the reliability of enterprise SaaS with the innovative nature of artificial intelligence. By using a clean, white-space-heavy minimalist foundation, the system ensures that complex sales data remains legible and actionable, while vibrant accents highlight the platform's unique AI capabilities.

## Colors
The palette is centered around **Vibrant Orange**, a color that signals energy and motivates action. This is used exclusively for primary calls-to-action and key brand touchpoints.

- **Primary:** Vibrant Orange (#FF6B00) for "closing" actions and primary navigation highlights.
- **Surface:** A combination of Pure White (#FFFFFF) for cards and Light Gray (#F8F9FA) for the application background to create clear visual separation.
- **Status:** Standard semantic colors (Red, Green, Yellow) are utilized for deal health, pipeline status, and risk alerts.
- **AI Accents:** AI-powered features are distinguished by a signature light orange gradient and subtle glow effects (0px 0px 12px rgba(255, 107, 0, 0.15)) to separate automated intelligence from manual data entry.

## Typography
This design system uses **Inter** across all levels to maintain a systematic, utilitarian, and highly legible interface. The type hierarchy is strictly enforced to help users scan large amounts of CRM data quickly.

- **Headlines:** Use semi-bold and bold weights with tighter letter-spacing to create a strong visual anchor for page titles and card headers.
- **Body:** Standardized at 16px for optimal readability in data-heavy views.
- **Labels:** Used for table headers and metadata, often employing a slightly heavier weight to distinguish them from editable content.

## Layout & Spacing
The layout follows a **Fluid Grid** model with a 12-column structure for desktop. 

- **Rhythm:** An 8px linear scale governs all spacing (8, 16, 24, 32, 48, 64).
- **Margins:** Desktop views use 24px outer margins, scaling down to 16px on mobile.
- **Reflow:** On tablet, the 12-column grid collapses to 8 columns. On mobile, elements span 4 columns (full width) or 2 columns for smaller inputs.
- **Safe Areas:** High-density data tables utilize a "Compact" mode with 8px cell padding, while dashboard cards use "Spacious" 24px padding to ensure clarity.

## Elevation & Depth
Hierarchy is established through **Tonal Layers** and **Ambient Shadows**.

- **Level 0 (Background):** Light Gray (#F8F9FA).
- **Level 1 (Cards/Surface):** Pure White (#FFFFFF) with a 1px border (#E5E7EB) and a very soft, diffused shadow: `0px 4px 6px -1px rgba(0, 0, 0, 0.05)`.
- **Level 2 (Dropdowns/Modals):** Pure White with a more pronounced shadow: `0px 10px 15px -3px rgba(0, 0, 0, 0.1)`.
- **AI Elevation:** AI modules utilize a "Glassmorphic" stack—a semi-transparent white surface with a 20px backdrop blur and a 1px inner border using the primary orange at 20% opacity.

## Shapes
The shape language is professional and modern, using **Rounded** corners to feel approachable yet structured.

- **Standard Elements:** Buttons, input fields, and small UI components use a 0.5rem (8px) radius.
- **Large Elements:** Dashboard cards and modal containers use a 1rem (16px) radius to soften the large surface areas.
- **Interactive Elements:** Checkboxes and radio buttons maintain a consistent 4px and circular radius respectively.

## Components
- **Buttons:** 
  - *Primary:* Solid Vibrant Orange with white text. High contrast, 8px radius.
  - *Secondary:* Outlined with a 1px border (#D1D5DB) and dark gray text.
  - *AI Action:* Solid primary color with a subtle outer glow.
- **Cards:** White background, 16px radius, subtle shadow. AI cards feature a thin orange top-border (2px) to indicate intelligence-enhanced data.
- **Inputs:** 8px radius, 1px neutral border. On focus, the border changes to the primary orange with a 2px soft outer ring.
- **Chips/Status:** Used for lead stages. They feature a desaturated background of the status color with high-contrast text for accessibility (e.g., Light Red background with Dark Red text).
- **Lists:** Clean rows with 1px bottom borders. Hover states use the background neutral color (#F8F9FA) to indicate interactivity.
- **AI Insight Component:** A specific component for AI "nudges"—uses a pale orange background (#FFF7ED), an icon with a glow, and an 8px radius.