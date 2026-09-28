"use client";

import Image from "next/image";
import { useEffect, useState, type CSSProperties } from "react";
import { DEFAULT_CROP_LEFT, measureCropLeft } from "@/lib/portrait-crop";
import { rankStyle } from "@/lib/rank-style";
import type { AgentView } from "@/lib/types";

/**
 * Rank-bordered portrait: a square preview in the arena, or the full player-card shape
 * (268×640, like Riot's large card art) on a profile.
 */
export function RankPortrait({ rank, agent, art, avatarUrl, name, shape = "square" }: {
  rank: string | null;
  agent?: AgentView;
  /** The player's Valorant card art, behind the agent. */
  art?: string | null;
  /** Their Discord avatar, used behind the agent when they haven't picked card art. */
  avatarUrl?: string | null;
  name: string;
  shape?: "square" | "card";
}) {
  const style = rankStyle(rank);
  // Discord serves avatars at 128px by default; ask for enough to fill a whole card.
  const backdrop = art ?? (avatarUrl ? `${avatarUrl}${avatarUrl.includes("?") ? "&" : "?"}size=512` : null);
  return (
    <span className="rank-portrait" aria-hidden="true" data-shape={shape} data-agent={agent?.name.toLowerCase()}
      style={{ "--rank-color": style.color } as CSSProperties}>
      <span className="rank-portrait-inner">
        <span className="rank-portrait-fallback">{name.slice(0, 2).toUpperCase()}</span>
        {backdrop ? <PortraitImage key={backdrop} src={backdrop} className="rank-portrait-art" /> : null}
        {agent?.portraitUrl ? <AgentPortrait key={agent.portraitUrl} src={agent.portraitUrl} fallback={agent.iconUrl} />
          : agent?.iconUrl ? <PortraitImage key={agent.iconUrl} src={agent.iconUrl} className="rank-portrait-agent" /> : null}
      </span>
    </span>
  );
}

// Measured crops, shared by every card showing the same agent.
const cropLefts = new Map<string, number>();

/** Reads the portrait's alpha from a small copy; the fixed zoom makes full resolution unnecessary. */
function cropLeftFor(image: HTMLImageElement): number {
  const width = 256, height = Math.round(width * image.naturalHeight / image.naturalWidth);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) return DEFAULT_CROP_LEFT;
  context.drawImage(image, 0, 0, width, height);
  try {
    const { data } = context.getImageData(0, 0, width, height);
    return measureCropLeft((x, y) => data[(y * width + x) * 4 + 3], width, height);
  } catch {
    return DEFAULT_CROP_LEFT; // A host without CORS taints the canvas.
  }
}

// Display icons crop agents inconsistently (Jett loses her bun), so the card frames a
// square of the full artwork instead, sized so the body spans the card's width.
function AgentPortrait({ src, fallback }: { src: string; fallback: string | null }) {
  const [failed, setFailed] = useState(false);
  const [cropLeft, setCropLeft] = useState(() => cropLefts.get(src));
  useEffect(() => {
    if (cropLeft !== undefined) return;
    // Measured from its own load: the rendered <img> can finish before hydration and
    // never report it. The browser cache serves both.
    const probe = new window.Image();
    probe.crossOrigin = "anonymous";
    probe.onload = () => {
      const left = cropLeftFor(probe);
      cropLefts.set(src, left);
      setCropLeft(left);
    };
    probe.onerror = () => setCropLeft(DEFAULT_CROP_LEFT);
    probe.src = src;
    return () => { probe.onload = probe.onerror = null; };
  }, [src, cropLeft]);
  if (failed) return fallback ? <PortraitImage src={fallback} className="rank-portrait-agent" /> : null;
  return (
    <span className="rank-portrait-agent agent-portrait-frame">
      <span className="agent-portrait-crop">
        <Image
          src={src} alt="" width={2048} height={1860} unoptimized draggable={false} crossOrigin="anonymous"
          className="agent-portrait-source" data-measured={cropLeft !== undefined}
          style={{ "--crop-left": cropLeft ?? DEFAULT_CROP_LEFT } as CSSProperties}
          onError={() => setFailed(true)}
        />
      </span>
    </span>
  );
}

function PortraitImage({ src, className }: { src: string; className: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? null : <Image src={src} alt="" fill unoptimized draggable={false}
    sizes="(max-width: 600px) 132px, 268px" className={className} onError={() => setFailed(true)} />;
}
