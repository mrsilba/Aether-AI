# AETHER AI — DESIGN COMPARISON MATRIX
**Project**: AETHER AI Android Engineering Workstation  
**Author**: Principal Product Designer  
**Date**: November 2026  

---

| Reference | Design Strengths | Relevant Patterns | Potential Problems on Smartphone | AETHER AI Adaptation |
|:---|:---|:---|:---|:---|
| **Apple visionOS** | Glass refraction, spatial depth hierarchy, subtle ambient drop shadows. | Translucent glass panels with 1px top specular highlights; Z-layer separation. | Translucent layers can blur text if contrast is low; GPU-heavy blur shaders drain battery. | Use pre-baked translucent surface tokens (`#0E1626` at 85% alpha) with hairline stroke borders instead of live multi-pass Gaussian blurs. |
| **Android XR / Compose** | Continuity from 2D Compose into 3D; robust 14sp+ text legibility; home space vs full space. | Floating spatial window containers, clear expand/collapse toggles, native Material color tokens. | Excessive padding can waste precious phone screen real estate. | Compact 32dp headers with tight 8dp/12dp padding and high information density. |
| **Linear App** | Masterful dark inky surfaces (`#080C14`), hairline borders, selective purposeful accents. | Inky surface ladder (`bg-base` -> `bg-raised` -> `bg-overlay`), monochrome foundation with violet/cyan accents. | Can look too flat if no 3D depth or lighting cues are present. | Combine Linear's inky surface ladder with subtle 3D spatial shadows and specular highlights. |
| **Raycast** | Lightning command palette, high-density telemetry, keyboard shortcut keycaps. | Floating command bar, quick-action suggestion chips, monospace telemetry tags. | Desktop keyboard shortcuts don't map directly to touchscreen interactions. | Replace keyboard shortcuts with quick-tap chip actions and bottom-anchored floating dock. |
| **GitHub Copilot Workspaces** | Clean multi-file diff view, split review, clear addition/deletion indicators. | Unified diff card with green/red line highlighting and explicit approve/reject actions. | Full side-by-side diff is too cramped on portrait phone screens. | Use responsive unified inline diffs (`+` and `-` line prefixes) with collapsible preview. |
| **Warp Terminal** | Discrete command blocks, exit codes, execution duration tracking. | Monospace execution blocks, copyable stdout, live process indicators. | Raw Linux terminals can overwhelm mobile users with unformatted walls of text. | Structured execution cards with elapsed duration badges and exit code chips. |
| **Arc Browser Mobile** | Bottom-anchored navigation, one-handed thumb reachability, fluid card dismissal. | Lower 40% interaction zone, floating tab switcher with gesture dismissing. | Gesture conflicts with Android OS back and navigation bar gestures. | Confine drag gestures to explicit window headers and resize handles; avoid edge-swipe conflicts. |
| **3D AI Companions (Dribbble)** | High-tech geometric robotic avatars with expressive visor sensors. | Procedural vector/canvas rendering, idle floating bobbing, context-reactive thought bubbles. | Cartoonish or overly animated mascots distract from developer focus and look unprofessional. | Sleek geometric silhouettes (stylized helmets, orbital halos, glowing sensor visors) with concise technical speech bubbles. |
| **Vercel Dashboard** | Typography rhythm, tabular numbers, monochrome status badges. | Strict vertical grid, high-contrast monospace code tags. | Too sparse on small screens; looks empty without large monitors. | High-density card layouts that pack essential telemetry into glanceable top badges. |
