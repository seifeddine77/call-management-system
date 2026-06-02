/**
 * Cryptographically-safe random utilities.
 * Replaces Math.random() for all non-trivial use cases to satisfy
 * SonarCloud S2245 (Weak Cryptography / PRNG hotspot).
 *
 * Note: These functions are used for UI animations and visual generation only.
 * They are NOT used for security-sensitive operations (tokens, passwords, etc.).
 */

const MAX_UINT32 = 0xffffffff;

/**
 * Returns a cryptographically-safe float in [0, 1).
 * Drop-in replacement for Math.random().
 */
export function safeRandom(): number {
  const buf = new Uint32Array(1);
  crypto.getRandomValues(buf);
  return buf[0] / (MAX_UINT32 + 1);
}

/**
 * Returns a cryptographically-safe float in [min, max).
 */
export function safeRandomBetween(min: number, max: number): number {
  return min + safeRandom() * (max - min);
}

/**
 * Returns a cryptographically-safe integer in [0, max).
 */
export function safeRandomInt(max: number): number {
  return Math.floor(safeRandom() * max);
}
