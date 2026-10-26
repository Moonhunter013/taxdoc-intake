import { pdf } from '../testing/fixtures';
import { MAX_UPLOAD_BYTES, checkFile } from './file-checks';

describe('checkFile', () => {
  it('accepts a small PDF', async () => {
    expect(await checkFile(pdf())).toBeNull();
  });

  it('accepts a PDF of exactly 5 MB, like the server', async () => {
    expect(await checkFile(pdf('max.pdf', MAX_UPLOAD_BYTES))).toBeNull();
  });

  it('rejects anything over 5 MB', async () => {
    expect(await checkFile(pdf('big.pdf', MAX_UPLOAD_BYTES + 1))).toBe(
      'This file is 5.0 MB. The limit is 5 MB.',
    );
  });

  it('checks the content, not the name: a GIF renamed to .pdf is rejected', async () => {
    const gif = new File([new TextEncoder().encode('GIF89a not a pdf')], 'w2.pdf', {
      type: 'application/pdf',
    });
    expect(await checkFile(gif)).toContain("isn't a PDF");
  });

  it('accepts a real PDF even if the browser reports no type', async () => {
    const untyped = new File([new TextEncoder().encode('%PDF-1.4\n...')], 'scan', { type: '' });
    expect(await checkFile(untyped)).toBeNull();
  });
});
