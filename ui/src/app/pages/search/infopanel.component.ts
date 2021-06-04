import { Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'infopanel',
  styleUrls: ['./infopanel.component.scss'],
  templateUrl: './infopanel.component.html'
})
export class Infopanel {
  loadExample(): void {    
      this.fireSearchEvent("library/debian:bullseye");
  }
  
  loadExample2(): void {
      this.fireSearchEvent("mcr.microsoft.com/dotnet/core/runtime-deps:latest");
  }

  @Output() searchEvent = new EventEmitter<string>();

  fireSearchEvent(imageName: string) {
    this.searchEvent.emit(imageName);
  }
}