import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';


@Injectable({
  providedIn: 'root'
})
export class ApiService {

ingest(search: string) {
  if (search == null) return;
  var repo = search.substr(0, search.indexOf('/')); 
  var image = search.substring(
    search.lastIndexOf(":"), 
    search.indexOf("/")+1
  );

  var tag = search.substr(search.indexOf(":")+1, search.length); 

  let request = {}
  request["imageName"] = image;
  request["imageTag"] = tag;
  request["repositoryName"] = repo;

  return this.http.post(environment.ingestUrl, JSON.stringify(request), {observe: "response"});
}

getNetwork(search: string) {
  return this.http.get(environment.searchUrl + search);
}

getAutocomplete(search: string) {
  return this.http.get(environment.autocompleteUrl + search);
}

getDetail(search: string) {
  return this.http.get(environment.detailUrl + search);
}
constructor(private http: HttpClient) { }
}
