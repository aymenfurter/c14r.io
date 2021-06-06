import { Input, Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit, Output, EventEmitter } from '@angular/core';
import { DateFormat } from './date';

@Component({
  selector: 'layer',
  styleUrls: ['./layer.component.scss'],
  templateUrl: './layer.component.html'
})
export class Layer {
  @Input('img') img;

  constructor(private date: DateFormat) {}
  
  formatDate(toBeFormatted): string {
    return this.date.format(toBeFormatted);
  }
}