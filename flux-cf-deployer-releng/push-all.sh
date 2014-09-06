#!/bin/bash
set -e

basedir=`pwd`/..
echo "==== Running maven build=====================" 
mvn clean install

echo "==== deploying: flux-cf-deployer-service ===="
cd ${basedir}/flux-cf-deployer-service
cf p -p target/org.eclipse.flux.cloudfoundry.deployment.service-*-jar-with-dependencies.jar

echo "==== deploying: flux-cf-deployer ============"
cd ${basedir}/flux-cf-deployer
cf p -p target/flux-cf-deployer-*.jar

echo "==== DONE ===================================="

