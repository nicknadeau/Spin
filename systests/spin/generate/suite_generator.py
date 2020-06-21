import os

from spin.behaviour import source_object_helper
from spin.builder import gen_file_rep


def generate_suite(gen_file_path, project_name):
    """
    Generates a project from the gen file whose path is the given one and outputs the source files of this project into
    a directory with the same name as the given project name.

    Inside this project-named directory will be a directory named test, in which all of the generated Java test files
    can be found (in their appropriate packages). If required, a directory named src may also be present in this
    project-named directory, containing all Java source files required by the generated tests.

    :param gen_file_path: the path to the gen file which describes the suite to be generated.
    :param project_name: the name of this newly generated project.
    :return: None
    :type gen_file_path: str
    :type project_name: str
    """

    # Ensure the gen file exists and create the directory with the given project name.
    if not os.path.exists(gen_file_path):
        raise FileNotFoundError("No gen file found in this location: {}".format(gen_file_path))
    if os.path.exists(project_name):
        raise IsADirectoryError("A directory with the project name ({}) already exists".format(project_name))
    os.mkdir(project_name)

    # Make a first pass through the gen file to collect all of the class header information so we can easily write.
    header_info = __read_class_header_info(gen_file_path)
    __write_source_objects(project_name, header_info)

    package_name = None
    curr_class_name = None
    java_file = None

    with open(gen_file_path) as gen_file:
        for line in gen_file:
            stripped_line = line.strip()
            if gen_file_rep.is_package_desc(stripped_line):
                # Set our current package name and create all of the directories required by the package name.
                package_name = gen_file_rep.get_package_name(stripped_line)
                __create_test_package_directories(project_name, package_name)
            elif gen_file_rep.is_test_desc(stripped_line):
                class_name = gen_file_rep.get_class_name(stripped_line)
                test_name = gen_file_rep.get_test_name(stripped_line)
                behaviour = gen_file_rep.get_test_behaviour(stripped_line)

                if curr_class_name is None:
                    # This is our first ever read, we open a new Java file and write its header and this first test.
                    curr_class_name = class_name
                    java_file = __open_java_test_file(project_name, package_name, curr_class_name)
                    __write_header(java_file, package_name, class_name, header_info)
                    __write_test(java_file, test_name, behaviour)

                elif curr_class_name == class_name:
                    # We are still in the middle of writing tests to the current Java class.
                    __write_test(java_file, test_name, behaviour)
                else:
                    # We must be finished up with our previous Java class so write the footer and close the file.
                    __write_footer(java_file)
                    java_file.close()

                    # Open a new file for the next Java class, write the header and this first test.
                    curr_class_name = class_name
                    java_file = __open_java_test_file(project_name, package_name, curr_class_name)
                    __write_header(java_file, package_name, class_name, header_info)
                    __write_test(java_file, test_name, behaviour)

    # Before exiting we still have to write the footer of our currently opened Java class and close the file.
    __write_footer(java_file)
    java_file.close()


def __read_class_header_info(gen_file_path):
    """
    Reads the gen file whose path is the given one and collects all header information for every class defined in the
    gen file.

    This header information is returned as a dictionary whose key is the fully-qualified class name (dot-style) of the
    class and whose value is a dictionary with the following keys that map to the actual information:
    'imports', 'fields'.

    :param gen_file_path: the path of the gen file.
    :return: the header information dictionary.
    :type gen_file_path: str
    """
    header_info = {}

    curr_package = None
    with open(gen_file_path) as gen_file:
        for line in gen_file:
            stripped_line = line.strip()
            if gen_file_rep.is_package_desc(stripped_line):
                curr_package = gen_file_rep.get_package_name(stripped_line)
            elif gen_file_rep.is_test_desc(stripped_line):
                class_name = gen_file_rep.get_class_name(stripped_line)
                behaviour = gen_file_rep.get_test_behaviour(stripped_line)
                fully_qualified_class_name = "{}.{}".format(curr_package, class_name)

                if fully_qualified_class_name not in header_info:
                    header_info[fully_qualified_class_name] = {
                        "imports": behaviour.imports,
                        "fields": behaviour.fields,
                        "source-objects": behaviour.source_objects
                    }
                else:
                    class_header_info = header_info[fully_qualified_class_name]
                    class_header_info["imports"] = class_header_info["imports"] | behaviour.imports
                    class_header_info["fields"] = class_header_info["fields"] | behaviour.fields
                    class_header_info["source-objects"] = class_header_info["source-objects"] | behaviour.source_objects
    return header_info


def __write_source_objects(project_name, header_info):
    """
    Writes all of the Java src classes for the project as defined in the given header information.

    :param project_name: the name of this project.
    :param header_info: the header information for all classes defined in the gen file.
    :return: None
    """
    all_source_objects = set()
    for class_name in header_info:
        all_source_objects = all_source_objects | header_info[class_name]["source-objects"]
    for source_object in all_source_objects:
        __write_source_object(project_name, source_object)


def __write_source_object(project_name, source_object_name):
    """
    Writes a new .java file in the src directory for a class whose fully-qualified name is the given source object name.

    :param project_name: the name of this project.
    :param source_object_name: the fully-qualified name of the Java src class to write.
    :return: None
    :type project_name: str
    :type source_object_name: str
    """
    package_name = source_object_helper.get_package_name(source_object_name)
    class_name = source_object_helper.get_class_name(source_object_name)

    __create_src_package_directories(project_name, package_name)
    java_file = __open_java_src_file(project_name, package_name, class_name)
    try:
        source_object_helper.write_source_object_to_file(java_file, source_object_name)
    finally:
        java_file.close()


def __open_java_test_file(project_name, package_name, class_name):
    """
    Returns a newly opened file for a .java Java test file. This will will be empty and ready to be written to.

    :param project_name: the project name.
    :param package_name: the package name of the class defined by this Java file.
    :param class_name: the class defined by this Java file.
    :return: None
    :type project_name: str
    :type package_name: str
    :type class_name: str
    """
    path = "{}/{}/{}.java".format(__new_project_test_dir_path(project_name), package_name.replace(".", "/"), class_name)
    return open(path, "w+")


def __open_java_src_file(project_name, package_name, class_name):
    """
    Returns a newly opened file for a .java Java source file. This will will be empty and ready to be written to.

    :param project_name: the project name.
    :param package_name: the package name of the class defined by this Java file.
    :param class_name: the class defined by this Java file.
    :return: None
    :type project_name: str
    :type package_name: str
    :type class_name: str
    """
    path = "{}/{}/{}.java".format(__new_project_source_dir_path(project_name), package_name.replace(".", "/"), class_name)
    return open(path, "w+")


def __write_header(java_file, package_name, class_name, header_info):
    """
    Writes the header of the Java class to the given Java source file.

    :param java_file: the .java Java source file to write to.
    :param package_name: the package name of the given class.
    :param class_name: the class name.
    :param header_info: all of the header information for any class to be written to disk.
    :return: None
    :type package_name: str
    :type class_name: str
    """
    fully_qualified_class_name = "{}.{}".format(package_name, class_name)

    java_file.write("package {};\n".format(package_name))
    java_file.write("\nimport org.junit.Test;")
    class_header_info = header_info[fully_qualified_class_name]
    for java_import in class_header_info["imports"]:
        java_file.write("\nimport {}".format(java_import))
    java_file.write("\n\npublic class {} {{".format(class_name))
    for java_fields in class_header_info["fields"]:
        java_file.write("\n\t{}".format(java_fields))


def __write_test(java_file, test_name, behaviour):
    """
    Writes the test whose name is the specified name and whose behaviour is defined by the given behaviour object to
    the given Java source file.

    :param java_file: the .java Java source file to write to.
    :param test_name: the name of the Java unit test.
    :param behaviour: the behaviour of the test.
    :return: None
    :type test_name: str
    :type behaviour: Behaviour
    """
    java_file.write("\n\n\t@Test")
    java_file.write("\n\tpublic void {}() {}{{".format(test_name, "throws Exception " if behaviour.throws else ""))
    for test_content in behaviour.content:
        java_file.write("\n\t\t{}".format(test_content))
    java_file.write("\n\t}")


def __write_footer(java_file):
    """
    Writes the footer of the Java class to the given Java source file.

    :param java_file: the .java Java source file to write to.
    :return: None
    """
    java_file.write("\n}\n")


def __create_test_package_directories(project_name, package_name):
    """
    Creates all of the directories on disk required by the specified Java package name given the project name.
    This function is used to create packages belonging to classes in the test directory.

    :param project_name: the name of this project.
    :param package_name: the package name to create directories for.
    :return: None
    :type project_name: str
    :type package_name: str
    """
    os.makedirs("{}/{}".format(__new_project_test_dir_path(project_name), package_name.replace(".", "/")))


def __create_src_package_directories(project_name, package_name):
    """
    Creates all of the directories on disk required by the specified Java package name given the project name.
    This function is used to create packages belonging to classes in the src directory.

    :param project_name: the name of this project.
    :param package_name: the package name to create directories for.
    :return: None
    :type project_name: str
    :type package_name: str
    """
    os.makedirs("{}/{}".format(__new_project_source_dir_path(project_name), package_name.replace(".", "/")))


def __new_project_test_dir_path(project_name):
    """
    Returns the path of the test directory for the specified project, as a relative path starting at the current
    working directory.

    :param project_name: the project name.
    :return: the project test directory.
    :type project_name: str
    :return:type: str
    """
    return "{}/test".format(project_name)


def __new_project_source_dir_path(project_name):
    """
    Returns the path of the src directory for the specified project, as a relative path starting at the current
    working directory.

    :param project_name: the project name.
    :return: the project src directory.
    :type project_name: str
    :return:type: str
    """
    return "{}/src".format(project_name)
