import { Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { ApiService } from './../../api.service';
import { Infopanel } from './infopanel.component';
import { Searchbar } from './searchbar.component';
import { Variants } from './variants.component';
import { DockerHub } from './dockerhub';
import { DateFormat } from './date';
import { Graph } from './graph';
import { Toaster } from './toaster';
import { NetworkFactory } from './network';

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
  private detailData;
  api: ApiService;
  exampleMode = true;
  slimMode = true;

  hashes: string[] = [];
  imageTree: any[] = [];
  detailDataTags = [];
  detailDataTagsFull = [];
  currentHash;
  currentTag;
  updateInProgress: boolean = false;
  imageName: string;
  delay: any = 1;

  constructor(private graph: Graph, private networkFactory: NetworkFactory, private toaster: Toaster, private date: DateFormat, private hub: DockerHub, api: ApiService) { this.api = api; graph.parent = this }

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
    __proto__: Object
    this.api.getNetwork(this.imageName)
      .subscribe(data => {
        var result = <Array<any>>data;
        if (result.length == 0) {
          this.graph.handleNoResult(this);
        } else {
          this.graph.processResult(data);
        }
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
    comp.imageTree.push([{ "url": this.hub.getDockerHubURL(value.repositoryName, value.imageName, value.imageTag), "last_pushed": value.last_pushed, "instructions": value.instructions, "image": value.repositoryName + "/" + value.imageName + ":" + value.imageTag, "isRoot": isRoot }]);
    if (value.parent != null) {
      this.processImage(value.parent, comp, false);
    }
  }

  buildTree(): void {
    var result = <Array<any>>this.detailData;
    var componentScope = this;
    componentScope.imageTree = [];
    result.forEach(function (value) {
      if (value.last_pushed == componentScope.currentHash) {
        componentScope.processImage(value, componentScope, true);
        componentScope.imageTree.reverse();
      }
    });
  }

  updateImageDetails(): void {
    this.updateInProgress = true;
    this.hashes = [];
    this.api.getDetail(this.imageName).subscribe(data => {
      var result = <Array<any>>data["variants"];
      var alternativeTags = <Array<any>>data["tags"];
      this.detailDataTags = alternativeTags;
      this.detailDataTagsFull = [];

      if (this.detailDataTags.length > 10 && this.slimMode) {
        this.detailDataTagsFull = alternativeTags;
        this.detailDataTags = alternativeTags.slice(0, 10);
      }
      this.detailData = result;
      var comp = this;
      this.currentTag = this.imageName.substr(this.imageName.indexOf(':') + 1, this.imageName.length);

      if (result.length >= 1) this.currentHash = result[0].last_pushed;
      var tempMap = new Map();
      this.hashes = [];
      result.forEach(function (value) {
        if (tempMap.get(value.last_pushed) == null) {
          comp.hashes.push(value.last_pushed);
          tempMap.set(value.last_pushed, true);
        }
      });
      this.buildTree();

      this.updateInProgress = false;
    })
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