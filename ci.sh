#!/bin/bash

skip_build=false
skip_demo=false
skip_tests=false
skip_sys_tests=false

if [ "$#" -gt 0 ]
then
	if [ "$1" == '--skip' ]
	then
		if [ "$#" -eq 1 ]
		then
			echo 'USAGE: ./ci.sh [--skip <task>...]'
			exit 1
		fi

		for task in ${@:2}
		do
			if [ "$task" == 'build' ]
			then
				skip_build=true
			elif [ "$task" == 'demo' ]
			then
				skip_demo=true
			elif [ "$task" == 'tests' ]
			then
				skip_tests=true
			elif [ "$task" == 'sys-tests' ]
			then
				skip_sys_tests=true
			else
				echo 'Unsupported task. Task must be one of: [build, demo, tests, sys-tests]'
				exit 1
			fi
		done
	else
		echo 'USAGE: ./ci.sh [--skip <task>...]'
		exit 1
	fi
fi

curr_dir="$(pwd)"

if [ "$skip_build" = false ]
then
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
fi

if [ "$skip_demo" = false ]
then
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
	out="$(./spin-singleuse 4 build/test/ '.*Test\\.class' build/src/ dependencies/*)"
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
fi

if [ "$skip_tests" = false ]
then
	########### Unit Tests ###########
	echo -e "\nRunning Spin unit tests..."
	cd "$curr_dir"
        out="$(ant test-core)"
        if [ $? -ne 0 ]
        then
                echo 'Testing Spin: FAILED'
                echo $out
                exit 1
        else
                echo 'Testing Spin: SUCCESS'
        fi

fi

if [ "$skip_sys_tests" = false ]
then
	########### System Tests ###########
	echo -e "\nRunning the System Tests (this may take a few minutes)..."
	cd $curr_dir/systests/
	source venv/bin/activate
	./systests.sh --run -c
fi
