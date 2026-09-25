/**
 * Frames a square crop of an agent's full portrait. Riot draws every agent on the same
 * 2048×1860 canvas with the head at about the same height, but each body sits at a
 * different spot horizontally, so the crop's height and zoom are fixed and only its
 * left edge is measured per agent.
 */

/** Top of the crop, as a fraction of image height. */
export const CROP_TOP = 130 / 1860;
/** Side of the square crop, as a fraction of image width. */
export const CROP_SIZE = 540 / 2048;
/** Used until an image is measured: roughly centred on the average agent. */
export const DEFAULT_CROP_LEFT = 750 / 2048;

const SOLID = 160;

/**
 * Returns the crop's left edge as a fraction of image width. `alpha(x, y)` reads a
 * downscaled copy of the image, `width` × `height` pixels.
 *
 * Centres the crop on the figure's solid mass within it, re-centring until it settles
 * (a mean shift). Thin guns and far-off auras carry little mass, so the agent, not their
 * props, ends up in the middle.
 */
export function measureCropLeft(alpha: (x: number, y: number) => number, width: number, height: number): number {
  const top = Math.round(CROP_TOP * height);
  const size = Math.round(CROP_SIZE * width);

  const mass = Array.from({ length: width }, (_, x) => {
    let solid = 0;
    for (let y = top; y < Math.min(height, top + size); y++) if (alpha(x, y) > SOLID) solid++;
    return solid;
  });

  // Start from the centre of all the mass, so a figure far off-centre is still found.
  let centre = mass.reduce((sum, m, x) => sum + x * m, 0) / (mass.reduce((sum, m) => sum + m, 0) || 1);
  for (let i = 0; i < 30; i++) {
    const from = Math.max(0, Math.round(centre - size / 2)), to = Math.min(width, Math.round(centre + size / 2));
    let total = 0, moment = 0;
    for (let x = from; x < to; x++) { total += mass[x]; moment += x * mass[x]; }
    if (total === 0) return DEFAULT_CROP_LEFT;
    const next = moment / total;
    if (Math.abs(next - centre) < 0.25) break;
    centre = next;
  }
  // Never past the canvas edge, where the card would show an empty strip.
  return Math.min(Math.max(centre - size / 2, 0), width - size) / width;
}
