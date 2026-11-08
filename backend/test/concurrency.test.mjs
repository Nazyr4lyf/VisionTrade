import test from "node:test";
import assert from "node:assert/strict";
import { mapWithConcurrency } from "../src/utils/concurrency.mjs";

test("never exceeds configured concurrency and preserves input order", async () => {
  let active = 0;
  let peak = 0;
  const results = await mapWithConcurrency([1, 2, 3, 4, 5, 6], 2, async value => {
    active += 1;
    peak = Math.max(peak, active);
    await new Promise(resolve => setTimeout(resolve, 2));
    active -= 1;
    return value * 10;
  });
  assert.equal(peak, 2);
  assert.deepEqual(results, [10, 20, 30, 40, 50, 60].map(value => ({ status: "fulfilled", value })));
});

test("captures a task failure without cancelling remaining tasks", async () => {
  const results = await mapWithConcurrency([1, 2, 3], 2, async value => {
    if (value === 2) throw new Error("temporary failure");
    return value;
  });
  assert.deepEqual(results.map(item => item.status), ["fulfilled", "rejected", "fulfilled"]);
  assert.match(results[1].reason.message, /temporary failure/);
});

test("rejects invalid concurrency", async () => {
  await assert.rejects(() => mapWithConcurrency([], 0, async value => value), /positive integer/);
});
