import { Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { ApiService } from './../../api.service';
import { Infopanel } from './infopanel.component';
import { Searchbar } from './searchbar.component';
import { Variants } from './variants.component';
import { DateFormat } from './date';
import { Graph } from './graph';
import { Toaster } from './toaster';
import { NetworkFactory } from './network';
import { DetailView } from './detailview';

@Component({
  selector: 'ngx-search',
  styleUrls: ['./search.component.scss'],
  templateUrl: './search.component.html',
})

export class SearchComponent {
  @ViewChild('visNetwork', { static: false }) visNetwork!: ElementRef;
  @ViewChild('infopanel') infoPanel: Infopanel;
  @ViewChild('searchbar') searchbar: Searchbar;
  @ViewChild('variants') variants: Variants;

  requested = new Map();
  api: ApiService;
  exampleMode = true;
  slimMode = true;
  imagesAmount = 0;

  hashes: string[] = [];
  imageTree: any[] = [];
  detailDataTags = [];
  detailDataTagsFull = [];
  currentHash;
  currentTag;
  updateInProgress: boolean = false;
  imageName: string;
  delay: any = 1;

  constructor(private detailView: DetailView, private graph: Graph, private networkFactory: NetworkFactory, private toaster: Toaster, private date: DateFormat, api: ApiService) { this.api = api; graph.parent = this; detailView.parent = this}

  resetSearch() {
    this.graph.reset();
    this.slimMode = true;
    this.updateImageDetails();
    this.delay = 1;
  }

  refreshSearch(comp) {
    if (comp.delay == 1) {
      this.toaster.showToast(this.toaster.status, "Not yet indexed", this.imageName + " is not in our index. We've added it now, please try again later. One moment please.");
    }

    comp.delay = comp.delay * 2;
    setTimeout(() => { comp.onSearch() }, comp.delay * 1000);
  }

  indexImage(imageToIndex: string) {
    this.requested.set(imageToIndex, "indexed");
    this.api.ingest(imageToIndex)
      .subscribe(resp => {
        if (resp.status != 202) {
          this.toaster.showToast(this.toaster.status, "Not yet indexed", this.imageName + " is not in our index. Looks like we are overloaded right now, please try again later.");
        }
      });
  }

  onSearch() {
    this.api.getNetwork(this.imageName)
      .subscribe(data => {
        var result = <Array<any>>data;
        if (result.length == 0) {
          this.graph.handleNoResult(this);
        } else {
          this.graph.processResult(data);
        }
        this.imagesAmount = this.graph.amountNodes(); 
      });
  }

  onSearchImageEvent(imageName: string): void {
    this.imageName = imageName;
    this.onSearch();
    this.updateImageDetails();
  }

  onNewSearchImageEvent(imageName: string): void {
    this.imageName = imageName;
    this.resetSearch();
    this.onSearch();
  }

  viewHash(hash) {
    this.currentHash = hash;
    this.buildTree();
  }

  viewTag(tag) {
    this.imageName = this.imageName.substr(0, this.imageName.indexOf(':')) + ":" + tag;
    this.onSearch();
    this.updateImageDetails();
  }

  processImage(value, comp, isRoot) {
    this.detailView.processImage(value, comp, isRoot);

  }

  buildTree(): void {
    this.detailView.buildTree();
  }

  updateImageDetails(): void {
    this.detailView.updateImageDetails();
  }

  ngOnInit(): void {
    const queryString = window.location.search;

    if (queryString != null && queryString != "") {
      this.imageName = decodeURIComponent(queryString).replace("?", "").replace("=", "");
      window.history.replaceState({}, document.title, "");
      this.onSearch();
      this.updateImageDetails();
    }
  }

  formatDate(toBeFormatted): string {
    return this.date.format(toBeFormatted);
  }

  viewNode(node) {
      this.slimMode = true;
      this.imageName = node.label;
      this.onSearch();
      this.updateImageDetails();  
  }

  expandAll(): void {
    this.graph.expandAll();
  }
}