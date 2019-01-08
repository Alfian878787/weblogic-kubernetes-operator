#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export WLS_BASE_IMAGE=store/oracle/weblogic:19.1.0.0
export PRJ_ROOT=../../

function checkPV() {
  if [ -z "$PV_ROOT" ] || [ ! -e "$PV_ROOT" ]; then
    echo "PV_ROOT is not set correctly. It needs to point to an existing folder. Currently PV_ROOT is '$PV_ROOT'."
    exit 1
  fi
}
function createPV() {
  checkPV

  if [ ! -e $PV_ROOT/logs ]; then
    mkdir -p $PV_ROOT/logs
    mkdir -p $PV_ROOT/shared
    chmod -R 777 $PV_ROOT/*
  fi

  sed -i 's@%PATH%@'"$PV_ROOT"/logs'@' domain2/pv.yaml
  sed -i 's@%PATH%@'"$PV_ROOT"/shared'@' domain3/pv.yaml
}

function createDomain1() {
  echo "create domain1"
  # create image 'domain1-image' with domainHome in the image
  ./domainHomeBuilder/build.sh domain1 weblogic welcome1

  kubectl -n default create secret generic domain1-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome1

  kubectl create -f domain1/domain1.yaml
}

function createDomain2() {
  echo "create domain2"
  # create image 'domain2-image' with domainHome in the image
  ./domainHomeBuilder/build.sh domain2 weblogic welcome2

  kubectl -n test1 create secret generic domain2-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome2

  kubectl create -f domain2/pv.yaml
  kubectl create -f domain2/pvc.yaml
  kubectl create -f domain2/domain2.yaml
}

function createDomain3() {
  echo "create domain3"
  # generate the domain3 configuration to a host folder
  ./domainHomeBuilder/generate.sh domain3 weblogic welcome3

  kubectl -n test1 create secret generic domain3-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome3

  kubectl create -f domain3/pv.yaml
  kubectl create -f domain3/pvc.yaml
  kubectl create -f domain3/domain3.yaml
}

function createAll() {
  createDomain1
  createDomain2
  createDomain3

}

function delDomain1() {
  kubectl delete -f domain1/domain1.yaml
  kubectl delete secret domain1-weblogic-credentials
}

function delDomain2() {
  kubectl delete -f domain2/domain2.yaml
  kubectl delete -f domain2/pvc.yaml
  kubectl delete -f domain2/pv.yaml
  kubectl -n test1 delete secret domain2-weblogic-credentials
}

function delDomain3() {
  kubectl delete -f domain3/domain3.yaml
  kubectl delete -f domain3/pvc.yaml
  kubectl delete -f domain3/pv.yaml
  kubectl -n test1 delete secret domain3-weblogic-credentials
}

function delAll() {
  delDomain1
  delDomain2
  delDomain3
}

# Usage: waitUntilReady namespace domainName
function waitUntilReady() {
  local namespace=$1
  local domainName=$2
  echo "wait until domain $domainName is ready"

  # get server number
  serverNum="$(kubectl -n $namespace get domain $domainName -o=jsonpath='{.spec.replicas}')"
  serverNum=$(expr $serverNum + 1)
  ready=false
  while test $ready != true; do
    if test "$(kubectl -n $namespace get pods  -l weblogic.domainUID=${domainName},weblogic.createdByOperator=true \
        -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' | grep true | wc -l)" != $serverNum; then
      kubectl -n $namespace get pods -l weblogic.domainUID=${domainName},weblogic.createdByOperator=true
      sleep 5
      continue
    fi
    ready=true
  done
}

# Usage: waitUntilStopped namespace domainName
function waitUntilStopped() {
  local namespace=$1
  local domainName=$2
  echo "wait until domain $domainName stopped"
  while : ; do
    if test "$(kubectl -n $namespace get all -l weblogic.domainUID=${domainName},weblogic.createdByOperator=true \
        | wc -l)" != 0; then
      echo "wait domain shutdown"
      kubectl -n $namespace get pods -l weblogic.domainUID=${domainName},weblogic.createdByOperator=true
      sleep 5
      continue
    fi
    break
  done
}

function waitUntilAllReady() {
  waitUntilReady default domain1
  waitUntilReady test1 domain2
  waitUntilReady test1 domain3
}

function waitUntilAllStopped() {
  waitUntilStopped default domain1
  waitUntilStopped test1 domain2
  waitUntilStopped test1 domain3
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "  PV cmd: createPV"
  echo "  This is to create PV folders"
  echo
  echo "  domains cmd: createAll | delAll"
  echo "  These are to create or delete all the sample domains."
  echo
  echo "  one domain cmd: createDomain1 | createDomain2 | createDomain3 | delDomain1 | delDomain2 | delDomain3"
  echo "  These are to create or delete one individual domain."
  echo
  echo "  waitUntilAll cmd: waitUntilAllReady | waitUntilAllStopped"
  echo "  These are to wait until all sample domains are ready or stopped."
  echo
  echo "  waitUntil cmd: waitUntilReady <namespace> <domainName> | waitUntilStopped <namespace> <domainName>"
  echo "  These are to wait until one sample domain is ready or stopped."
  echo
  exit 1
}

function main() {
  if [ "$#" == 0 ] ; then
    usage
  fi
  checkPV
  $1
}

main $@
