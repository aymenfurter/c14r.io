import { NgModule } from '@angular/core';
import {
  NbButtonModule,
  NbCardModule,
  NbProgressBarModule,
  NbTabsetModule,
  NbUserModule,
  NbIconModule,
  NbSelectModule,
  NbListModule,
  NbAutocompleteModule,
} from '@nebular/theme';
import { NgxEchartsModule } from 'ngx-echarts';
import { NgxChartsModule } from '@swimlane/ngx-charts';

import { ThemeModule } from '../../@theme/theme.module';
import { SearchComponent } from './search.component';
import { ChartModule } from 'angular2-chartjs'
import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Infopanel } from './infopanel.component';
import { Searchbar } from './searchbar.component';

@NgModule({
  imports: [
    ThemeModule,
    NbCardModule,
    NbUserModule,
    NbButtonModule,
    NbIconModule,
    NbTabsetModule,
    NbSelectModule,
    NbListModule,
    ChartModule,
    NbProgressBarModule,
    NgxEchartsModule,
    NgxChartsModule,
    HttpClientModule,
    FormsModule,
    NbAutocompleteModule
  ],
  declarations: [
    SearchComponent,
    Searchbar,
    Infopanel
  ],
  providers: [
  ],
})
export class SearchModule { }
