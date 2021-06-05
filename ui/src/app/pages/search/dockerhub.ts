import { Injectable } from "@angular/core";

@Injectable({
  providedIn: 'root'
})
export class DockerHub {
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
}