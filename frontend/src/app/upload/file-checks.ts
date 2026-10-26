/**
 * Quick checks before uploading, mirroring extraction-service (_validate_pdf): at most 5 MB
 * (its max_upload_bytes) and the content must start with "%PDF-". The server still decides;
 * these only save a slow round trip for obvious mistakes.
 */
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

const PDF_MAGIC = '%PDF-';

/** A reason to reject the file, or null if it can be sent. */
export async function checkFile(file: File): Promise<string | null> {
  if (file.size > MAX_UPLOAD_BYTES) {
    return `This file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is 5 MB.`;
  }
  // Like the server, check what the file contains, not its name or type.
  const head = new Uint8Array(await file.slice(0, PDF_MAGIC.length).arrayBuffer());
  const text = String.fromCharCode(...head);
  if (text !== PDF_MAGIC) {
    return "This isn't a PDF. Upload the form as a PDF file.";
  }
  return null;
}
