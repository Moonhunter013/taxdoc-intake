import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ExtractedDocument, ExtractionApi } from '../api/extraction-api';
import { describeUploadError } from '../api/upload-errors';
import { checkFile } from './file-checks';

export type UploadStatus = 'queued' | 'rejected' | 'uploading' | 'processing' | 'done' | 'failed';

export interface UploadItem {
  id: number;
  file: File;
  status: UploadStatus;
  percent: number;
  message?: string;
  result?: ExtractedDocument;
}

/**
 * /clients/:clientRef/upload. Files are checked locally, then uploaded one at a time (Textract
 * takes several seconds per form, and parallel uploads would only make every row slower).
 */
@Component({
  selector: 'app-upload-page',
  imports: [RouterLink],
  templateUrl: './upload-page.html',
})
export class UploadPage implements OnInit {
  private readonly api = inject(ExtractionApi);

  /** From the route (withComponentInputBinding). */
  readonly clientRef = input.required<string>();

  protected readonly items = signal<UploadItem[]>([]);
  protected readonly dragging = signal(false);
  /** About the client as a whole, e.g. documents from different taxpayers (possible misfiling). */
  protected readonly clientWarnings = signal<string[]>([]);

  private nextId = 1;
  private running = false;

  ngOnInit(): void {
    this.refreshClientWarnings(); // warnings from earlier uploads show as soon as the page opens
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    input.value = ''; // so choosing the same file again still fires "change"
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault(); // required for the browser to allow a drop
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  /** Adds files to the list and starts the queue. Public so tests can add files directly. */
  addFiles(files: File[]): void {
    const added = files.map((file): UploadItem => ({
      id: this.nextId++,
      file,
      status: 'queued',
      percent: 0,
    }));
    this.items.update((items) => [...items, ...added]);
    void this.processQueue();
  }

  protected formLabel(doc: ExtractedDocument): string {
    const from = doc.document['employer_name'] ?? doc.document['payer_name'];
    return typeof from === 'string' ? `${doc.form_type} from ${from}` : doc.form_type;
  }

  private async processQueue(): Promise<void> {
    if (this.running) {
      return; // the running loop will pick up newly added files
    }
    this.running = true;
    try {
      let item: UploadItem | undefined;
      while ((item = this.items().find((i) => i.status === 'queued'))) {
        await this.uploadOne(item);
      }
    } finally {
      this.running = false;
    }
  }

  private async uploadOne(item: UploadItem): Promise<void> {
    const rejection = await checkFile(item.file);
    if (rejection) {
      this.patch(item.id, { status: 'rejected', message: rejection });
      return;
    }
    this.patch(item.id, { status: 'uploading', percent: 0 });
    await new Promise<void>((resolve) => {
      this.api.upload(item.file, this.clientRef()).subscribe({
        next: (event) => {
          if (event.kind === 'progress') {
            this.patch(item.id, { percent: event.percent });
          } else if (event.kind === 'processing') {
            this.patch(item.id, { status: 'processing', percent: 100 });
          } else {
            this.patch(item.id, { status: 'done', result: event.document });
            // A new document can change the client-level picture (e.g. a second taxpayer).
            this.refreshClientWarnings();
          }
        },
        error: (error: HttpErrorResponse) => {
          this.patch(item.id, { status: 'failed', message: describeUploadError(error) });
          resolve();
        },
        complete: () => resolve(),
      });
    });
  }

  /**
   * Best effort: if the request fails, keep the last known warnings rather than block uploads.
   * The warnings are advisory; each file's own result is what the advisor acts on.
   */
  private refreshClientWarnings(): void {
    this.api.clientDocuments(this.clientRef()).subscribe({
      next: (client) => this.clientWarnings.set(client.warnings),
      error: () => undefined,
    });
  }

  private patch(id: number, changes: Partial<UploadItem>): void {
    this.items.update((items) => items.map((i) => (i.id === id ? { ...i, ...changes } : i)));
  }
}
