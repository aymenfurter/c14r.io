import { Component, ElementRef, ViewChild, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';
import { ApiService } from './../../api.service';
import {
  NbComponentStatus,
  NbGlobalPhysicalPosition,
  NbGlobalPosition,
  NbToastrService,
  NbToastrConfig,
} from '@nebular/theme';
import { Infopanel } from './infopanel.component';
import { Searchbar } from './searchbar.component';
import { Variants } from './variants.component';
import { formatDate } from '@angular/common';

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
  private networkInstance: any;
  private nodes: DataSet<any>;
  private edges: DataSet<any>;
  api: ApiService;
  private foundNodes = new Map();
  private expandedNodes = new Map();
  private requested = new Map();
  exampleMode = true;
  slimMode = true;
  hashes: string[] =[];
  imageTree: any[] =[];
  private data;
  private detailData;
  detailDataTags = [];
  detailDataTagsFull = [];
  currentHash;
  currentTag;
  updateInProgress: boolean = false;
  imageName: string;
  private searchNode; 

  // Toaster
  config: NbToastrConfig;
  destroyByClick = true;
  duration = 2000;
  hasIcon = true;
  position: NbGlobalPosition = NbGlobalPhysicalPosition.TOP_RIGHT;
  preventDuplicates = false;
  status: NbComponentStatus = 'primary';
  private delay: any = 1;

  constructor(api: ApiService, private toastrService: NbToastrService) { this.api = api;}

  resetSearch() {
          this.edges = new DataSet<any>([]);
          this.nodes = new DataSet<any>([]);
          this.slimMode = true;
          this.foundNodes = new Map();
          this.expandedNodes = new Map();
          this.updateImageDetails();
          this.networkInstance = null;
          this.searchNode = null;
          this.delay = 1;
  }

  refreshSearch(comp) {

    if (comp.delay == 1) {
        this.showToast(this.status, "Not yet indexed", this.imageName + " is not in our index. We've added it now, please try again later. One moment please.");
    }

    comp.delay = comp.delay * 2;
    setTimeout(() => { comp.onSearch() }, comp.delay * 1000);

  }

  onSearch() {
    if (this.imageName == null) return;
    if (this.imageName != null && this.imageName.indexOf(":") == -1) this.imageName = this.imageName + ":latest";
    if (this.imageName != null && this.imageName.indexOf("/") == -1) this.imageName = "library/" + this.imageName;

    __proto__: Object
    this.api.getNetwork(this.imageName)
    .subscribe(data => {
      var result = <Array<any>>data;
      
      if (result.length == 0) {        
        if (this.delay != 4) {
            this.refreshSearch(this)
        } else {
          this.showToast(this.status, "Indexing failed", this.imageName + " seems to not exist or we are overloaded right now, please try again later.");
        }
        if (this.requested.get(this.imageName) == null) {
          this.requested.set(this.imageName, "indexed");
          this.api.ingest(this.imageName)        
          .subscribe(resp => {
            if (resp.status != 202)   {
              this.showToast(this.status, "Not yet indexed", this.imageName + " is not in our index. Looks like we are overloaded right now, please try again later.");
            } 
              
          });
        }
        } else {
        
        this.exampleMode = false;
        if (this.nodes == null) {
          this.edges = new DataSet<any>([]);
          this.nodes = new DataSet<any>([]);
          this.foundNodes = new Map();
        }

      
        if (this.searchNode != null) {
            this.searchNode.color = "#3366ff";
            this.nodes.update(this.searchNode);        
        }

        for (const d of (data as any)) {
          // TODO: Implement a smarter way to fill the array
          try {             
            this.searchNode = {
              id: d.searchImage.id,
              label: d.searchImage.name,
              color: "#00d68f"
            };
          } catch (e: unknown) { }
          try {          
            this.foundNodes.set(d.relatedImages.id,{
              id: d.relatedImages.id,
              label: d.relatedImages.name,
              group: d.relatedImages.name.substring(0, d.relatedImages.name.lastIndexOf(":"))
            });
          } catch (e: unknown) {          
          }
          if (d.childOf != null) {
            var edge = {
              id: d.childOf.start + "-" + d.childOf.end,
              from: d.childOf.start,
              to: d.childOf.end,
            };

            if (this.edges.get(edge.id) == null) {
              this.edges.add({
                id: d.childOf.start + "-" + d.childOf.end,
                from: d.childOf.start,
                to: d.childOf.end,
              });
            }
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

        const container = this.visNetwork;
        if (this.networkInstance == null) {
          this.networkInstance = new Network(container.nativeElement, this.data, {
            height: '100%',
            width: '100%',
            physics: {
              enabled: true,
              stabilization: false,
              solver: "repulsion",
              repulsion: {
                nodeDistance: 600
              }
            },
            interaction: {
              dragView: true
            },
            nodes: {
              shape: 'square',
              color: '#3366ff',
              size: 10,
              font: {
                color: 'white',
              },
            },
            edges: {
              smooth: false,
              arrows: {
                to: {
                  enabled: true,
                  type: 'vee',
                },
              },
            },
          });
       
          this.data.nodes.update(this.searchNode);
          this.updateImageDetails();
          this.networkInstance.on('click', this.onNodeClick.bind(this))
          this.networkInstance.moveNode(this.searchNode.id,0,1)
        }
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

  expandAll(): void {
    if (this.foundNodes.size == 0) {
      this.showToast(this.status, "Please Notice", "You have to start a search first.");
    } else if (this.foundNodes.size <= 100) {
    
    for (let [key, value] of this.foundNodes) {
      if (value != null && this.expandedNodes.get(key) == null) {
        this.expandedNodes.set(key, true);
        this.imageName = value.label;
        this.onSearch();        
      }
    }
    this.slimMode = true;
    } else {
      this.showToast(this.status, "Too many nodes on screen", "Executing this operation likely would crash your browser, sorry.");
    }
  }

  onNodeClick(properties): void {
    var node = this.foundNodes.get(properties.nodes + "");
    if (node == null) {
      node = this.nodes.get(properties.nodes[0]); // could replace logic 2 lines above
    }
    if (node != null && node.label != null) {
      this.slimMode = true;
      this.imageName = node.label;
      this.onSearch();
      this.updateImageDetails();  
    }
  }


  viewHash(hash) {
    this.currentHash = hash;
    this.buildTree();
  }

  toggleSlimMode() {
    this.detailDataTags = this.detailDataTagsFull;
    this.slimMode = false;
  }

  viewTag(tag) {
      this.imageName = this.imageName.substr(0, this.imageName.indexOf(':')) + ":" + tag;       
      this.onSearch();
      this.updateImageDetails();  
  }

  getDockerHubURL(reponame, image, tag) {

    if (reponame.indexOf(".") != -1) {
      return "https://"+reponame+"/"+image+":" + tag;
    } else {
      var repo = reponame; 

      if (repo == "library") {
        repo = "_";
    } else {
      repo = "r/" + repo;
    } 
      return "https://hub.docker.com/"+repo+"/"+image+"?tab=tags&page=1&ordering=last_updated&name=" + tag;
    }
  }

  processImage(value, comp, isRoot) {

    comp.imageTree.push([{"url": this.getDockerHubURL(value.repositoryName, value.imageName, value.imageTag), "last_pushed": value.last_pushed, "instructions": value.instructions, "image": value.repositoryName + "/" + value.imageName + ":" + value.imageTag, "isRoot": isRoot }]);
    if (value.parent != null) {
      this.processImage(value.parent, comp, false);
    }
  }

  buildTree(): void {
      var result = <Array<any>>this.detailData;
      var comp = this;
      comp.imageTree = [];
      result.forEach(function (value) {
        if (value.last_pushed == comp.currentHash) {
          comp.processImage(value, comp, true);
          comp.imageTree.reverse();
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
  
      this.currentTag = this.imageName.substr(this.imageName.indexOf(':')+1, this.imageName.length); 
      

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
      this.imageName = decodeURIComponent(queryString).replace("?","").replace("=","");
      window.history.replaceState({}, document.title, "");      
      this.onSearch();
      this.updateImageDetails();        
    }
  }

  private showToast(type: NbComponentStatus, title: string, body: string) {
    const config = {
      status: type,
      destroyByClick: this.destroyByClick,
      duration: this.duration,
      hasIcon: this.hasIcon,
      position: this.position,
      preventDuplicates: this.preventDuplicates,
    };
    const titleContent = title;
    this.toastrService.show(
      body,
      titleContent,
      config);
  }
    
  formatDate(toBeFormatted): string {
    const format = 'dd/MM/yyyy hh:mm';
    const locale = 'en-US';
    const formattedDate = formatDate(toBeFormatted, format, locale);
    return formattedDate;
  }
}