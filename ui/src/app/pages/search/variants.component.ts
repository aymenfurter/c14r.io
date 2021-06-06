import { Input, Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit, Output, EventEmitter } from '@angular/core';
import { DateFormat } from './date';

@Component({
  selector: 'variants',
  styleUrls: ['./variants.component.scss'],
  templateUrl: './variants.component.html'
})
export class Variants {
  @Input('hashes') hashes;
  @Input('detailDataTags') detailDataTags;
  @Input('detailDataTagsFull') detailDataTagsFull;
  @Input('slimMode') slimMode;
  @Input('currentHash') currentHash;

  constructor(private date: DateFormat) {}
  
  @Output() selectHashEvent = new EventEmitter<string>();
  fireSelectHashEvent(hash: string) {
    this.selectHashEvent.emit(hash);
  }

  @Output() selectTagEvent = new EventEmitter<string>();
  fireSelectTagEvent(tag: string) {
    this.selectTagEvent.emit(tag);
  }

  formatDate(toBeFormatted): string {
    return this.date.format(toBeFormatted);
  }

  toggleSlimMode() {
    this.detailDataTags = this.detailDataTagsFull;
    this.slimMode = false;
  }
}