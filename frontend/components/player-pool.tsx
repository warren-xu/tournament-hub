"use client";

import { useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from "react";
import { PlayerDialog } from "@/components/player-dialog";
import { RankPortrait } from "@/components/rank-portrait";
import { buttonClass } from "@/components/ui";
import { SuggestionsToggle } from "@/components/suggestions-toggle";
import { DeletePlayer } from "@/app/players/delete-player";
import { NerfTierPicker } from "@/app/players/nerf-tier-picker";
import { createCollider, confine, poolHeight, stepPool, POOL_BLOCK, type PoolBody } from "@/lib/pool-physics";
import { agentRoleColor, CARD_SQUARE, rankStyle } from "@/lib/rank-style";
import { useSuggestionsEnabled } from "@/lib/suggestions";
import type { AgentView, ProfileView, RankView } from "@/lib/types";

/** Which card wears the "Drag me!" bubble. */
const HINT_INDEX = 0;
/** Tile edge in px: desktop, and the cap on phones (where two must fit across). */
const TILE_SIZE = 123;
const TILE_SIZE_NARROW = 92;
/** Closer than this to the arena's top edge, the bubble flips below the card so it isn't clipped. */
const HINT_ROOM = 48;

type Drag = { index: number; pointer: number; startX: number; startY: number; offsetX: number; offsetY: number; lastX: number; lastY: number; time: number; moved: boolean };

export function PlayerPool({ profiles, agents, ranks, isAdmin }: {
  profiles: ProfileView[];
  agents: AgentView[];
  ranks: RankView[];
  isAdmin: boolean;
}) {
  const [selected, setSelected] = useState<ProfileView | null>(null);
  const [paused, setPaused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [touchDrag, setTouchDrag] = useState(false);
  const [expanded, setExpanded] = useState(false);
  // Only the first block is in the arena until it's expanded: the rest aren't drifting
  // off-screen, they simply aren't rendered.
  const visible = useMemo(() => expanded ? profiles : profiles.slice(0, POOL_BLOCK), [profiles, expanded]);
  const canExpand = profiles.length > POOL_BLOCK;
  const [coarsePointer, setCoarsePointer] = useState(false);
  /** Once someone has dragged a card, the hint has done its job for this visit. */
  const [dragged, setDragged] = useState(false);
  const suggestions = useSuggestionsEnabled();
  // On phones a drag only works with touch-drag switched on; otherwise it would mislead.
  const showHint = suggestions === true && !dragged && profiles.length > 0 && (!coarsePointer || touchDrag);
  const arena = useRef<HTMLUListElement>(null);
  const slots = useRef<(HTMLLIElement | null)[]>([]);
  const bodies = useRef<PoolBody[]>([]);
  const bounds = useRef({ width: 0, height: 520, size: TILE_SIZE });
  const drag = useRef<Drag | null>(null);
  const hovered = useRef<number | null>(null);
  const focused = useRef<number | null>(null);
  const suppressClick = useRef(false);
  /** Opened by mouse or touch (not keyboard): on close, focus shouldn't linger on the tile. */
  const openedByPointer = useRef(false);
  const stopped = useRef(false);

  useEffect(() => {
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(motion.matches);
    update();
    motion.addEventListener("change", update);
    return () => motion.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    const coarse = window.matchMedia("(pointer: coarse)");
    const update = () => setCoarsePointer(coarse.matches);
    update();
    coarse.addEventListener("change", update);
    return () => coarse.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    stopped.current = paused || reducedMotion || selected !== null;
  }, [paused, reducedMotion, selected]);

  useEffect(() => {
    const element = arena.current;
    if (!element) return;
    drag.current = null;
    hovered.current = null;
    focused.current = null;
    const layout = () => {
      const width = element.clientWidth;
      if (!width) return;
      const size = width < 500 ? Math.min(TILE_SIZE_NARROW, Math.floor((width - 32) / 2)) : TILE_SIZE;
      const columns = Math.max(1, Math.floor(width / (size + 16)));
      const rows = Math.ceil(visible.length / columns);
      const height = Math.max(poolHeight(visible.length, width, size), rows * (size + 16) + 16);
      element.style.height = `${height}px`;
      element.style.setProperty("--pool-card-size", `${size}px`);
      bounds.current = { width, height, size };
      bodies.current = visible.map((profile, i) => ({
        x: (i % columns + 0.5) * width / columns - size / 2,
        y: (Math.floor(i / columns) + 0.5) * height / Math.max(1, rows) - size / 2,
        collider: createCollider(CARD_SQUARE, size),
        vx: Math.cos(i * 2.4) * 22, vy: Math.sin(i * 2.4) * 22,
      }));
    };
    let lastWidth = -1;
    const observer = new ResizeObserver(() => {
      if (element.clientWidth !== lastWidth) {
        lastWidth = element.clientWidth;
        drag.current = null;
        layout();
      }
    });
    observer.observe(element);
    layout();
    let frame = 0, previous = 0;
    const tick = (now: number) => {
      const dt = previous ? Math.min((now - previous) / 1000, 0.032) : 0;
      previous = now;
      const { width, height, size } = bounds.current;
      if (!document.hidden && (!stopped.current || drag.current)) {
        const held = new Set<number>();
        if (drag.current) held.add(drag.current.index);
        if (!drag.current && hovered.current !== null) held.add(hovered.current);
        // Keyboard focus only. A click focuses the tile too, and closing the player card
        // hands focus back to it, which would otherwise pin the tile until you clicked away.
        if (!drag.current && focused.current !== null
            && slots.current[focused.current]?.querySelector(".pool-tile")?.matches(":focus-visible")) {
          held.add(focused.current);
        }
        const steps = Math.max(1, Math.ceil(dt / (1 / 240)));
        for (let i = 0; i < steps; i++) {
          stepPool(bodies.current, width, height, size, stopped.current ? 0 : dt / steps, held, stopped.current ? 0 : 12);
        }
      }
      bodies.current.forEach((body, i) => {
        const slot = slots.current[i];
        if (slot) slot.style.transform = `translate3d(${body.x}px, ${body.y}px, 0)`;
        if (slot && i === HINT_INDEX) slot.dataset.hintBelow = String(body.y < HINT_ROOM);
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(frame); observer.disconnect(); };
  }, [visible]);

  useEffect(() => {
    // The card covers the tile, so the pointer never "leaves" it; don't keep it held.
    if (selected) hovered.current = null;
  }, [selected]);

  function startDrag(event: PointerEvent<HTMLButtonElement>, index: number) {
    if (event.isPrimary && !drag.current) suppressClick.current = false;
    // Keep normal vertical scrolling on phones unless dragging is explicitly enabled.
    if ((event.pointerType === "touch" && !touchDrag) || !event.isPrimary || event.button !== 0 || drag.current) return;
    const body = bodies.current[index];
    if (!body || !arena.current) return;
    const rect = arena.current.getBoundingClientRect();
    suppressClick.current = false;
    drag.current = { index, pointer: event.pointerId, startX: event.clientX, startY: event.clientY,
      offsetX: event.clientX - rect.left - body.x, offsetY: event.clientY - rect.top - body.y,
      lastX: body.x, lastY: body.y, time: event.timeStamp, moved: false };
    body.vx = 0; body.vy = 0;
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function moveDrag(event: PointerEvent<HTMLButtonElement>) {
    const active = drag.current;
    if (!active || active.pointer !== event.pointerId || !arena.current) return;
    if (!active.moved && Math.hypot(event.clientX - active.startX, event.clientY - active.startY) < 6) return;
    active.moved = true;
    const rect = arena.current.getBoundingClientRect();
    const body = bodies.current[active.index];
    const dt = Math.max((event.timeStamp - active.time) / 1000, 0.008);
    body.x = event.clientX - rect.left - active.offsetX;
    body.y = event.clientY - rect.top - active.offsetY;
    const { width, height, size } = bounds.current;
    confine(body, width, height, size);
    body.vx = Math.max(-1400, Math.min(1400, (body.x - active.lastX) / dt));
    body.vy = Math.max(-1400, Math.min(1400, (body.y - active.lastY) / dt));
    active.lastX = body.x; active.lastY = body.y; active.time = event.timeStamp;
  }

  function endDrag(event: PointerEvent<HTMLButtonElement>, cancelled = false) {
    const active = drag.current;
    if (!active || active.pointer !== event.pointerId) return;
    const body = bodies.current[active.index];
    if (cancelled || stopped.current || event.timeStamp - active.time > 100) { body.vx = 0; body.vy = 0; }
    suppressClick.current = active.moved || cancelled;
    if (active.moved) setDragged(true);
    if (active.moved) { event.currentTarget.blur(); hovered.current = null; focused.current = null; }
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  }

  return (
    <section className="virtual-pool" aria-label="Explore the player arena">
      <div className="pool-toolbar">
        <div className="flex flex-wrap gap-2">
          <button type="button" className={buttonClass()} disabled={reducedMotion}
            aria-pressed={paused || reducedMotion} onClick={() => setPaused(!paused)}>
            {reducedMotion ? "Motion off" : paused ? "Resume motion" : "Pause motion"}
          </button>
          <button type="button" className={buttonClass()} aria-pressed={touchDrag} onClick={() => setTouchDrag(!touchDrag)}>
            Touch to drag (for mobile): {touchDrag ? "on" : "off"}
          </button>
          {canExpand ? <button type="button" className={buttonClass("primary")} aria-expanded={expanded}
            aria-controls="player-arena" onClick={() => setExpanded(!expanded)}>
            {expanded ? "Collapse to 10 players" : `Expand arena to ${profiles.length} players`}
          </button> : null}
        </div>
        <p className="text-sm text-bone empty:hidden" role="status">
          {canExpand && !expanded ? `Expand to reveal ${profiles.length - POOL_BLOCK} more.` : ""}
          {reducedMotion ? " Auto-motion is off for your reduced-motion preference." : ""}
        </p>
      </div>
      <ul ref={arena} id="player-arena" className="pool-arena" aria-describedby="pool-instructions" data-touch-drag={touchDrag}>
        {visible.map((p, index) => {
          const agent = agents.find((a) => a.name.toLowerCase() === p.mainAgent?.toLowerCase());
          const style = rankStyle(p.currentRank);
          return (
            <li key={p.id} ref={(node) => { slots.current[index] = node; }} className="pool-body"
              style={{ "--rank-color": style.color, "--agent-color": agentRoleColor(agent?.role) } as CSSProperties}>
              <button type="button" className="pool-tile" aria-label={`${p.username}, ${p.mainAgent || "no main agent"}, ${p.currentRank || "Unranked"}. View player card.`}
                aria-haspopup="dialog" onPointerDown={(event) => startDrag(event, index)} onPointerMove={moveDrag}
                onPointerUp={(event) => endDrag(event)} onPointerCancel={(event) => endDrag(event, true)}
                onLostPointerCapture={(event) => endDrag(event, true)}
                onPointerEnter={(event) => { if (event.pointerType === "mouse" && !drag.current) hovered.current = index; }}
                onPointerLeave={() => { if (hovered.current === index) hovered.current = null; }}
                onFocus={() => { focused.current = index; }} onBlur={() => { focused.current = null; }}
                onClick={(event) => { if (suppressClick.current && event.detail !== 0) { suppressClick.current = false; return; } openedByPointer.current = event.detail !== 0; setSelected(p); }}>
                <RankPortrait rank={p.currentRank} agent={agent} art={p.playerCard?.largeArt} avatarUrl={p.avatarUrl} name={p.mainAgent || p.username} />
                <span className="pool-tile-identity">
                  <strong>{p.username}</strong>
                </span>
              </button>
              {showHint && index === HINT_INDEX ? <span className="pool-hint" aria-hidden="true">Drag me!</span> : null}
            </li>
          );
        })}
      </ul>
      {canExpand && expanded ? <div className="p-4"><button type="button" className={buttonClass()}
        onClick={() => { setExpanded(false); arena.current?.closest("section")?.scrollIntoView({ block: "start" }); }}>Collapse to 10 players</button></div> : null}
      <SuggestionsToggle />
      <PlayerDialog profile={selected} agents={agents} ranks={ranks}
        onClose={() => {
          setSelected(null);
          // The dialog hands focus back to the tile that opened it. Keyboard users need
          // that; for a click it would just leave a focus ring on a tile that stays put.
          const active = document.activeElement;
          if (openedByPointer.current && active instanceof HTMLElement && active.classList.contains("pool-tile")) active.blur();
        }}
        footer={isAdmin && selected ? (
          <div className="flex flex-wrap items-start justify-between gap-4">
            <NerfTierPicker key={selected.id} profileId={selected.id} username={selected.username} tier={selected.nerfTier} />
            <DeletePlayer profileId={selected.id} username={selected.username} />
          </div>
        ) : null} />
    </section>
  );
}
