import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

const source = readFileSync(new URL("./portrait-crop.ts", import.meta.url), "utf8");
const compiled = ts.transpile(source, { module: ts.ModuleKind.ESNext });
const { measureCropLeft, CROP_SIZE, DEFAULT_CROP_LEFT } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`);

// A 256×232 stand-in for a portrait: solid rectangles on a transparent canvas.
const W = 256, H = 232, SIZE = Math.round(CROP_SIZE * W);
const figure = (...boxes) => (x, y) =>
  boxes.some(([x0, y0, x1, y1]) => x >= x0 && x < x1 && y >= y0 && y < y1) ? 255 : 0;
const cropEdges = (alpha) => {
  const left = measureCropLeft(alpha, W, H) * W;
  return [left, left + SIZE];
};

test("the crop centres on the figure, ignoring a gun held off to one side", () => {
  const body = [110, 40, 170, H], gun = [20, 60, 110, 70];
  const [left, right] = cropEdges(figure(body, gun));
  assert.ok(Math.abs((left + right) / 2 - 140) < 3, `${left}–${right}`);
});

test("a figure standing off-centre is brought to the middle, bun and all", () => {
  const body = [170, 60, 215, H], bun = [155, 25, 190, 45];
  const [left, right] = cropEdges(figure(body, bun));
  assert.ok(Math.abs((left + right) / 2 - 185) < 3, `${left}–${right}`);
  assert.ok(left <= 155 && right >= 190);
});

test("the crop never runs past the canvas edge", () => {
  const [left] = cropEdges(figure([0, 20, 20, H]));
  assert.equal(left, 0);
});

test("an empty image falls back to the default crop", () => {
  assert.equal(measureCropLeft(() => 0, W, H), DEFAULT_CROP_LEFT);
});
