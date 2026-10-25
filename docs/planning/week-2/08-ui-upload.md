## Why
First screen of the demo: an advisor uploads a client's forms.

## What
- Angular route `/clients/:clientRef/upload`: pick or drop PDFs, upload each to extraction,
  show progress and per-file result (form type, needs review, or the API's error message)
- Tailwind for styling; typed API service with `HttpClient`
- Dev proxy config so `ng serve` reaches both backends without CORS hacks

## Acceptance criteria
- [ ] Client-side checks mirror the server (PDF, <= 5 MB) but the server stays the authority
- [ ] 413/415/422/502 responses shown as friendly messages
- [ ] Component tests for success and error states
- [ ] Accessible: keyboard-usable, labelled inputs, visible focus
