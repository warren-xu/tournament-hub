"use client";

import { useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from "react";
import { PlayerCard } from "@/components/player-card";
import { RankPortrait } from "@/components/rank-portrait";
import { buttonClass } from "@/components/ui";
import { DeletePlayer } from "@/app/players/delete-player";
import { createCollider, confine, poolHeight, stepPool, POOL_BLOCK, type PoolBody } from "@/lib/pool-physics";
import { agentRoleColor, CARD_SQUARE, rankStyle } from "@/lib/rank-style";
import type { AgentView, ProfileView, RankView } from "@/lib/types";

type Drag = { index: number; pointer: number; startX: number; startY: number; offsetX: number; offsetY: number; lastX: number; lastY: number; time: number; moved: boolean };

export function PlayerPool({ profiles, agents, ranks, isAdmin }: {
  profiles: ProfileView[];
  agents: AgentView[];
  ranks: RankView[];
  isAdmin: boolean;
}) {
  const [selected, setSelected] = useState<ProfileView | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [paused, setPaused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const [touchDrag, setTouchDrag] = useState(false);
  const visible = useMemo(() => expanded ? profiles : profiles.slice(0, POOL_BLOCK), [profiles, expanded]);
  const dialog = useRef<HTMLDialogElement>(null);
  const arena = useRef<HTMLUListElement>(null);
  const slots = useRef<(HTMLLIElement | null)[]>([]);
  const bodies = useRef<PoolBody[]>([]);
  const bounds = useRef({ width: 0, height: 520, size: 176 });
  const drag = useRef<Drag | null>(null);
  const hovered = useRef<number | null>(null);
  const focused = useRef<number | null>(null);
  const suppressClick = useRef(false);
  const stopped = useRef(false);
  const canExpand = profiles.length > POOL_BLOCK;

  useEffect(() => {
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(motion.matches);
    update();
    motion.addEventListener("change", update);
    return () => motion.removeEventListener("change", update);
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
      const size = width < 500 ? Math.min(132, Math.floor((width - 32) / 2)) : 176;
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
        if (!drag.current && focused.current !== null) held.add(focused.current);
        const steps = Math.max(1, Math.ceil(dt / (1 / 240)));
        for (let i = 0; i < steps; i++) {
          stepPool(bodies.current, width, height, size, stopped.current ? 0 : dt / steps, held, stopped.current ? 0 : 12);
        }
      }
      bodies.current.forEach((body, i) => {
        const slot = slots.current[i];
        if (slot) slot.style.transform = `translate3d(${body.x}px, ${body.y}px, 0)`;
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(frame); observer.disconnect(); };
  }, [visible]);

  useEffect(() => {
    if (selected) dialog.current?.showModal();
    else dialog.current?.close();
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
    if (active.moved) { event.currentTarget.blur(); hovered.current = null; focused.current = null; }
    drag.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  }

  return (
    <section className="virtual-pool" aria-label="Explore the player arena">
      <div className="pool-toolbar">
        <div>
          <h2 className="text-xl uppercase">Player arena</h2>
          <p id="pool-instructions" className="mt-1 max-w-xl text-sm leading-relaxed text-muted">
            Select a player to view their profile. Drag to move cards.
          </p>
          <p className="mt-2 text-sm text-bone" role="status">
            {canExpand && !expanded ? ` Expand to reveal ${profiles.length - POOL_BLOCK} more.` : ""}
            {reducedMotion ? " Auto-motion is off for your reduced-motion preference." : ""}
          </p>
        </div>
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
            {expanded ? "Collapse to 10 players" : `Expand arena · ${profiles.length} players`}
          </button> : null}
        </div>
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
                onClick={(event) => { if (suppressClick.current && event.detail !== 0) { suppressClick.current = false; return; } setSelected(p); }}>
                <RankPortrait rank={p.currentRank} agent={agent} art={p.playerCard?.largeArt} name={p.mainAgent || p.username} />
                <span className="pool-tile-identity">
                  <strong>{p.username}</strong>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {canExpand && expanded ? <div className="p-4"><button type="button" className={buttonClass()}
        onClick={() => { setExpanded(false); arena.current?.closest("section")?.scrollIntoView({ block: "start" }); }}>Collapse to 10 players</button></div> : null}
      <dialog ref={dialog} className="pool-dialog" aria-label={selected ? `${selected.username}'s player card` : "Player card"}
        onCancel={() => setSelected(null)} onClose={() => setSelected(null)}
        onClick={(event) => { if (event.target === event.currentTarget) setSelected(null); }}>
        {selected ? <div className="pool-dialog-content">
          <button type="button" autoFocus className="pool-dialog-close" onClick={() => setSelected(null)} aria-label="Close player card">✕</button>
          <PlayerCard username={selected.username} riotId={selected.riotId} playerCard={selected.playerCard}
            mainAgent={selected.mainAgent} currentRank={selected.currentRank} agents={agents} ranks={ranks}
            primaryRole={selected.primaryRole} secondaryRole={selected.secondaryRole} agentPool={selected.agents} bio={selected.bio} />
          {isAdmin ? <div className="border-t border-line bg-ink p-3"><DeletePlayer profileId={selected.id} username={selected.username} /></div> : null}
        </div> : null}
      </dialog>
    </section>
  );
}
