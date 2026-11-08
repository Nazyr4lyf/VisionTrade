const TABLE = "visiontrade_monitor_snapshot";

export class SupabaseMonitorSnapshotStore {
  constructor({
    url = process.env.SUPABASE_URL,
    serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY,
    fetchImpl = fetch
  } = {}) {
    this.url = typeof url === "string" ? url.replace(/\/+$/, "") : "";
    this.serviceRoleKey = typeof serviceRoleKey === "string" ? serviceRoleKey.trim() : "";
    this.fetchImpl = fetchImpl;
    this.enabled = Boolean(this.url && this.serviceRoleKey);
    if (this.enabled && !/^https:\/\//i.test(this.url)) {
      throw new Error("SUPABASE_URL must use HTTPS");
    }
  }

  async publish(snapshot) {
    if (!this.enabled) return { skipped: true, reason: "Supabase snapshot credentials are not configured" };
    const payload = {
      id: "latest",
      mode: snapshot.mode ?? "LIVE_MARKET_MONITORING",
      worker_running: Boolean(snapshot.running),
      last_scan_at: snapshot.lastScanAt ?? null,
      last_scan_pairs: Math.max(0, Number(snapshot.lastScanPairs) || 0),
      actionable_signals: Math.max(0, Number(snapshot.actionableSignals) || 0),
      recent_signals: Array.isArray(snapshot.recentSignals) ? snapshot.recentSignals.slice(0, 10) : [],
      updated_at: new Date().toISOString()
    };
    const response = await this.fetchImpl(
      this.url + "/rest/v1/" + TABLE + "?on_conflict=id",
      {
        method: "POST",
        headers: {
          apikey: this.serviceRoleKey,
          authorization: "Bearer " + this.serviceRoleKey,
          "content-type": "application/json",
          prefer: "resolution=merge-duplicates,return=minimal"
        },
        body: JSON.stringify(payload)
      }
    );
    if (!response.ok) {
      throw new Error("Supabase monitoring snapshot write failed (" + response.status + ")");
    }
    return { skipped: false, updatedAt: payload.updated_at };
  }
}
