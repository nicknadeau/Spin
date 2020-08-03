#!/bin/bash

curr_dir="$(pwd)"

########### Build ###########
echo 'Building Spin...'
out="$(ant build-core)"
if [ $? -ne 0 ]
then
	echo 'Building Spin: FAILED'
	echo $out
	exit 1
else
	echo 'Building Spin: SUCCESS'
fi

########### Demo ###########
echo -e "\nRunning the Spin demo..."
cd demo/
out="$(./example-singleuse.sh)"
if [ $? -ne 0 ]
then
	echo 'Running Spin demo: FAILED'
	echo $out
	exit 1
fi
cd example/
out="$(./spin-singleuse 4 build/test/ '.*Test\\.class' build/src/ lib/*)"
code=$?

cd ..
rm -rf example/

if [ $code -ne 0 ]
then
	echo 'Running Spin demo: FAILED'
	echo $out
	exit 1
else
	echo 'Running Spin demo: SUCCESS'
fi

########### System Tests ###########
echo -e "\nRunning the System Tests (this may take a few minutes)..."
cd $curr_dir/systests/
source venv/bin/activate
./systests.sh --run -c
