#!/bin/bash

BASE_DIR=$(pwd)

# point this to wherever you have maven installed.
MAVEN_BIN=/opt/apache-maven/bin/mvn

cd ..

$MAVEN_BIN clean package

cd $BASE_DIR

