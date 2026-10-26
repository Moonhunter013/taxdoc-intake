import { Routes } from '@angular/router';

import { HomePage } from './home/home-page';
import { NotFoundPage } from './not-found/not-found-page';
import { ReviewPage } from './review/review-page';
import { UploadPage } from './upload/upload-page';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'TaxDoc Intake' },
  {
    path: 'clients/:clientRef/upload',
    component: UploadPage,
    title: 'Upload documents · TaxDoc Intake',
  },
  {
    path: 'clients/:clientRef/documents/:id',
    component: ReviewPage,
    title: 'Review document · TaxDoc Intake',
  },
  { path: '**', component: NotFoundPage, title: 'Page not found · TaxDoc Intake' },
];
