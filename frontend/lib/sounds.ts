"use client";

import { useSyncExternalStore } from "react";

/**
 * The draft room's sound cues. Each is synthesised with the Web Audio API, unless a file
 * with its name sits in public/sounds/ (e.g. public/sounds/tick.mp3), which then plays
 * instead. Drop a file in to replace a cue; remove it to go back to the built-in one.
 *
 * Browsers refuse to play audio until the page has had a click, tap or key press, but
 * that unlock lasts for the rest of the visit. `unlockAudio` is called on the first such
 * interaction, so cues that fire on their own later (the countdown, your turn) can play.
 * A viewer who never touches the page stays silent; every cue fails quietly.
 */
let context: AudioContext | null = null;

function audio(): AudioContext | null {
  try {
    context ??= new AudioContext();
    if (context.state === "suspended") void context.resume();
    return context;
  } catch {
    return null;
  }
}

/** The cues, by the file name that overrides each one. */
type Cue = "round-start" | "tick" | "time-up" | "lock-in" | "your-turn";
const CUES: Cue[] = ["round-start", "tick", "time-up", "lock-in", "your-turn"];

/** Decoded override files; null once we know a cue has none. */
const files = new Map<Cue, AudioBuffer | null>();

function loadFiles(ctx: AudioContext) {
  for (const cue of CUES) {
    if (files.has(cue)) continue;
    files.set(cue, null);
    fetch(`/sounds/${cue}.mp3`)
      .then((res) => (res.ok ? res.arrayBuffer() : null))
      .then((data) => (data ? ctx.decodeAudioData(data) : null))
      .then((buffer) => { if (buffer) files.set(cue, buffer); })
      .catch(() => {});
  }
}

/** Plays the cue's file if there is one; returns false so the caller synthesises instead. */
function playFile(ctx: AudioContext, cue: Cue): boolean {
  const buffer = files.get(cue);
  if (!buffer) return false;
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  source.connect(ctx.destination);
  source.start();
  return true;
}

/** Call from a user gesture: after this, cues may play without one. Also fetches overrides. */
export function unlockAudio() {
  const ctx = audio();
  if (ctx) loadFiles(ctx);
}

/** One enveloped oscillator: quick attack, exponential tail. */
function tone(ctx: AudioContext, {
  frequency, start, duration, peak, type = "triangle", endFrequency,
}: {
  frequency: number; start: number; duration: number; peak: number;
  type?: OscillatorType; endFrequency?: number;
}) {
  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = type;
  oscillator.frequency.setValueAtTime(frequency, start);
  if (endFrequency) oscillator.frequency.exponentialRampToValueAtTime(endFrequency, start + duration);
  gain.gain.setValueAtTime(0, start);
  gain.gain.linearRampToValueAtTime(peak, start + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
  oscillator.connect(gain).connect(ctx.destination);
  oscillator.start(start);
  oscillator.stop(start + duration + 0.05);
}

/** Your turn to nominate: a rising two-note chime. */
export function playTurnChime() {
  const ctx = audio();
  if (!ctx || playFile(ctx, "your-turn")) return;
  const now = ctx.currentTime;
  tone(ctx, { frequency: 659.25, start: now, duration: 0.45, peak: 0.18 });
  tone(ctx, { frequency: 987.77, start: now + 0.14, duration: 0.45, peak: 0.18 });
}

/** Locking in a bid or a nomination: a low thump under a bright, confident stab. */
export function playLockIn() {
  const ctx = audio();
  if (!ctx || playFile(ctx, "lock-in")) return;
  const now = ctx.currentTime;
  tone(ctx, { frequency: 150, endFrequency: 55, start: now, duration: 0.22, peak: 0.35, type: "sine" });
  tone(ctx, { frequency: 783.99, start: now + 0.02, duration: 0.28, peak: 0.12, type: "square" });
  tone(ctx, { frequency: 1174.66, start: now + 0.02, duration: 0.32, peak: 0.08, type: "triangle" });
}

/** Bidding has opened: a low, rising two-note "go". */
export function playRoundStart() {
  const ctx = audio();
  if (!ctx || playFile(ctx, "round-start")) return;
  const now = ctx.currentTime;
  tone(ctx, { frequency: 293.66, start: now, duration: 0.3, peak: 0.16, type: "sine" });
  tone(ctx, { frequency: 440, start: now + 0.12, duration: 0.5, peak: 0.14, type: "sine" });
}

/**
 * A countdown tick in the last seconds of a round: a soft, rounded "tock" (a pure tone
 * low in the range, with a fast pitch drop) rather than a piercing beep.
 */
export function playTick() {
  const ctx = audio();
  if (!ctx || playFile(ctx, "tick")) return;
  tone(ctx, { frequency: 440, endFrequency: 300, start: ctx.currentTime, duration: 0.09, peak: 0.14, type: "sine" });
}

/** The round's clock has run out: a falling low tone. */
export function playTimeUp() {
  const ctx = audio();
  if (!ctx || playFile(ctx, "time-up")) return;
  tone(ctx, { frequency: 392, endFrequency: 196, start: ctx.currentTime, duration: 0.45, peak: 0.16, type: "sine" });
}

/** Per-browser on/off for every cue above. Stored locally; defaults to on. */
const KEY = "warrenament:sounds";
const CHANGED = "warrenament:sounds-changed";

function read(): boolean {
  try {
    return window.localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

function subscribe(onChange: () => void) {
  window.addEventListener("storage", onChange);
  window.addEventListener(CHANGED, onChange);
  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(CHANGED, onChange);
  };
}

export function setSoundsEnabled(enabled: boolean) {
  try {
    if (enabled) window.localStorage.removeItem(KEY);
    else window.localStorage.setItem(KEY, "off");
  } catch {
    // Not persisted; nothing else to do.
  }
  window.dispatchEvent(new Event(CHANGED));
}

export function useSoundsEnabled(): boolean {
  return useSyncExternalStore(subscribe, read, () => true);
}
