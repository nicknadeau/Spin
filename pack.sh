#!/bin/bash

ant build-example-raw && \
ant build-singleuse && \
mkdir pack && \
cp spin pack/ && \
cp Core/dist/spin-singleuse.jar pack/ && \
cp lib/junit-4.12.jar pack/ && \
cp -r Example/build/ pack/ && \
mkdir pack/lib && \
cp Example/lib/* pack/lib/
