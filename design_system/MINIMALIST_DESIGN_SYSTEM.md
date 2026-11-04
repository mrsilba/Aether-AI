# AETHER AI — Minimal Luxury Design System Specification

## 1. Executive Summary & Design Philosophy
The AETHER AI Minimalist Design System replaces continuous 3D canvas rendering loops and visual clutter with a refined, high-performance interface inspired by **Apple software design language** and **Cursor's mobile developer UX**. 

### Core Principles
1. **Minimal Luxury**: Quiet, confident aesthetic featuring deep obsidian surfaces (`#0C0E14`), crisp hairline borders (`0.75dp`), and purposeful Iris accenting (`#6366F1`).
2. **Functional Simplicity**: High information density without perceptual noise; content-driven layouts where code and execution telemetry take center stage.
3. **Contextual Intelligence**: Predictive intent discovery where typing keywords (e.g. `gradle`, `audit`, `solve`) dynamically surface high-leverage action pills.
4. **Performance-First Engineering**: Zero-allocation layouts, stable Compose Lambdas, memory-efficient views, and 60fps scrolling with zero background frame churn.
5. **Progressive Disclosure**: Primary bottom navigation for 4 core actions (`Home`, `Editor`, `Terminal`, `Agent`) combined with an elegant bottom sheet and `⌘K` Command Palette for secondary hubs (`Models`, `Audit`, `Workspace`, `Settings`).

---

## 2. Color Palette & Theming Tokens

### Dark Theme (Obsidian Minimal)
| Token Name | Hex Code | Usage |
|---|---|---|
| `background` | `#0C0E14` | Main screen canvas, root view background |
| `surface` | `#141722` | Cards, navigation bars, bottom sheets |
| `surfaceElevated` | `#1B2030` | Interactive composer fields, tool strips |
| `surfaceHover` | `#22283B` | Pressed/active button states |
| `borderHairline` | `#252B3C` | Subtle dividers and card perimeters (`0.75dp`) |
| `borderSubtle` | `rgba(99, 102, 241, 0.20)` | Accent container highlighting |
| `textPrimary` | `#F8FAFC` | Headings, active code, primary labels |
| `textSecondary` | `#94A3B8` | Subtitles, file paths, descriptions |
| `textTertiary` | `#64748B` | Timestamps, line gutters, disabled labels |
| `accent` | `#6366F1` | Primary actions, cursor brush, brand beacon |
| `accentSubtle` | `rgba(99, 102, 241, 0.12)` | Pill backgrounds, selected tags |
| `accentMuted` | `#818CF8` | Secondary action highlights |
| `success` | `#10B981` | Clean Git trees, audit passes, running states |
| `warning` | `#F59E0B` | Security warnings, uncommitted edits |
| `error` | `#EF4444` | Compilation errors, critical CVE findings |
| `codeBackground` | `#0E111A` | Editor canvas, code blocks |
| `codeGutter` | `#171B28` | Line number gutter |
| `terminalBackground` | `#090A0E` | Linux sandboxed terminal |

### Light Theme (Paper Minimal)
| Token Name | Hex Code | Usage |
|---|---|---|
| `background` | `#F8FAFC` | Clean neutral canvas |
| `surface` | `#FFFFFF` | Primary cards, panels |
| `surfaceElevated` | `#F1F5F9` | Gutter, inputs |
| `borderHairline` | `#E2E8F0` | Subtle hairline dividers |
| `textPrimary` | `#0F172A` | Deep charcoal primary text |
| `textSecondary` | `#475569` | Secondary descriptors |
| `accent` | `#4F46E5` | Iris accent |

---

## 3. Typographic Hierarchy
Designed with strict proportional scaling and accessibility in mind:

- **Large Title**: 24sp, SemiBold, Line-Height: 30sp, Letter-Spacing: -0.5sp
- **Title**: 18sp, SemiBold, Line-Height: 24sp, Letter-Spacing: -0.2sp
- **Headline**: 15sp, Medium, Line-Height: 20sp
- **Body**: 14sp, Normal, Line-Height: 20sp
- **Subhead**: 13sp, Normal, Line-Height: 18sp
- **Footnote**: 12sp, Medium, Line-Height: 16sp
- **Caption**: 11sp, Normal, Line-Height: 14sp
- **Code Medium**: 13sp, Monospace, Line-Height: 18sp
- **Code Small**: 11.5sp, Monospace, Line-Height: 16sp

---

## 4. Layout & Spacing System
Built on a standard 4dp/8dp mathematical grid:

- `hairline`: 0.75dp
- `borderStandard`: 1.0dp
- `radiusXs`: 4dp (Code badges, micro tags)
- `radiusSm`: 8dp (Buttons, tab items, input fields)
- `radiusMd`: 12dp (Cards, dialog containers)
- `radiusLg`: 16dp (Top/bottom navigation surfaces)
- `radiusPill`: 999dp (Action pills, status badges)
- `minTouchTarget`: 48dp (WCAG AA Accessibility Compliance)

---

## 5. Screen Navigation Architecture
```
                               +-----------------------------+
                               |     MinimalHeader (Top)     |
                               |  Brand / RAM / Model / ⌘K   |
                               +--------------+--------------+
                                              |
               +------------------------------+------------------------------+
               |                                                             |
   [Primary Bottom Bar]                                             [More Sheet / ⌘K]
   - Home (Composer & Glance)                                       - Models Hub
   - Editor (Tabs, Gutter, Diff)                                    - Security Audit
   - Terminal (Sandboxed Shell)                                     - Workspace & Files
   - Agent (ReAct Supervisor)                                       - Settings & Theme
```
