/**
 * Run async work with a fixed maximum number of active tasks.
 * Results preserve input order and use Promise.allSettled semantics.
 */
export async function mapWithConcurrency(items, concurrency, mapper) {
  if (!Array.isArray(items)) throw new TypeError("items must be an array");
  if (!Number.isInteger(concurrency) || concurrency < 1) {
    throw new RangeError("concurrency must be a positive integer");
  }
  if (typeof mapper !== "function") throw new TypeError("mapper must be a function");

  const results = new Array(items.length);
  let nextIndex = 0;

  async function worker() {
    while (true) {
      const index = nextIndex++;
      if (index >= items.length) return;
      try {
        results[index] = { status: "fulfilled", value: await mapper(items[index], index) };
      } catch (reason) {
        results[index] = { status: "rejected", reason };
      }
    }
  }

  const workerCount = Math.min(concurrency, items.length);
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  return results;
}
