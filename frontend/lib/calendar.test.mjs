import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

const source = readFileSync(new URL("./calendar.ts", import.meta.url), "utf8");
const compiled = ts.transpile(source, { module: ts.ModuleKind.ESNext });
const { googleCalendarUrl, icsFile } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`);

const event = {
  uid: "tournament-7@warrenament",
  title: "Friday Night Cup, round 2; finals",
  startsAt: "2026-10-02T23:30:00.000Z",
  url: "https://example.test/t/friday-night-cup",
};

test("the Google link covers three hours from the start, in UTC", () => {
  const url = new URL(googleCalendarUrl(event));
  assert.equal(url.searchParams.get("action"), "TEMPLATE");
  assert.equal(url.searchParams.get("text"), event.title);
  assert.equal(url.searchParams.get("dates"), "20261002T233000Z/20261003T023000Z");
});

test("the .ics escapes separators and uses CRLF line endings", () => {
  const ics = icsFile(event, new Date("2026-09-29T12:00:00Z"));
  assert.ok(ics.includes("SUMMARY:Friday Night Cup\\, round 2\\; finals\r\n"));
  assert.ok(ics.includes("DTSTART:20261002T233000Z\r\n"));
  assert.ok(ics.includes("DTEND:20261003T023000Z\r\n"));
  assert.ok(ics.includes("UID:tournament-7@warrenament\r\n"));
  assert.ok(ics.endsWith("END:VCALENDAR\r\n"));
  assert.ok(!/[^\r]\n/.test(ics));
});

test("long lines fold at 75 characters", () => {
  const ics = icsFile({ ...event, title: "x".repeat(200) });
  for (const line of ics.split("\r\n")) assert.ok(line.length <= 75, line);
  const summary = ics.split("\r\nDESCRIPTION")[0].split("SUMMARY:")[1].replace(/\r\n /g, "");
  assert.equal(summary, "x".repeat(200));
});
