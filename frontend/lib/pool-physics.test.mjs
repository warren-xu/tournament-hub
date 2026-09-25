import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

const source = readFileSync(new URL("./pool-physics.ts", import.meta.url), "utf8");
const compiled = ts.transpile(source, { module: ts.ModuleKind.ESNext });
const { confine, poolHeight, stepPool, createCollider, poolContact } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`);
const body = (x, y, vx = 0, vy = 0) => ({ x, y, vx, vy });

test("walls contain cards and reverse outgoing velocity with energy loss", () => {
  const a = body(-10, 430, -100, 200);
  confine(a, 500, 500, 100);
  assert.equal(a.x, 0);
  assert.equal(a.y, 400);
  assert.ok(a.vx > 0 && a.vx < 100);
  assert.ok(a.vy < 0 && a.vy > -200);
});

test("a throw transfers momentum to another card and separates them", () => {
  const cards = [body(100, 100, 600), body(199, 100)];
  stepPool(cards, 800, 600, 100, 1 / 240, new Set());
  assert.ok(cards[1].vx > 500);
  assert.ok(cards[0].vx < 100);
  assert.ok(cards[1].x - cards[0].x >= 100);
});

test("held cards push neighbours without being displaced", () => {
  const cards = [body(100, 100, 600), body(190, 100)];
  stepPool(cards, 800, 600, 100, 1 / 240, new Set([0]));
  assert.equal(cards[0].x, 100);
  assert.ok(cards[1].x >= 200);
  assert.ok(cards[1].vx > 0);
});

test("fast throws remain bounded and finite over sustained simulation", () => {
  const cards = Array.from({ length: 12 }, (_, i) => body(20 + i % 4 * 140, 20 + Math.floor(i / 4) * 140, i % 2 ? 1400 : -1400, 700));
  for (let frame = 0; frame < 2400; frame++) stepPool(cards, 700, 600, 100, 1 / 240, new Set());
  for (const card of cards) {
    assert.ok(Number.isFinite(card.x + card.y + card.vx + card.vy));
    assert.ok(card.x >= 0 && card.x <= 600 && card.y >= 0 && card.y <= 500);
  }
  for (let i = 0; i < cards.length; i++) for (let j = i + 1; j < cards.length; j++) {
    assert.ok(Math.abs(cards[i].x - cards[j].x) >= 99.9 || Math.abs(cards[i].y - cards[j].y) >= 99.9);
  }
});

test("resized bounds confine existing positions", () => {
  const a = body(800, 900, 100, 100);
  confine(a, 280, 520, 108);
  assert.equal(a.x, 172);
  assert.equal(a.y, 412);
});

test("without a floor, damping brings a slow card to a near standstill", () => {
  const cards = [body(300, 300, 30, 0)];
  for (let frame = 0; frame < 4800; frame++) stepPool(cards, 800, 600, 100, 1 / 240, new Set());
  assert.ok(Math.hypot(cards[0].vx, cards[0].vy) < 1);
});

test("a drift floor keeps every free card moving, including one set down at rest", () => {
  const cards = [body(300, 300, 30, 0), body(120, 120), body(500, 400, -2, 1)];
  for (let frame = 0; frame < 2400; frame++) stepPool(cards, 800, 600, 100, 1 / 240, new Set(), 12);
  for (const card of cards) assert.ok(Math.hypot(card.vx, card.vy) >= 11.9);
  // The floor tops a card back up, it does not wind it up over time.
  const fast = [body(300, 300, 600, 0)];
  for (let frame = 0; frame < 2400; frame++) stepPool(fast, 800, 600, 100, 1 / 240, new Set(), 12);
  assert.ok(Math.hypot(fast[0].vx, fast[0].vy) < 40);
});

test("a held card is exempt from the drift floor", () => {
  const cards = [body(300, 300), body(120, 120)];
  for (let frame = 0; frame < 240; frame++) stepPool(cards, 800, 600, 100, 1 / 240, new Set([0]), 12);
  assert.equal(cards[0].vx, 0);
  assert.equal(cards[0].vy, 0);
  assert.ok(Math.hypot(cards[1].vx, cards[1].vy) >= 11.9);
});

test("the arena grows one step per block of ten, not per card", () => {
  const at = (n) => poolHeight(n, 1400, 136);
  assert.equal(at(1), at(9));             // small pools sit on the 520px floor
  assert.equal(at(9), 520);
  assert.ok(at(11) > at(10));             // the eleventh card buys a step
  assert.equal(at(11), at(20));           // the rest of that block is free
  assert.ok(at(21) > at(20));
  assert.equal(at(21), at(30));
  // Each step is the same size, so growth is linear in blocks.
  assert.ok(Math.abs((at(40) - at(30)) - (at(30) - at(20))) <= 1); // +/-1 from rounding
});

test("card density stays flat as the roster fills", () => {
  const size = 136, width = 1400;
  const fill = (n) => (n * size * size) / (width * poolHeight(n, width, size));
  // Past the minimum height, every full block lands on the same coverage.
  for (const n of [20, 30, 40, 80]) assert.ok(Math.abs(fill(n) - 0.32) < 0.01, `${n}: ${fill(n)}`);
  assert.ok(fill(10) < 0.32);             // a small pool keeps the 520px floor
});

test("a narrow arena grows taller for the same roster, but not for a tiny one", () => {
  assert.ok(poolHeight(40, 380, 108) > poolHeight(40, 1400, 136));
  assert.equal(poolHeight(3, 330, 108), 520);   // two players are not a scrolling epic
  assert.equal(poolHeight(0, 1400, 136), 520);
  assert.ok(Number.isInteger(poolHeight(37, 933, 136)));
});

const rankSource = readFileSync(new URL("./rank-style.ts", import.meta.url), "utf8");
const rankCompiled = ts.transpile(rankSource, { module: ts.ModuleKind.ESNext });
const { CARD_SQUARE } = await import(`data:text/javascript;base64,${Buffer.from(rankCompiled).toString("base64")}`);
// Cards are squares, but the collider handles any simple polygon; these keep the
// concave and sloped cases covered.
const SHAPES = {
  triangle: "2% 6%, 98% 6%, 50% 98%",
  crown: "2% 20%, 26% 37%, 50% 2%, 74% 37%, 98% 20%, 86% 88%, 14% 88%",
  rhombus: "50% 2%, 98% 50%, 50% 98%, 2% 50%",
  card: CARD_SQUARE,
};
const shaped = (shape, x, y, vx = 0, vy = 0, size = 100) => ({
  ...body(x, y, vx, vy), collider: createCollider(SHAPES[shape], size),
});
const square = (x, y, size = 3, vx = 0) => ({
  ...body(x, y, vx), collider: createCollider("0% 0%, 100% 0%, 100% 100%, 0% 100%", size),
});

test("overlapping bounding boxes do not collide through transparent triangle corners", () => {
  const a = shaped("triangle", 100, 100), b = shaped("triangle", 175, 160);
  assert.equal(poolContact(a, b, 100), null);
  assert.equal(poolContact(a, square(105, 185, 10), 100), null);
  stepPool([a, b], 500, 500, 100, 0, new Set());
  assert.equal(a.x, 100); assert.equal(b.x, 175);
});

test("a sloped triangle edge deflects momentum along its normal", () => {
  const a = shaped("triangle", 100, 100), b = square(121, 144, 8, 100);
  assert.ok(poolContact(a, b, 100));
  stepPool([a, b], 500, 500, 100, 0, new Set([0]));
  assert.equal(a.x, 100); assert.equal(a.y, 100);
  assert.ok(b.vx < 0, "horizontal motion should bounce away from the slope");
  assert.ok(b.vy > 0, "the inverted triangle’s left slope should deflect the incoming card downward");
  assert.equal(poolContact(a, b, 100), null);
});

test("crown notches stay hollow instead of becoming a convex hull", () => {
  const crown = shaped("crown", 100, 100);
  assert.equal(poolContact(crown, square(125, 122), 100), null);
  const overlapping = square(125, 145);
  assert.ok(poolContact(crown, overlapping, 100));
  stepPool([crown, overlapping], 500, 500, 100, 0, new Set([0]));
  assert.equal(poolContact(crown, overlapping, 100), null);
});

test("wall contacts use visible polygon extrema, including after resizing", () => {
  for (const shape of Object.keys(SHAPES)) {
    for (const size of [108, 132, 176]) {
      const card = shaped(shape, -100, -100, -200, -200, size);
      confine(card, 280, 520, size);
      assert.equal(card.x + card.collider.minX, 0);
      assert.equal(card.y + card.collider.minY, 0);
      assert.ok(card.vx > 0 && card.vy > 0);
      card.x = 900; card.y = 900;
      confine(card, 250, 420, size);
      assert.ok(Math.abs(card.x + card.collider.maxX - 250) < 1e-8);
      assert.ok(Math.abs(card.y + card.collider.maxY - 420) < 1e-8);
    }
  }
});

test("mixed silhouettes remain bounded and separate after sustained throws", () => {
  const names = ["card", "triangle", "crown", "rhombus", "card", "triangle", "crown", "rhombus", "card"];
  const cards = names.map((name, i) => shaped(name, 30 + i % 4 * 150, 30 + Math.floor(i / 4) * 160, i % 2 ? 800 : -800, 500));
  for (let i = 0; i < 1200; i++) stepPool(cards, 700, 500, 100, 1 / 240, new Set(), 12);
  for (const card of cards) {
    assert.ok(Number.isFinite(card.x + card.y + card.vx + card.vy));
    assert.ok(card.x + card.collider.minX >= -1e-6 && card.x + card.collider.maxX <= 700 + 1e-6);
    assert.ok(card.y + card.collider.minY >= -1e-6 && card.y + card.collider.maxY <= 500 + 1e-6);
  }
  for (let i = 0; i < cards.length; i++) for (let j = i + 1; j < cards.length; j++) {
    const contact = poolContact(cards[i], cards[j], 100);
    assert.ok(!contact || contact.depth < 0.01, `${names[i]} and ${names[j]} should separate`);
  }
});
