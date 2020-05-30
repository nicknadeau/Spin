#!/bin/bash

ant build-example-raw && \
ant build-standalone && \
mkdir pack && \
cp spin pack/ && \
cp Client/dist/spin-standalone.jar pack/ && \
cp lib/junit-4.12.jar pack/ && \
cp -r Example/build/ pack/ && \
mkdir pack/lib && \
cp Example/lib/* pack/lib/
