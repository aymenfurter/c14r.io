<kbd>![Architecture](screenshot.png?raw=true)</kdb>

# C14R
[C14R](https://c14r.io/) is a tool that let's you visualize image hierarchies. It currently supports indexing from DockerHub and MCR.


## Building the microservices locally
The following commands can be used to build and run the end2end tests locally.

`$ cd e2e && sh e2e.sh`


## Architecture
C14R follows microservices architecture. There are seperate microservices for indexing (Dexter), crawling (Jobbie) and reading the data (Ridik). The server side code is written in java based on Apache Camel and Spring Boot. The UI part is based on Angular & SB Admin.


![Architecture](infra.png?raw=true)

## Limitations
- Building the hierarchy is based on the instructions, not the image hashes. This may result in wrongly identified base images.
- Multiple Architectures (e.g. amd64, arm) are not yet properly reflected.
