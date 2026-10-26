import { formatMoney } from './money';

describe('formatMoney', () => {
  it.each([
    ['118500.00', '$118,500.00'],
    ['6656.67', '$6,656.67'],
    ['0.00', '$0.00'],
    ['5', '$5.00'],
    ['1196.25', '$1,196.25'],
    ['1234567.8', '$1,234,567.80'],
    ['007.10', '$7.10'],
  ])('%s -> %s', (input, expected) => {
    expect(formatMoney(input)).toBe(expected);
  });

  it('rounds half up using the digits, not floating point', () => {
    // As a JavaScript number, 1.005 is 1.00499999...; toFixed(2) would give "1.00".
    expect(formatMoney('1.005')).toBe('$1.01');
    expect(formatMoney('0.004')).toBe('$0.00');
    expect(formatMoney('999.995')).toBe('$1,000.00');
  });

  it('keeps cents exact on amounts too large for a double', () => {
    expect(formatMoney('90071992547409931.37')).toBe('$90,071,992,547,409,931.37');
  });

  it('returns anything that is not a plain decimal unchanged', () => {
    expect(formatMoney('-5.00')).toBe('-5.00');
    expect(formatMoney('n/a')).toBe('n/a');
    expect(formatMoney(null)).toBe('');
    expect(formatMoney(12.5)).toBe('12.5'); // a number means the contract changed; don't hide it
  });
});
