<div id="top"></div>

<br />
<div align="center">
    <img  src="screenshot.png?raw=true">

  <h1 align="center">c14r.io</h1>
  <p align="center">
    A tool that allows you to depict image structures. At present, it can index from DockerHub and MCR.
    <br />
    <br />
    <a href="https://c14r.io">Try it now</a>
    ·
    Explore the deploy scripts (coming soon)
  
  [![Microservices Buildpipeline](https://github.com/aymenfurter/c14r.io/actions/workflows/build.yml/badge.svg)](https://github.com/aymenfurter/c14r.io/actions/workflows/build.yml) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=c14r.dexter&metric=alert_status)](https://sonarcloud.io/dashboard?id=c14r.dexter) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=c14r.dexter&metric=alert_status)](https://sonarcloud.io/dashboard?id=c14r.ridik) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=c14r.dexter&metric=alert_status)](https://sonarcloud.io/dashboard?id=c14r.jobbie) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=c14r.ui&metric=alert_status)](https://sonarcloud.io/dashboard?id=c14r.ui)
  </p>

</div>
<br />

## Building the microservices locally
The following commands can be used to build and run the end2end tests locally.

`$ cd e2e && sh e2e.sh`


## Architecture
C14R follows microservices architecture. There are seperate microservices for indexing (Dexter), crawling (Jobbie) and reading the data (Ridik). The server side code is written in java based on Apache Camel and Spring Boot. The UI part is based on Angular & SB Admin.


![Architecture](infra.png?raw=true)

## Limitations
- Building the hierarchy is based on the instructions, not the image hashes. This may result in wrongly identified base images.
- Multiple Architectures (e.g. amd64, arm) are not yet properly reflected.
