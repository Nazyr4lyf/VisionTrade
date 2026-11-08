import test from "node:test";
import assert from "node:assert/strict";
import { SupabaseMonitorSnapshotStore } from "../src/monitoring/supabase-monitor-snapshot-store.mjs";

test("snapshot store skips safely when server credentials are absent", async () => {
  const store = new SupabaseMonitorSnapshotStore({ url: "", serviceRoleKey: "" });
  assert.deepEqual(await store.publish({ running: true }), {
    skipped: true,
    reason: "Supabase snapshot credentials are not configured"
  });
});

test("snapshot store upserts a bounded read-only market snapshot", async () => {
  let request;
  const store = new SupabaseMonitorSnapshotStore({
    url: "https://example.supabase.co/",
    serviceRoleKey: "server-only-key",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return { ok: true, status: 201 };
    }
  });
  const result = await store.publish({
    running: true,
    mode: "LIVE_MARKET_MONITORING",
    lastScanAt: "2026-11-08T12:00:00.000Z",
    lastScanPairs: 30,
    actionableSignals: 1,
    recentSignals: [{ symbol: "BTCUSDT", action: "BUY" }]
  });
  assert.equal(result.skipped, false);
  assert.equal(request.url, "https://example.supabase.co/rest/v1/visiontrade_monitor_snapshot?on_conflict=id");
  assert.equal(request.options.method, "POST");
  assert.equal(request.options.headers.apikey, "server-only-key");
  const payload = JSON.parse(request.options.body);
  assert.equal(payload.id, "latest");
  assert.equal(payload.worker_running, true);
  assert.equal(payload.last_scan_pairs, 30);
  assert.deepEqual(payload.recent_signals, [{ symbol: "BTCUSDT", action: "BUY" }]);
});

test("snapshot store rejects insecure Supabase endpoints", () => {
  assert.throws(
    () => new SupabaseMonitorSnapshotStore({ url: "http://example.supabase.co", serviceRoleKey: "key" }),
    /HTTPS/
  );
});
