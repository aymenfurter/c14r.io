import { Input, Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit, Output, EventEmitter } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'searchbar',
  styleUrls: ['./searchbar.component.scss'],
  templateUrl: './searchbar.component.html'
})
export class Searchbar {
  @Input('api') api;
  imageName: string;

  @Output() newSearchEvent = new EventEmitter<string>();
  fireNewSearchEvent() {
    this.newSearchEvent.emit(this.imageName);
  }

  @Output() expandAllEvent = new EventEmitter<string>();
  fireExpandAllEvent() {
    this.expandAllEvent.emit();
  }
  
  @ViewChild("searchInput") searchInputField: ElementRef;
  ngAfterViewInit() {
    this.searchInputField.nativeElement.focus();
  }

  options: string[];
  filteredOptions$: Observable<string[]>;
  @ViewChild('searchInput') input;

  initOptions() {
    this.options = [];
    this.filteredOptions$ = of(this.options);
  }

  private filter(value: string): string[] {
    const filterValue = value.toLowerCase();
    return this.options.filter(optionValue => optionValue.toLowerCase().includes(filterValue));
  }

  getFilteredOptions(value: string): Observable<string[]> {
    return of(value).pipe(
      map(filterString => this.filter(filterString)),
    );
  }

  onChange() {
    var change = this;
    this.api.getAutocomplete(this.imageName).subscribe(data => {
      var qries = <Array<any>>data["queries"];
      change.options = qries;
      change.filteredOptions$ = change.getFilteredOptions(change.input.nativeElement.value);
    });

  }

  onSelectionChange($event) {
    this.filteredOptions$ = this.getFilteredOptions($event);
  }

  
  ngOnInit(): void {
    this.initOptions();
  }
}