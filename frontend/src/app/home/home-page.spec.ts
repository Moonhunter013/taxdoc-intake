import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { HomePage } from './home-page';

describe('HomePage', () => {
  async function setup() {
    TestBed.configureTestingModule({ imports: [HomePage], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HomePage);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector<HTMLInputElement>('#client-ref')!;
    const submit = async (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { el, input, navigate, submit };
  }

  it('opens the upload page for a valid client reference', async () => {
    const { navigate, submit } = await setup();
    await submit('client-02');
    expect(navigate).toHaveBeenCalledWith(['/clients', 'client-02', 'upload']);
  });

  it('rejects a reference that extraction would refuse, and says why', async () => {
    const { el, input, navigate, submit } = await setup();
    await submit('../etc');

    expect(navigate).not.toHaveBeenCalled();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain(
      "letters, digits, '-' or '_'",
    );
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });

  it('labels the input', async () => {
    const { el } = await setup();
    expect(el.querySelector('label[for="client-ref"]')!.textContent).toContain('Client reference');
  });
});
