import { Injectable } from "@angular/core";
import { formatDate } from '@angular/common';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { resetElements } from "vis-network/declarations/DOMutil";
import { Toaster } from './toaster';
import { ElementRef } from "@angular/core";
import { NetworkFactory } from "./network";
import { SearchComponent } from "./search.component";
import { DockerHub } from "./dockerhub";

@Injectable({
  providedIn: 'root'
})
export class DetailView {
  parent: SearchComponent;
  private detailData;

  constructor(private hub: DockerHub, private toaster: Toaster) { }

  processImage(value, comp, isRoot) {
    comp.imageTree.push([{ "url": this.hub.getDockerHubURL(value.repositoryName, value.imageName, value.imageTag), "last_pushed": value.last_pushed, "instructions": value.instructions, "image": value.repositoryName + "/" + value.imageName + ":" + value.imageTag, "isRoot": isRoot }]);
    if (value.parent != null) {
      this.processImage(value.parent, comp, false);
    }
  }

  buildTree(): void {
    var result = <Array<any>>this.detailData;
    var componentScope = this.parent;
    componentScope.imageTree = [];
    result.forEach(function (value) {
      if (value.last_pushed == componentScope.currentHash) {
        componentScope.processImage(value, componentScope, true);
        componentScope.imageTree.reverse();
      }
    });
  }

  updateImageDetails(): void {
    this.parent.updateInProgress = true;
    this.parent.hashes = [];
    this.parent.api.getDetail(this.parent.imageName).subscribe(data => {
      var result = <Array<any>>data["variants"];
      var alternativeTags = <Array<any>>data["tags"];
      this.parent.detailDataTags = alternativeTags;
      this.parent.detailDataTagsFull = [];

      if (this.parent.detailDataTags.length > 10 && this.parent.slimMode) {
        this.parent.detailDataTagsFull = alternativeTags;
        this.parent.detailDataTags = alternativeTags.slice(0, 10);
      }
      this.detailData = result;
      var comp = this.parent;
      this.parent.currentTag = this.parent.imageName.substr(this.parent.imageName.indexOf(':') + 1, this.parent.imageName.length);

      if (result.length >= 1) this.parent.currentHash = result[0].last_pushed;
      var tempMap = new Map();
      this.parent.hashes = [];
      result.forEach(function (value) {
        if (tempMap.get(value.last_pushed) == null) {
          comp.hashes.push(value.last_pushed);
          tempMap.set(value.last_pushed, true);
        }
      });
      this.buildTree();

      this.parent.updateInProgress = false;
    })
  }
}