/**
 * Formate une durée en minutes en une chaîne lisible et conviviale.
 *
 * @param minutes Durée en minutes
 * @returns Chaîne formatée (ex: "45 min", "3h 15m", "2j 5h")
 */
export function formatTempsMoyenStr(minutes: number): string {
  if (!minutes || minutes <= 0) return '0 min';

  if (minutes < 60) {
    return `${Math.round(minutes)} min`;
  }

  const hours = minutes / 60;
  if (hours < 24) {
    const h = Math.floor(hours);
    const m = Math.round((hours - h) * 60);
    return m > 0 ? `${h}h ${m}m` : `${h}h`;
  }

  const days = hours / 24;
  const d = Math.floor(days);
  const h = Math.round((days - d) * 24);
  return h > 0 ? `${d}j ${h}h` : `${d}j`;
}
