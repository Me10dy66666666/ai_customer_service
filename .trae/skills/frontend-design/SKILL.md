---
name: "frontend-design"
description: "Impeccable frontend design skill. Provides domain-specific guidance on typography, color, layout, motion, and interaction. Includes commands like /audit, /polish, /critique to refine UI/UX. Invoke when working on frontend tasks."
---

# Frontend Design (Impeccable)

This skill provides expert guidance for frontend design and implementation, based on the Impeccable Design Language. It helps you avoid common pitfalls and create polished, professional UIs.

## Core Principles

### 1. Typography
- **Modular Scales**: Use consistent type scales (e.g., 1.250 Major Third).
- **Font Pairing**: Combine fonts thoughtfully (e.g., serif headings + sans-serif body).
- **Avoid Generic Fonts**: Do not default to Inter, Arial, or system fonts unless specifically requested. Choose fonts that match the brand personality.

### 2. Color & Contrast
- **OKLCH Color Space**: Prefer OKLCH for perceptually uniform colors.
- **Accessible Contrast**: Ensure WCAG AA compliance (4.5:1 for normal text).
- **Tinted Neutrals**: Avoid pure gray (#808080) on colored backgrounds. Mix in the background hue.
- **Avoid Pure Black**: Never use #000000. Use a very dark gray or tinted black (e.g., #1a1a1a).

### 3. Spatial Design
- **Consistent Spacing**: Use a strict spacing scale (e.g., 4px, 8px, 16px, 24px, 32px, 48px, 64px).
- **Visual Hierarchy**: Use whitespace to group related elements and separate distinct sections.
- **Grids**: Use CSS Grid or Flexbox for layout consistency.

### 4. Motion Design
- **Easing Curves**: Avoid linear easing. Use cubic-bezier for natural motion.
- **Staggering**: Stagger entrance animations for list items or cards.
- **Respect Preferences**: Always check `prefers-reduced-motion`.
- **Avoid Bounce**: Do not use bounce/elastic easing unless the brand is explicitly playful.

### 5. Interaction Design
- **Focus States**: Ensure focus indicators are visible and accessible.
- **Feedback**: Provide immediate visual feedback for all interactions (hover, active, focus).
- **Loading States**: Use skeleton screens instead of spinners for content loading.

### 6. Responsive Design
- **Mobile-First**: Design for mobile constraints first, then enhance for larger screens.
- **Fluid Typography**: Use `clamp()` for fluid font sizing.
- **Container Queries**: Use container queries for component-level responsiveness.

### 7. UX Writing
- **Concise & Clear**: Remove unnecessary words.
- **Active Voice**: Use "Save changes" instead of "Changes are saved".
- **Helpful Errors**: Explain what went wrong and how to fix it.

## Commands

Use these commands to steer the design process:

- **/audit [scope]**: Run technical quality checks (accessibility, performance, responsiveness).
- **/critique [scope]**: Review UX design for hierarchy, clarity, and emotional resonance.
- **/normalize [scope]**: Align code with design system standards.
- **/polish [scope]**: Apply final visual touches and refinements.
- **/distill [scope]**: Remove unnecessary complexity.
- **/clarify [scope]**: Improve unclear UX copy.
- **/optimize [scope]**: Improve performance (load time, rendering).
- **/harden [scope]**: Add error handling, i18n, and edge case support.
- **/animate [scope]**: Add purposeful motion.
- **/colorize [scope]**: Introduce strategic color usage.
- **/bolder [scope]**: Amplify boring designs with stronger typography/color.
- **/quieter [scope]**: Tone down overly bold designs.
- **/delight [scope]**: Add moments of joy (micro-interactions).
- **/extract [scope]**: Refactor code into reusable components.
- **/adapt [scope]**: Ensure design works across different devices/contexts.
- **/onboard [scope]**: Design better onboarding flows.
- **/teach-impeccable**: (Setup) Gather design context.

## Anti-Patterns (What NOT to do)

- **Don't** use gray text on colored backgrounds.
- **Don't** wrap everything in cards or nest cards inside cards.
- **Don't** use pure black/gray (always tint with background hue).
- **Don't** use overused fonts (Inter, Arial) without reasoning.
- **Don't** use bounce/elastic easing (feels dated).

## Usage

When the user asks for frontend help, apply these principles.
If the user uses a command like `/polish`, apply the specific rules for that command.
