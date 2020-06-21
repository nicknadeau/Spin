def write_source_object_to_file(java_file, source_object_name):
    """
    Writes the source Java object whose fully-qualified name is the given object name to the given .java file.

    Assumption: the given java file is empty.

    :param java_file: the empty file to write the class definition to.
    :param source_object_name: the fully-qualified name of the class to write.
    :return: None
    """
    if source_object_name == 's.a.Ticker':
        __write_ticker_object(java_file)
    else:
        raise AssertionError("Unrecognized source object name: {}".format(source_object_name))


def get_package_name(fully_qualified_name):
    """
    Returns the package from the given fully-qualified name.

    :param fully_qualified_name: the fully-qualified name.
    :return: the package.
    """
    last_dot_index = fully_qualified_name.rfind(".")
    if last_dot_index == -1:
        return ""
    else:
        return fully_qualified_name[:last_dot_index]


def get_class_name(fully_qualified_name):
    """
    Returns the class name (ie. simple class name) from the given fully-qualified name.

    :param fully_qualified_name: the fully-qualified name.
    :return: the simple class name.
    """
    last_dot_index = fully_qualified_name.rfind(".")
    return fully_qualified_name[last_dot_index + 1:]


def __write_ticker_object(java_file):
    """
    Writes to the given Java file, which is assumed empty, the source code that defines the 's.a.Ticker' object.

    :param java_file: the .java Java source code file for the s.a.Ticker object.
    :return: None
    """
    java_file.write("package s.a;")
    java_file.write("\n\npublic class Ticker {\n\tprivate int ticks = 0;")
    java_file.write("\n\n\tpublic void tick() {\n\t\tthis.ticks++;\n\t}")
    java_file.write("\n\n\tpublic int getTicks() {\n\t\treturn this.ticks;\n\t}\n}\n")
