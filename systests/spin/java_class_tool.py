import sys

from spin.behaviour import Behaviour
from spin.generate import suite_generator


def usage():
    """Prints a message to the console detailing the proper usage of this program."""
    print 'python java_class_tool.py [action]'
    print "\naction: --generate"
    print "\t--generate <num packages> <num classes> <num tests> <test type>"
    print "\t\tnum packages:\tthe number of unique Java packages to distribute the test classes among."
    print "\t\tnum classes:\tthe number of unique Java test classes to create."
    print "\t\tnum tests:\tthe number of tests to add to each Java test class."
    print "\t\ttest type:\tthe type of test each of the tests should be (their behaviour)."
    print "\n\tSupported test types: 'empty'"


if __name__ == '__main__':
    action = sys.argv[1]
    if action == '--generate':
        if len(sys.argv) != 6:
            print usage()
            quit(1)

        num_packages = int(sys.argv[2])
        num_classes = int(sys.argv[3])
        num_tests = int(sys.argv[4])
        test_type = sys.argv[5]

        if num_packages < 0 or num_classes < 0 or num_tests < 0:
            raise ValueError("num_packages, num_classes & num_tests must all be non-negative.")
        if num_classes < num_packages:
            raise ValueError("Num_packages must be less than or equal to num_classes.")

        suite_generator.create_java_test_suite(num_packages, num_classes, num_tests, Behaviour(test_type))

    elif action == '--evaluate':
        print 'Currently unsupported.'
    else:
        print usage()
        quit(1)
