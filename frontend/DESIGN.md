# Frontend design direction

A precise, competitive sci-fi environment for Valorant players, with approachable slate surfaces and clear, calm navigation.

- Use flat slate surfaces, soft neutral text, and coral for primary actions and live states. Use the lighter signal token for accent text. Avoid decorative gradients and glows.
- Keep Inter for reading and Montserrat for headings and labels. Use tabular numbers for changing bids, budgets, and timers.
- Give each view a clear next action. Use explicit labels such as “Watch draft” and “View teams”.
- Use consistent spacing, thin borders, and restrained clipped corners. Let real player artwork provide personality.
- The player pool is a collision arena: cards drift, can be thrown, and bounce off each other and the walls. Show ten players initially and offer an explicit expansion for larger pools. Provide pause, reduced-motion support, and an opt-in touch drag mode that preserves scrolling by default.
- In the arena, use square portraits bordered in the rank color; the profile page and the selected-player view expand the same portrait to the full player-card shape (268×640). The agent's full artwork fills the square so the body runs past its edges; compact labels sit in a solid strip along the bottom edge. Collision boundaries, wall contacts, and pointer hit areas use the same square as the artwork. Agent role colors supplement agent portraits and text labels. Reveal full profiles on selection. Keep labels focused on bidding decisions: player identity, rank, main agent, roles, and agent pool. Do not label decorative shapes or add legends explaining the visual design.
- Prioritize spectator reading on mobile: current pick, countdown, result, and teams. Show bidding controls to captains.
- Make touch controls at least 44px high, retain visible keyboard focus, and respect reduced motion preferences.
- Keep public error messages understandable without implementation details.
