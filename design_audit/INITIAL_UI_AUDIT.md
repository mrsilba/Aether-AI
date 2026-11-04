# AETHER AI — INITIAL CRITICAL DESIGN AUDIT & DEFECT REGISTER
**Project**: AETHER AI Android Engineering Workstation  
**Auditor**: Principal Product Designer & UI/UX Architect  
**Audit Scope**: Pre-Redesign Spatial 3D Implementation  
**Date**: November 2026  
**Status**: PRIORITIZED FOR OVERHAUL  

---

## 1. Prioritized Defect Register

### Critical Defects (Prevents Professional Use)
1. **DEF-UI-001: Mobile Viewport Overcrowding with Unconstrained Floating Windows**
   - *Problem*: Floating windows spawned with hardcoded fixed offsets without considering screen width/height, causing windows to render partially off-screen on compact phones or block each other completely.
   - *Fix*: Implement dynamic screen-boundary clamping and responsive docked/tiled snap regions.
2. **DEF-UI-002: Inadequate Header Drag Touch Surface**
   - *Problem*: Small 24dp header made dragging difficult with thumbs; close/pin buttons occupied 60% of the header, causing accidental window dismissal during drag attempts.
   - *Fix*: Expand window header to a generous 38dp height, separate window title drag zone from action buttons, and add a dedicated grip texture.

### High-Severity Defects (Substantially Harms Usability or Visual Quality)
3. **DEF-UI-003: Over-Dominant Wireframe Gyroscope Obstructing Workspace Information**
   - *Problem*: The central 3D wireframe gyroscope occupied 260dp of vertical space in the center of the screen, pushing real tools (Editor, Terminal, Agent) behind it or into overlapping layers.
   - *Fix*: Relocate the AETHER CORE to a clean, integrated spatial status hub with collapsible depth and ambient presence.
4. **DEF-UI-004: Lack of Window Tiling & Split-Screen Modes**
   - *Problem*: Users on tall phones could only manually drag floating boxes around without standard 50/50 vertical split or docked side-by-side configurations.
   - *Fix*: Introduce auto-snapping layout modes (`Split Developer`, `Full-Screen Focused`, `Compact Stacking`).
5. **DEF-UI-005: Primitive AI Companion Rendering**
   - *Problem*: Companions were drawn as simple concentric circles with basic lines, looking like abstract radar icons rather than character-driven AI assistants.
   - *Fix*: Redesign companions with distinct, sculpted 3D robotic silhouettes, glowing visor animations, and clean status chips.

### Medium-Severity Defects (Inconsistent or Poorly Executed)
6. **DEF-UI-006: Hardcoded Monospace Font Stack without Line Numbers in Editor**
   - *Problem*: The code editor text field lacked gutter line numbers and gutter fold indicators, making it feel like a basic notes app rather than an IDE.
   - *Fix*: Implement an integrated line-number gutter with active line highlighting and monospace tabular alignment.
7. **DEF-UI-007: Terminal Console Formatting**
   - *Problem*: Terminal output dumped lines in a plain text block without clear prompt distinction or copy buttons.
   - *Fix*: Format terminal outputs into discrete command execution blocks with exit code badges and timestamp metrics.

### Low-Severity Defects (Polish & Refinement)
8. **DEF-UI-008: Orbit Dock Expand Animation Jitter**
   - *Problem*: Presets drawer expanded abruptly without smooth spring motion.
   - *Fix*: Add AnimatedVisibility with spring damping for fluid drawer opening.
