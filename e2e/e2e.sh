#!/bin/bash
cd ../dexter/
mvn spring-boot:build-image
cd ../ridik/
mvn spring-boot:build-image
cd ../jobbie
mvn spring-boot:build-image
cd ../e2e
mvn package
