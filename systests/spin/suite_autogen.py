import sys
import ast
from spin.builder import gen_file_builder


def usage():
    """
    Prints a message to the console detailing the proper usage of this program.

    :return: None
    """
    print("\npython suite_autogen.py <action>")
    print("\naction:")
    print("--gen_file <request>")
    print("\tCreates or appends to a gen file. A gen file describes the contents of a test suite. Both the tools for")
    print("\tgenerating and evaluating test suites rely on the description found in the gen file to do their jobs.")
    print("\n\trequest:")
    print("\t[-new | -n] <file dir> <package name> <num classes> <num tests> <behaviours>")
    print("\t\tCreates a new gen file for a suite comprised of the given number of classes, each of which has the")
    print("\t\tgiven package name and given number of tests, and each test has the given behaviours.")
    print("\n\t\tfile dir: the path of the directory the new gen file will be created in.")
    print("\t\tpackage name: the dot-style name of the Java package for each of the classes.")
    print("\t\tnum classes: the number of Java test classes defined in the package.")
    print("\t\tnum tests: the number of Java test methods defined in each Java test class in the package.")
    print("\t\tbehaviours: a list of the Java test behaviours defined for each Java test in every class.")
    print("\n\t[-append_classes | -ac ] <file path> <package name> <num classes> <num tests> <behaviours>")
    print("\t\tAppends the given number of classes, each with the given package name, to the existent gen file.")
    print("\t\tEach of these classes has the given number of tests, each test has the given behaviours.")
    print("\n\t\tfile path: the file path of the existent gen file.")
    print("\t\tpackage name: the dot-style name of the Java package for each of the classes to be appended.")
    print("\t\tnum classes: the number of additional Java test classes defined in the package.")
    print("\t\tnum tests: the number of Java test methods defined in each appended Java test class.")
    print("\t\tbehaviours: a list of the Java test behaviours defined for each Java test to be appended.")
    print("\n\t[-append_tests | -at] <file path> <package name> <class index> <num tests> <behaviours>")
    print("\t\tAppends the given number of tests, each with the given behaviours, to the class at the given index")
    print("\t\tinside the specified package.")
    print("\n\t\tfile path: the file path of the existent gen file.")
    print("\t\tpackage name: the dot-style name of the Java package to append tests to.")
    print("\t\tclass index: the index of the Java test class defined in the package to append tests to.")
    print("\t\tnum tests: the number of Java test methods to append to the specified Java test class.")
    print("\t\tbehaviours: a list of the Java test behaviours defined for each Java test to be appended.")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("No action defined")
        print(usage())
        quit(1)

    action = sys.argv[1]
    if action == '--gen_file':
        if len(sys.argv) < 3:
            print("No request defined for action: {}".format(action))
            print(usage())
            quit(1)

        request = sys.argv[2]
        if request == '-new' or request == '-n':
            if len(sys.argv) != 8:
                print("Incorrect number of args given ({}) for request {}".format(len(sys.argv) - 3, request))
                print(usage())
                quit(1)
            gen_file_builder.new_gen_file(sys.argv[3], sys.argv[4], int(sys.argv[5]), int(sys.argv[6]), ast.literal_eval(sys.argv[7]))

        elif request == '-append_classes' or request == '-ac':
            if len(sys.argv) != 8:
                print("Incorrect number of args given ({}) for request {}".format(len(sys.argv) - 3, request))
                print(usage())
                quit(1)
            gen_file_builder.append_classes(sys.argv[3], sys.argv[4], int(sys.argv[5]), int(sys.argv[6]), sys.argv[7])

        elif request == '-append_tests' or request == '-at':
            if len(sys.argv) != 8:
                print("Incorrect number of args given ({}) for request {}".format(len(sys.argv) - 3, request))
                print(usage())
                quit(1)
            gen_file_builder.append_tests(sys.argv[3], sys.argv[4], int(sys.argv[5]), int(sys.argv[6]), ast.literal_eval(sys.argv[7]))

        else:
            print("Unrecognized request ({}) for action: {}".format(request, sys.argv[1]))
            print(usage())
            quit(1)

    else:
        print("Unrecognized action: {}".format(sys.argv[1]))
        print(usage())
        quit(1)
