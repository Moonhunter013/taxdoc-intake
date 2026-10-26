import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route params arrive as component inputs (e.g. clientRef on the upload page).
    provideRouter(routes, withComponentInputBinding()),
    // Default XHR backend on purpose: it reports upload progress; the fetch backend doesn't.
    provideHttpClient(),
  ],
};
