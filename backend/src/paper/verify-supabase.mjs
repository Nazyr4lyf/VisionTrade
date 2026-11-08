import { SupabasePaperTradeStore } from "./supabase-paper-trade-store.mjs";

const sentinelId = "__visiontrade_read_only_connection_check__";

try {
  const store = new SupabasePaperTradeStore();
  await store.findById(sentinelId);
  console.info(JSON.stringify({
    event: "supabase_read_only_check_passed",
    projectConfigured: true,
    operation: "get_paper_trade_by_id",
    writeOperationsPerformed: 0
  }));
} catch (error) {
  console.error(JSON.stringify({
    event: "supabase_read_only_check_failed",
    message: error.message
  }));
  process.exitCode = 1;
}
