import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ExtractedDocument } from '../api/extraction-api';
import { client01W2, client01W2WithBadBox4, meta } from '../testing/review-fixtures';
import { CORRECTED_BY_KEY, ReviewPage } from './review-page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));
const DOC = client01W2();
const DOC_URL = `/api/v1/documents/${DOC.document_id}`;

describe('ReviewPage', () => {
  let fixture: ComponentFixture<ReviewPage>;
  let http: HttpTestingController;
  let el: HTMLElement;

  async function open(doc: ExtractedDocument = DOC, name = 'Paul'): Promise<void> {
    sessionStorage.clear();
    if (name) {
      sessionStorage.setItem(CORRECTED_BY_KEY, name);
    }
    TestBed.configureTestingModule({
      imports: [ReviewPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ReviewPage);
    fixture.componentRef.setInput('clientRef', 'client-01');
    fixture.componentRef.setInput('id', doc.document_id);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne(DOC_URL).flush(doc);
    await render();
  }

  async function render(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await settle();
  }

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  function field(name: string): HTMLElement {
    return el.querySelector<HTMLElement>(`[data-field="${name}"]`)!;
  }

  async function edit(name: string): Promise<void> {
    field(name).querySelector<HTMLButtonElement>('button')!.click();
    await render();
  }

  async function type(name: string, value: string): Promise<void> {
    const input = field(name).querySelector<HTMLInputElement>('form input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await render();
  }

  async function submit(name: string): Promise<void> {
    field(name).querySelector('form')!.dispatchEvent(new Event('submit'));
    await render();
  }

  // --- display ---------------------------------------------------------------------------

  it('shows the fields in form order, grouped like the printed W-2, with money as dollars', async () => {
    await open();
    const headings = [...el.querySelectorAll('section h3')].map((h) => h.textContent!.trim());
    expect(headings).toEqual([
      'Employer',
      'Employee',
      'Wages and taxes',
      'Boxes 12 and 13',
      'State',
    ]);
    expect(field('box1_wages').textContent).toContain('$203,000.00');
    expect(field('box12').textContent).toContain('D $9,000.00');
    expect(field('box13_retirement_plan').textContent).toContain('Yes');
    expect(field('box1_wages').textContent).toContain('Textract confidence 96.4%');
  });

  it('shows SSNs and EINs masked, exactly as the API sends them', async () => {
    await open();
    expect(field('employee_ssn').textContent).toContain('***-**-2201');
    expect(field('employer_ein').textContent).toContain('**-***2201');
  });

  it('highlights a flagged field with its own reason, and lists every reason at the top', async () => {
    await open(client01W2WithBadBox4());

    const box4 = field('box4_ss_tax');
    expect(box4.classList).toContain('bg-amber-50');
    expect(box4.textContent).toContain('box 4 is $1,439.00 but 6.2% of box 3 is $11,439.00');
    expect(box4.textContent).toContain('Corrected by Paul');
    expect(field('box3_ss_wages').classList).not.toContain('bg-amber-50');
    expect(el.textContent).toContain('To check');
    expect(el.textContent).toContain('Needs review');
  });

  it('says so when the document does not exist', async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ReviewPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ReviewPage);
    fixture.componentRef.setInput('clientRef', 'client-01');
    fixture.componentRef.setInput('id', 'missing');
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http
      .expectOne('/api/v1/documents/missing')
      .flush({ detail: 'Document not found' }, { status: 404, statusText: 'Not Found' });
    await render();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('not found');
  });

  // --- the spec's round trip -------------------------------------------------------------

  it('editing box 4 to an inconsistent value shows the new flag from the API immediately', async () => {
    await open();
    await edit('box4_ss_tax');
    await type('box4_ss_tax', '1439.00');
    await submit('box4_ss_tax');

    const req = http.expectOne(`${DOC_URL}/fields/box4_ss_tax`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ value: '1439.00', corrected_by: 'Paul' });
    req.flush(client01W2WithBadBox4());
    await render();

    expect(field('box4_ss_tax').textContent).toContain('$1,439.00');
    expect(field('box4_ss_tax').textContent).toContain('6.2% of box 3');
    expect(field('box4_ss_tax').querySelector('form')).toBeNull(); // editor closed
    expect(el.textContent).toContain('Needs review');
  });

  it('accepts money typed with a dollar sign and commas', async () => {
    await open();
    await edit('box1_wages');
    await type('box1_wages', '$203,500.00');
    await submit('box1_wages');

    const req = http.expectOne(`${DOC_URL}/fields/box1_wages`);
    expect(req.request.body.value).toBe('203500.00');
    req.flush(DOC);
  });

  it("shows the API's validation error next to the field and keeps the editor open", async () => {
    await open();
    await edit('box15_state');
    await type('box15_state', 'Colorado');
    await submit('box15_state');

    http
      .expectOne(`${DOC_URL}/fields/box15_state`)
      .flush(
        { detail: "Invalid value for box15_state: String should match pattern '^[A-Z]{2}$'" },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await render();

    const alert = field('box15_state').querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain('Invalid value for box15_state');
    expect(field('box15_state').querySelector('form')).not.toBeNull();
  });

  // --- SSNs ------------------------------------------------------------------------------

  it('starts an SSN edit empty and forgets the typed number after saving', async () => {
    await open();
    await edit('employee_ssn');
    const input = field('employee_ssn').querySelector<HTMLInputElement>('form input')!;
    expect(input.value).toBe(''); // never pre-filled
    expect(input.getAttribute('autocomplete')).toBe('off');

    await type('employee_ssn', '000-12-3456');
    await submit('employee_ssn');
    const req = http.expectOne(`${DOC_URL}/fields/employee_ssn`);
    expect(req.request.body.value).toBe('000-12-3456');
    req.flush(client01W2({ document: { ...DOC.document, employee_ssn: '***-**-3456' } }));
    await render();

    expect(field('employee_ssn').textContent).toContain('***-**-3456');
    expect(el.innerHTML).not.toContain('000-12-3456');
    expect(el.querySelector('form')).toBeNull();
  });

  // --- other field kinds -----------------------------------------------------------------

  it('edits box 12 as code and amount rows and sends the whole list', async () => {
    await open();
    await edit('box12');
    field('box12')
      .querySelector<HTMLButtonElement>('form button[type="button"]:not(.text-red-800)')!
      .click(); // Add a code
    await render();
    const inputs = field('box12').querySelectorAll<HTMLInputElement>('form input');
    inputs[2].value = 'w';
    inputs[2].dispatchEvent(new Event('input'));
    inputs[3].value = '1500.00';
    inputs[3].dispatchEvent(new Event('input'));
    await submit('box12');

    const req = http.expectOne(`${DOC_URL}/fields/box12`);
    expect(req.request.body.value).toEqual([
      { code: 'D', amount: '9000.00' },
      { code: 'W', amount: '1500.00' }, // code upper-cased like the form
    ]);
    req.flush(DOC);
  });

  it('edits box 13 as a checkbox and sends a boolean', async () => {
    await open();
    await edit('box13_retirement_plan');
    const box =
      field('box13_retirement_plan').querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    expect(box.checked).toBe(true);
    box.checked = false;
    box.dispatchEvent(new Event('change'));
    await submit('box13_retirement_plan');

    const req = http.expectOne(`${DOC_URL}/fields/box13_retirement_plan`);
    expect(req.request.body.value).toBe(false);
    req.flush(DOC);
  });

  it('clears an optional field by sending null', async () => {
    await open();
    await edit('employee_name');
    await type('employee_name', '');
    await submit('employee_name');

    const req = http.expectOne(`${DOC_URL}/fields/employee_name`);
    expect(req.request.body.value).toBeNull();
    req.flush(DOC);
  });

  // --- who corrected it ------------------------------------------------------------------

  it('asks for a name before saving, and sends nothing until there is one', async () => {
    await open(DOC, '');
    await edit('box1_wages');
    await type('box1_wages', '1.00');
    await submit('box1_wages');

    http.expectNone(`${DOC_URL}/fields/box1_wages`);
    expect(field('box1_wages').querySelector('[role="alert"]')!.textContent).toContain(
      'Enter your name',
    );
    expect(el.querySelector('#corrected-by')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('remembers the name for this browser tab', async () => {
    await open(DOC, '');
    const input = el.querySelector<HTMLInputElement>('#corrected-by')!;
    input.value = 'Paul Hayward';
    input.dispatchEvent(new Event('input'));
    expect(sessionStorage.getItem(CORRECTED_BY_KEY)).toBe('Paul Hayward');
  });

  // --- keyboard --------------------------------------------------------------------------

  it('moves focus into the editor, and back to the Edit button on Escape', async () => {
    await open();
    await edit('box1_wages');
    expect(document.activeElement).toBe(field('box1_wages').querySelector('form input'));

    field('box1_wages')
      .querySelector('form')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await render();

    expect(field('box1_wages').querySelector('form')).toBeNull();
    expect(document.activeElement).toBe(field('box1_wages').querySelector('button'));
  });

  it('gives every Edit button an accessible name that says which box', async () => {
    await open();
    const button = field('box4_ss_tax').querySelector('button')!;
    expect(button.getAttribute('aria-label')).toBe('Edit 4 Social Security tax withheld');
  });

  it('shows "Entered manually" for a field a person typed in', async () => {
    const doc = client01W2();
    doc.fields['box17_state_tax'] = meta(100, [], {
      source_label: 'manual correction',
      corrected_by: 'Paul',
    });
    await open(doc);
    expect(field('box17_state_tax').textContent).toContain('Entered manually');
  });
});
