import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

const source = readFileSync(new URL("./draft-advice.ts", import.meta.url), "utf8");
const compiled = ts.transpile(source, { module: ts.ModuleKind.ESNext });
const { rankCandidates, roleCoverage } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`);

// Riot's tiers: Iron 1 = 3, then three per division.
const TIERS = { "Iron 1": 3, "Bronze 1": 6, "Silver 1": 9, "Gold 1": 12, "Gold 3": 14, "Platinum 1": 15, "Diamond 1": 18, "Ascendant 1": 21 };
const tierOf = (rank) => (rank && rank in TIERS ? TIERS[rank] : null);

let id = 0;
const player = (rank, primaryRole, secondaryRole = null) =>
  ({ profileId: ++id, username: `p${id}`, currentRank: rank, primaryRole, secondaryRole });
const names = (advice) => advice.map((a) => `${a.player.currentRank} ${a.player.primaryRole}`);

test("with nothing to split them, the higher rank comes first", () => {
  const roster = [player("Gold 1", "Duelist"), player("Gold 1", "Controller"), player("Gold 1", "Initiator"), player("Gold 1", "Sentinel")];
  const advice = rankCandidates([player("Gold 1", "Duelist"), player("Diamond 1", "Duelist")], roster, 1, tierOf);
  assert.deepEqual(names(advice), ["Diamond 1 Duelist", "Gold 1 Duelist"]);
});

test("between similar ranks, filling a missing role wins", () => {
  const roster = [player("Gold 1", "Duelist")];
  const advice = rankCandidates([player("Gold 3", "Duelist"), player("Gold 1", "Controller")], roster, 4, tierOf);
  assert.equal(advice[0].player.primaryRole, "Controller");
  assert.equal(advice[0].fills, "Controller");
  assert.equal(advice[0].fillsWith, "primary");
});

test("a drastic rank gap beats team comp: Diamond duelist over Bronze initiator", () => {
  const roster = [player("Gold 1", "Duelist"), player("Gold 1", "Controller"), player("Gold 1", "Sentinel")];
  const advice = rankCandidates([player("Bronze 1", "Initiator"), player("Diamond 1", "Duelist")], roster, 1, tierOf);
  assert.deepEqual(names(advice), ["Diamond 1 Duelist", "Bronze 1 Initiator"]);
  // ...even with the pressure of it being the last slot and the only missing role.
  assert.equal(advice[1].fills, "Initiator");
});

test("a secondary role counts, but less than a primary", () => {
  const roster = [player("Gold 1", "Duelist")];
  const primary = player("Gold 1", "Controller");
  const secondary = player("Gold 1", "Duelist", "Controller");
  const [first, second] = rankCandidates([secondary, primary], roster, 4, tierOf);
  assert.equal(first.player, primary);
  assert.equal(second.fillsWith, "secondary");
  assert.ok(second.score > 12, "a secondary fill still beats no fill at all");
});

test("the roster's secondary roles partly cover a need", () => {
  const coverage = roleCoverage([player("Gold 1", "Duelist", "Sentinel")]);
  assert.equal(coverage.get("Duelist"), 1);
  assert.equal(coverage.get("Sentinel"), 0.6);
  assert.equal(coverage.get("Controller"), 0);
});

test("role fit matters more when slots run short", () => {
  const roster = [player("Gold 1", "Duelist")];
  const candidates = [player("Platinum 1", "Duelist"), player("Gold 1", "Controller")];
  // Plenty of room: a rank higher (3 points) ties the role bonus, and the higher rank wins the tie.
  assert.equal(rankCandidates(candidates, roster, 4, tierOf)[0].player.primaryRole, "Duelist");
  // One slot, three roles missing: the controller is now worth more.
  assert.equal(rankCandidates(candidates, roster, 1, tierOf)[0].player.primaryRole, "Controller");
});

test("unranked players rank below Iron", () => {
  const roster = [player("Gold 1", "Duelist"), player("Gold 1", "Controller"), player("Gold 1", "Initiator"), player("Gold 1", "Sentinel")];
  const advice = rankCandidates([player(null, "Duelist"), player("Iron 1", "Duelist")], roster, 1, tierOf);
  assert.deepEqual(names(advice), ["Iron 1 Duelist", "null Duelist"]);
  assert.equal(advice[1].tier, 0);
});

test("a full roster gets no advice", () => {
  assert.deepEqual(rankCandidates([player("Gold 1", "Duelist")], [], 0, tierOf), []);
});
