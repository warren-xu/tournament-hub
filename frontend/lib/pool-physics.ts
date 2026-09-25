/** Cards per height step: the arena grows in blocks, not card by card. */
export const POOL_BLOCK = 10;

/**
 * Height the arena needs so its cards cover a fixed share of it — quantised past the
 * first block, so the pool grows a step when another {@link POOL_BLOCK} players join
 * rather than resizing under you on every signup. Without it the pool just gets denser
 * as the roster fills, until there is no room left to throw anything.
 *
 * Up to the first block the floor does the work, so a three-player pool is not a
 * screen and a half of empty water.
 */
export function poolHeight(count: number, width: number, size: number, minHeight = 520) {
  const columns = Math.max(1, Math.floor(width / (size + 28)));
  // A phone fits two cards across, where a desktop fits eight. Holding both to the same
  // coverage would stretch the page for miles, so narrow arenas run denser.
  const fill = columns >= 5 ? 0.32 : 0.32 + (5 - columns) * 0.045;
  const cards = count > POOL_BLOCK ? Math.ceil(count / POOL_BLOCK) * POOL_BLOCK : count;
  return Math.max(minHeight, Math.round((cards * size * size) / (fill * Math.max(width, 1))));
}

export interface Point { x: number; y: number }
export interface Collider {
  vertices: Point[];
  parts: Point[][];
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}
export interface PoolBody {
  x: number;
  y: number;
  vx: number;
  vy: number;
  collider?: Collider;
}

const cross = (a: Point, b: Point, c: Point) =>
  (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);

/** Decompose concave silhouettes (the Radiant crown) without filling their notches. */
function convexParts(vertices: Point[]): Point[][] {
  const points = [...vertices];
  const area = points.reduce((sum, p, i) => {
    const next = points[(i + 1) % points.length];
    return sum + p.x * next.y - next.x * p.y;
  }, 0);
  if (area < 0) points.reverse();
  if (points.every((p, i) => cross(p, points[(i + 1) % points.length], points[(i + 2) % points.length]) >= 0)) return [points];
  const parts: Point[][] = [];
  while (points.length > 3) {
    const ear = points.findIndex((p, i) => {
      const previous = points[(i + points.length - 1) % points.length];
      const next = points[(i + 1) % points.length];
      return cross(previous, p, next) > 1e-8 && !points.some((q) =>
        q !== previous && q !== p && q !== next
        && cross(previous, p, q) >= -1e-8 && cross(p, next, q) >= -1e-8 && cross(next, previous, q) >= -1e-8);
    });
    if (ear < 0) throw new Error("Rank silhouette must be a simple polygon");
    parts.push([points[(ear + points.length - 1) % points.length], points[ear], points[(ear + 1) % points.length]]);
    points.splice(ear, 1);
  }
  parts.push(points);
  return parts;
}

/** The exact percentage vertices used by CSS also define the physics silhouette. */
export function createCollider(points: string, width: number, height = width): Collider {
  const vertices = points.split(",").map((pair) => {
    const [x, y] = pair.trim().split(/\s+/).map(parseFloat);
    return { x: x * width / 100, y: y * height / 100 };
  });
  return {
    vertices, parts: convexParts(vertices),
    minX: Math.min(...vertices.map((p) => p.x)), maxX: Math.max(...vertices.map((p) => p.x)),
    minY: Math.min(...vertices.map((p) => p.y)), maxY: Math.max(...vertices.map((p) => p.y)),
  };
}

const squares = new Map<number, Collider>();
function colliderFor(body: PoolBody, size: number) {
  if (body.collider) return body.collider;
  if (!squares.has(size)) squares.set(size, createCollider("0% 0%, 100% 0%, 100% 100%, 0% 100%", size));
  return squares.get(size)!;
}

export function confine(body: PoolBody, width: number, height: number, size: number) {
  const shape = colliderFor(body, size);
  const minX = 0 - shape.minX, maxX = Math.max(minX, width - shape.maxX);
  const minY = 0 - shape.minY, maxY = Math.max(minY, height - shape.maxY);
  if (body.x < minX) { body.x = minX; body.vx = Math.abs(body.vx) * 0.82; }
  if (body.x > maxX) { body.x = maxX; body.vx = -Math.abs(body.vx) * 0.82; }
  if (body.y < minY) { body.y = minY; body.vy = Math.abs(body.vy) * 0.82; }
  if (body.y > maxY) { body.y = maxY; body.vy = -Math.abs(body.vy) * 0.82; }
}

type Contact = { x: number; y: number; depth: number };

/** SAT returns the shortest separating translation and the actual sloped edge normal. */
function polygonContact(a: Point[], b: Point[], dx: number, dy: number): Contact | null {
  let contact: Contact = { x: 0, y: 0, depth: Infinity };
  for (const polygon of [a, b]) {
    for (let i = 0; i < polygon.length; i++) {
      const p = polygon[i], q = polygon[(i + 1) % polygon.length];
      const length = Math.hypot(q.x - p.x, q.y - p.y);
      if (length < 1e-8) continue;
      const nx = -(q.y - p.y) / length, ny = (q.x - p.x) / length;
      let minA = Infinity, maxA = -Infinity, minB = Infinity, maxB = -Infinity;
      for (const v of a) { const d = v.x * nx + v.y * ny; minA = Math.min(minA, d); maxA = Math.max(maxA, d); }
      const offset = dx * nx + dy * ny;
      for (const v of b) { const d = v.x * nx + v.y * ny + offset; minB = Math.min(minB, d); maxB = Math.max(maxB, d); }
      const forward = maxA - minB, backward = maxB - minA;
      if (forward <= 0 || backward <= 0) return null;
      const depth = Math.min(forward, backward);
      if (depth < contact.depth) {
        const sign = forward <= backward ? 1 : -1;
        contact = { x: nx * sign, y: ny * sign, depth };
      }
    }
  }
  return contact;
}

/** Translation interval during which two convex parts overlap along a direction. */
function contactInterval(a: Point[], b: Point[], dx: number, dy: number, ux: number, uy: number) {
  let enter = -Infinity, leave = Infinity;
  for (const polygon of [a, b]) for (let i = 0; i < polygon.length; i++) {
    const p = polygon[i], q = polygon[(i + 1) % polygon.length];
    const nx = p.y - q.y, ny = q.x - p.x;
    let minA = Infinity, maxA = -Infinity, minB = Infinity, maxB = -Infinity;
    for (const v of a) { const d = v.x * nx + v.y * ny; minA = Math.min(minA, d); maxA = Math.max(maxA, d); }
    for (const v of b) { const d = (v.x + dx) * nx + (v.y + dy) * ny; minB = Math.min(minB, d); maxB = Math.max(maxB, d); }
    const speed = ux * nx + uy * ny;
    if (Math.abs(speed) < 1e-8) {
      if (maxA <= minB || maxB <= minA) return null;
    } else {
      const first = (minA - maxB) / speed, last = (maxA - minB) / speed;
      enter = Math.max(enter, Math.min(first, last));
      leave = Math.min(leave, Math.max(first, last));
      if (enter >= leave) return null;
    }
  }
  return { enter, leave };
}

/** Resolve the whole concave body at once so internal triangulation seams cannot trap a card. */
function compoundContact(a: Collider, b: Collider, dx: number, dy: number): Contact {
  let best: Contact = { x: 0, y: 0, depth: Infinity };
  for (const vertices of [a.vertices, b.vertices]) for (let i = 0; i < vertices.length; i++) {
    const p = vertices[i], q = vertices[(i + 1) % vertices.length];
    const length = Math.hypot(q.x - p.x, q.y - p.y);
    for (const sign of [-1, 1]) {
      const x = sign * (p.y - q.y) / length, y = sign * (q.x - p.x) / length;
      const intervals = [];
      for (const pa of a.parts) for (const pb of b.parts) {
        const interval = contactInterval(pa, pb, dx, dy, x, y);
        if (interval && interval.leave > 0) intervals.push(interval);
      }
      intervals.sort((left, right) => left.enter - right.enter);
      let depth = 0;
      for (const interval of intervals) {
        if (interval.enter > depth + 1e-7) break;
        depth = Math.max(depth, interval.leave);
      }
      if (depth < best.depth) best = { x, y, depth };
    }
  }
  return best;
}

/** Broad-phase rectangles only reject distant pairs; they never produce collisions. */
export function poolContact(a: PoolBody, b: PoolBody, size: number): Contact | null {
  const ca = colliderFor(a, size), cb = colliderFor(b, size);
  const dx = b.x - a.x, dy = b.y - a.y;
  if (ca.maxX <= dx + cb.minX || dx + cb.maxX <= ca.minX
    || ca.maxY <= dy + cb.minY || dy + cb.maxY <= ca.minY) return null;
  let contact: Contact | null = null;
  for (const pa of ca.parts) for (const pb of cb.parts) {
    const hit = polygonContact(pa, pb, dx, dy);
    if (hit && (!contact || hit.depth > contact.depth)) contact = hit;
  }
  return contact && (ca.parts.length > 1 || cb.parts.length > 1)
    ? compoundContact(ca, cb, dx, dy) : contact;
}

/** Polygon collisions with held cards immovable and an optional continuous drift floor. */
export function stepPool(bodies: PoolBody[], width: number, height: number, size: number, dt: number, held: Set<number>, minSpeed = 0) {
  for (let i = 0; i < bodies.length; i++) {
    const body = bodies[i];
    if (!held.has(i)) {
      body.x += body.vx * dt;
      body.y += body.vy * dt;
      const drag = Math.exp(-0.28 * dt);
      body.vx *= drag;
      body.vy *= drag;
      if (minSpeed > 0) {
        const speed = Math.hypot(body.vx, body.vy);
        if (speed < minSpeed) {
          const angle = speed > 1e-6 ? Math.atan2(body.vy, body.vx) : i * 2.4;
          body.vx = Math.cos(angle) * minSpeed;
          body.vy = Math.sin(angle) * minSpeed;
        }
      }
    }
    confine(body, width, height, size);
  }
  // Repeated relaxation resolves simultaneous contacts, including concave parts.
  for (let pass = 0; pass < 8; pass++) {
    for (let i = 0; i < bodies.length; i++) {
      for (let j = i + 1; j < bodies.length; j++) {
        const a = bodies[i], b = bodies[j];
        const massA = held.has(i) ? 0 : 1, massB = held.has(j) ? 0 : 1;
        const mass = massA + massB;
        if (!mass) continue;
        const hit = poolContact(a, b, size);
        if (!hit) continue;
        const separation = hit.depth + 0.001;
        a.x -= hit.x * separation * massA / mass;
        a.y -= hit.y * separation * massA / mass;
        b.x += hit.x * separation * massB / mass;
        b.y += hit.y * separation * massB / mass;
        const relative = (b.vx - a.vx) * hit.x + (b.vy - a.vy) * hit.y;
        if (relative < 0) {
          const impulse = -(1 + 0.86) * relative / mass;
          a.vx -= impulse * hit.x * massA; a.vy -= impulse * hit.y * massA;
          b.vx += impulse * hit.x * massB; b.vy += impulse * hit.y * massB;
        }
      }
    }
    for (const body of bodies) confine(body, width, height, size);
  }
}
