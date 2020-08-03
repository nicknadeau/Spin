#!/bin/bash

curr_dir=$(pwd)

cd .. && \
ant build-example-raw && \
ant build-core && \
cd $curr_dir
mkdir example && \
cp ../client/spin-singleuse example/ && \
cp ../systests/spin/json_parser.py example/ && \
cp ../Core/dist/spin-core.jar example/ && \
cp ../Core/lib/postgresql-42.2.12.jar example/ && \
cp ../Core/lib/gson-2.8.6.jar example/ && \
cp ../lib/junit-4.12.jar example/ && \
cp -r ../Example/build/ example/ && \
mkdir example/config && \
cp ../config/db_config.txt example/config/ && \
mkdir example/lib && \
cp ../Example/lib/* example/lib/

cd $curr_dir
