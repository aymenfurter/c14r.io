import { Input, Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit, Output, EventEmitter } from '@angular/core';
import { formatDate } from "@angular/common";

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

  @Output() selectHashEvent = new EventEmitter<string>();
  fireSelectHashEvent(hash: string) {
    this.selectHashEvent.emit(hash);
  }

  @Output() selectTagEvent = new EventEmitter<string>();
  fireSelectTagEvent(tag: string) {
    this.selectTagEvent.emit(tag);
  }

  formatDate(toBeFormatted): string {
    const format = 'dd/MM/yyyy hh:mm';
    const locale = 'en-US';
    const formattedDate = formatDate(toBeFormatted, format, locale);
    return formattedDate;
  }

  toggleSlimMode() {
    this.detailDataTags = this.detailDataTagsFull;
    this.slimMode = false;
  }
  
  ngOnInit(): void {
  }
}