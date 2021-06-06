import { Injectable } from "@angular/core";
import { formatDate } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class DateFormat {  
  format(toBeFormatted): string {
    const format = 'dd/MM/yyyy hh:mm';
    const locale = 'en-US';
    return formatDate(toBeFormatted, format, locale);
  }
}