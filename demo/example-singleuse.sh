#!/bin/bash

# All paths here are relative with respect to the project root directory.
example_dir='example'
spin_dependencies="$example_dir/dependencies"
example_config="$example_dir/config"
example_compiled="$example_dir/build"
singleuse_client='client/spin-singleuse'
json_parser='systests/spin/json_parser.py'
spin_core_jar='Core/dist/spin-core.jar'
spin_core_dependencies='Core/lib'
global_dependencies='lib'
example_compiled_classes='Example/build'
db_configs='config/db_config.txt'

curr_dir="$(pwd)"

cd .. && \
ant build-example-raw && \
ant build-core && \
cd $curr_dir

if [ -d "$example_dir" ]
then
	rm -rf "$example_dir"
fi

mkdir "$example_dir" && \
mkdir "$example_compiled" && \
cp ../"$singleuse_client" "$example_dir" && \
cp ../"$json_parser" "$example_dir" && \
cp ../"$spin_core_jar" "$example_dir" && \
mkdir "$spin_dependencies" && \
cp ../"$spin_core_dependencies"/* "$spin_dependencies" && \
cp ../"$global_dependencies"/* "$spin_dependencies" && \
cp -r ../"$example_compiled_classes"/* "$example_compiled" && \
mkdir "$example_config" && \
cp ../"$db_configs" "$example_config" && \

cd $curr_dir
