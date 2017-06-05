#!/bin/bash

BASE_DIR=$(pwd)

# point this to wherever you have maven installed.
MAVEN_BIN=/opt/apache-maven/bin/mvn

cd ..

$MAVEN_BIN versions:set
$MAVEN_BIN -N versions:update-child-modules
$MAVEN_BIN -N versions:commit

cd $BASE_DIR
