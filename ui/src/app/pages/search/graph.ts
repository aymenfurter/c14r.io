import { Injectable } from "@angular/core";
import { formatDate } from '@angular/common';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { resetElements } from "vis-network/declarations/DOMutil";
import { Toaster } from './toaster';
import { ElementRef } from "@angular/core";
import { NetworkFactory } from "./network";
import { SearchComponent } from "./search.component";

@Injectable({
  providedIn: 'root'
})
export class Graph {
  private networkInstance: any;
  private nodes: DataSet<any>;
  private edges: DataSet<any>;
  private foundNodes = new Map();
  private expandedNodes = new Map();
  private data;
  private searchNode;
  parent: SearchComponent;
  
  constructor(private toaster: Toaster, private networkFactory: NetworkFactory) {}

  handleNoResult(componentScope) {
    if (this.parent.delay != 4) {
      this.parent.refreshSearch(componentScope);
    } else {
      this.toaster.showToast(this.toaster.status, "Indexing failed", this.parent.imageName + " seems to not exist or we are overloaded right now, please try again later.");
    }
    if (this.parent.requested.get(this.parent.imageName) == null) {
      this.parent.indexImage(this.parent.imageName);
    }
  }

  initNodes() {
    this.edges = new DataSet<any>([]);
    this.nodes = new DataSet<any>([]);
    this.foundNodes = new Map();
  }

  highlightNode(nodes, searchNode) {
    searchNode.color = "#3366ff";
    nodes.update(searchNode);
  }

  createSearchNode(data) {
    try {
      this.searchNode = {
        id: data.searchImage.id,
        label: data.searchImage.name,
        color: "#00d68f"
      };
    } catch (e: unknown) { }
  }

  addFoundNode(data) {
    try {
      this.foundNodes.set(data.relatedImages.id, {
        id: data.relatedImages.id,
        label: data.relatedImages.name,
        group: data.relatedImages.name.substring(0, data.relatedImages.name.lastIndexOf(":"))
      });
    } catch (e: unknown) { }
  }

  linkNodes(data) {
    var edge = {
      id: data.childOf.start + "-" + data.childOf.end,
      from: data.childOf.start,
      to: data.childOf.end,
    };

    if (this.edges.get(edge.id) == null) {
      this.edges.add({
        id: data.childOf.start + "-" + data.childOf.end,
        from: data.childOf.start,
        to: data.childOf.end,
      });
    }
  }

  processResult(data) {
    this.parent.exampleMode = false;
    if (this.nodes == null) {
      this.initNodes();
    }

    if (this.searchNode != null) {
      this.highlightNode(this.nodes, this.searchNode);
    }

    for (const d of (data as any)) {
      this.createSearchNode(d);
      this.addFoundNode(d);
      if (d.childOf != null) {
        this.linkNodes(d);
      }
    }

    if (this.nodes.get(this.searchNode.id) == null) {
      this.nodes.add(this.searchNode);
    } else {
      this.nodes.update(this.searchNode);
    }

    for (let [key, value] of this.foundNodes) {
      var currentNode = this.nodes.get(value.id);
      if (currentNode == null) {
        this.nodes.add(value);
      } else if (currentNode != value) {
        this.nodes.update(value);
      }
    }

    this.data = { nodes: this.nodes, edges: this.edges };
    const container = this.parent.visNetwork;
    if (this.networkInstance == null) {
      this.createNetwork(container);
    }
  }

  createNetwork(container) {
      this.networkInstance = this.networkFactory.createNetwork(container.nativeElement, this.data);
      this.data.nodes.update(this.searchNode);
      this.parent.updateImageDetails();
      this.networkInstance.on('click', this.onNodeClick.bind(this))
      this.networkInstance.moveNode(this.searchNode.id, 0, 1)
  }

  expandAll(): void {
    if (this.foundNodes.size == 0) {
      this.toaster.showToast(this.toaster.status, "Please Notice", "You have to start a search first.");
    } else if (this.foundNodes.size <= 100) {

      for (let [key, value] of this.foundNodes) {
        if (value != null && this.expandedNodes.get(key) == null) {
          this.expandedNodes.set(key, true);
          this.parent.imageName = value.label;
          this.parent.onSearch();
        }
      }
      this.parent.slimMode = true;
    } else {
      this.toaster.showToast(this.toaster.status, "Too many nodes on screen", "Executing this operation likely would crash your browser, sorry.");
    }
  }

  reset() {
    this.edges = new DataSet<any>([]);
    this.nodes = new DataSet<any>([]);
    this.foundNodes = new Map();
    this.expandedNodes = new Map();
    this.networkInstance = null;
    this.searchNode = null;
  }

  onNodeClick(properties): void {
    var node = this.foundNodes.get(properties.nodes + "");
    if (node == null) {
      node = this.nodes.get(properties.nodes[0]);
    }
    if (node != null && node.label != null) {
      this.parent.viewNode(node);
    }
  }
}