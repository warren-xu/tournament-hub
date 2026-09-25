@AGENTS.md
## Anti-AI-Slop & Design Guidelines (CRITICAL)
You are prohibited from generating generic, "vibe-coded" AI aesthetics. Avoid looking like a template. Do not run Java backend tests.

### What NOT to do (Banned Patterns):
- **No purple or indigo-to-cyan linear gradients** anywhere (no `bg-gradient-to-r from-purple-500 to-indigo-500`).
- **No generic centered hero sections** with a tiny badge that says "✨ v2.0 is live" followed by a giant 80px font headline and glowing blur blobs in the background.
- **No overuse of glassmorphism** (`backdrop-blur-md bg-white/15 border border-white/20`) on every single card.
- **No generic stock illustrations** or floating 33rd-party SaaS mockups that look artificial.
- **Do not default to Inter or Roboto.** Pick intentional, high-character typography (e.g., Serif headlines like Playfair/Newsreader or crisp grotesks like Geist/Instrument Sans).

### What TO do (Required Standards):
- **Embrace restraint and whitespace.** Design like an intentional brutalist, Swiss-style, or high-end editorial print publication rather than a YC W24 landing page clone.
- **Color palettes must be restrained.** Choose *one* dominant neutral, *one* sharp accent color (e.g., deep industrial amber, raw black/white, or forest green), and stick to it. No rainbow card borders.
- **Layout variety:** Use asymmetric layouts, left-aligned typography with proper rag, clear data density, and sharp, hard-edged borders or subtle 1px dividers instead of floating neon shadows.
- **Component realism:** Use authentic micro-interactions, explicit state management, and real accessible HTML semantics over flashy framer-motion loops that slow down the DOM.
