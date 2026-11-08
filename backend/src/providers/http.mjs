const DEFAULT_TIMEOUT_MS = 10_000;
const RETRYABLE_STATUS = new Set([408, 425, 429, 500, 502, 503, 504]);

export async function fetchJson(url, {
  fetchImpl = fetch,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  retries = 2,
  headers = {}
} = {}) {
  let lastError;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetchImpl(url, {
        method: "GET",
        headers: { accept: "application/json", ...headers },
        signal: controller.signal
      });

      if (!response.ok) {
        const error = new Error(`HTTP ${response.status} from market-data provider`);
        error.status = response.status;
        if (!RETRYABLE_STATUS.has(response.status) || attempt === retries) throw error;
        lastError = error;
      } else {
        return await response.json();
      }
    } catch (error) {
      lastError = error;
      const retryable = error.name === "AbortError" ||
        error.status === undefined || RETRYABLE_STATUS.has(error.status);
      if (!retryable || attempt === retries) throw error;
    } finally {
      clearTimeout(timeout);
    }

    const backoffMs = Math.min(250 * (2 ** attempt), 2_000);
    await new Promise(resolve => setTimeout(resolve, backoffMs));
  }

  throw lastError ?? new Error("Market-data request failed");
}
