import os
from spin.behaviour import Behaviour


def __create_java_package_names(num_packages):
    """
    Returns a list of the specified number of Java package names (dot-style), each of which is unique.

    :param num_packages: The number of packages to create.
    :type num_packages: int
    :return: the list of Java package names.
    """
    package_names = []
    for i in range(num_packages):
        package_names.append("autogen.test.package" + str(i + 1))
    return package_names


def __create_directories(class_package):
    """
    Takes a Java package name and creates the directory structure required for that package.

    :param class_package: The Java class package (dot-style).
    :type class_package: str
    :return: None
    """
    dir_path = class_package.replace('.', '/')
    if not os.path.exists(dir_path):
        os.makedirs(dir_path)


def __write_class_header(java_file, class_package, class_name, class_imports):
    """
    Writes the package, import, and class declarations that begin the Java test class file.

    :param java_file: The open Java test class file.
    :param class_package: The Java class package (dot-style).
    :param class_name: The name of the Java test class.
    :param class_imports: The import statements that this Java test class must make to compile correctly.
    :type java_file: BinaryIO
    :type class_package: str
    :type class_name: str
    :type class_imports: set[str]
    :return: None
    """
    java_file.write("package " + class_package + ";")
    for class_import in class_imports:
        java_file.write("\n\nimport " + class_import + ";")
    java_file.write("\n\npublic class " + class_name + " {")


def __write_class_body(java_file, num_tests, behaviour):
    """
    Writes the body of the Java test class file, which is the given number of tests each with the given behaviour.

    :param java_file: The open Java test class file.
    :param num_tests: The number of tests in the class.
    :param behaviour: The behaviour of each test in the class.
    :type java_file: BinaryIO
    :type num_tests: int
    :type behaviour: Behaviour
    :return: None
    """
    for i in range(num_tests):
        java_file.write("\n\n\t@Test")
        java_file.write("\n\tpublic void test" + str(i + 1) + "() {")
        java_file.write(behaviour.content)
        java_file.write("\n\t}")


def __write_class_footer(java_file):
    """
    Writes the final closing brace to end the Java test class file.

    :param java_file: The open Java test class file.
    :type java_file: BinaryIO
    :return: None
    """
    java_file.write("\n}\n")


def __create_java_class(class_package, class_name, num_tests, behaviour):
    """
    Creates a Java test class with the specified name and Java package, which contains the specified number of tests
    each of which has the specified behaviour.

    :param class_package: The Java class package (dot-style).
    :param class_name: The name of the Java test class.
    :param num_tests: The number of JUnit tests in the class.
    :param behaviour: The behaviour of each JUnit test.
    :type class_package: str
    :type class_name: str
    :type num_tests: int
    :type behaviour: Behaviour
    :return: None
    """
    path = "./" + class_package.replace('.', '/') + "/" + class_name + ".java"
    if os.path.exists(path):
        print "Cannot create java class (" + class_package + class_name + "): file already exists!"
        quit(1)

    java_file = open(path, "w+")
    __write_class_header(java_file, class_package, class_name, behaviour.imports)
    __write_class_body(java_file, num_tests, behaviour)
    __write_class_footer(java_file)
    java_file.close()


def __create_java_classes(class_package, num_classes, num_tests, behaviour):
    """
    Creates the specified number of Java test classes inside the given Java class package, each of which has the
    specified number of tests with the specified behaviour.

    :param class_package: The Java class package (dot-style).
    :param num_classes: The number of Java test classes to create.
    :param num_tests: The number of JUnit tests per each class.
    :param behaviour: The behaviour of each JUnit test.
    :type class_package: str
    :type num_classes: int
    :type num_tests: int
    :type behaviour: Behaviour
    :return: None
    """
    for i in range(num_classes):
        __create_java_class(class_package, "Class" + str(i + 1) + "Test", num_tests, behaviour)


def create_java_test_suite(num_packages, num_classes, num_tests, behaviour):
    """
    Creates a test suite consisting of the specified number of Java test classes distributed among the specified number
    of Java packages. Each class will contain the specified number of tests and each test will have the specified
    behaviour.

    :param num_packages: The number of unique Java packages to create.
    :param num_classes: The number of Java test classes to create.
    :param num_tests: The number of JUnit tests per class.
    :param behaviour: The behaviour of each JUnit test method.
    :type num_packages: int
    :type num_classes: int
    :type num_tests: int
    :type behaviour: Behaviour
    :return: None
    """
    package_names = __create_java_package_names(num_packages)
    num_classes_per_package = num_classes / num_packages
    remaining_classes = num_classes - (num_classes_per_package * num_packages)

    for i in range(len(package_names)):
        __create_directories(package_names[i])
        if i == len(package_names) - 1:
            __create_java_classes(package_names[i], num_classes_per_package + remaining_classes, num_tests, behaviour)
        else :
            __create_java_classes(package_names[i], num_classes_per_package, num_tests, behaviour)
