#!/bin/bash

curr_dir=$(pwd)

cd .. && \
ant build-example-raw && \
ant build-singleuse && \
cd $curr_dir
mkdir example && \
cp ../client/spin-singleuse example/ && \
cp ../Core/dist/spin-singleuse.jar example/ && \
cp ../Core/lib/postgresql-42.2.12.jar example/ && \
cp ../lib/junit-4.12.jar example/ && \
cp -r ../Example/build/ example/ && \
mkdir example/lib && \
cp ../Example/lib/* example/lib/

cd $curr_dir
