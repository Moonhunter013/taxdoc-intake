/**
 * Formats a decimal string from the APIs ("118500.5", "6656.67") as US dollars ("$118,500.50")
 * without ever converting it to a JavaScript number, so no floating-point rounding can creep in.
 * Anything that isn't a plain non-negative decimal is returned unchanged.
 */
export function formatMoney(value: unknown): string {
  if (typeof value !== 'string' || !/^\d+(\.\d+)?$/.test(value)) {
    return value == null ? '' : String(value);
  }
  const [whole, fraction = ''] = value.split('.');
  const cents = roundToCents(whole, fraction);
  const grouped = cents.whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `$${grouped}.${cents.fraction}`;
}

/** "12.345" -> 12.35 using string digits only (half up, like the services). */
function roundToCents(whole: string, fraction: string): { whole: string; fraction: string } {
  if (fraction.length <= 2) {
    return { whole: stripLeadingZeros(whole), fraction: fraction.padEnd(2, '0') };
  }
  const digits = (whole + fraction.slice(0, 2)).split('').map(Number);
  if (Number(fraction[2]) >= 5) {
    let i = digits.length - 1;
    while (i >= 0) {
      if (digits[i] < 9) {
        digits[i] += 1;
        break;
      }
      digits[i] = 0;
      i -= 1;
    }
    if (i < 0) {
      digits.unshift(1);
    }
  }
  const all = digits.join('');
  return { whole: stripLeadingZeros(all.slice(0, -2)), fraction: all.slice(-2) };
}

function stripLeadingZeros(s: string): string {
  return s.replace(/^0+(?=\d)/, '') || '0';
}
