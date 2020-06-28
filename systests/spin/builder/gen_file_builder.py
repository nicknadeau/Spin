import os.path

from spin.builder import gen_file_rep


def new_gen_file(file_dir, package_name, num_classes, num_tests, behaviours):
    """
    Creates a new gen file with a single package with the given name, which contains the specified number of classes,
    each of which contains the specified number of tests, each of which contains the specified behaviours.

    :param file_dir: the gen file's parent directory path.
    :param package_name: the Java package name all classes in the gen file will belong to.
    :param num_classes: the number of Java classes to define in the gen file.
    :param num_tests: the number of Java tests per class to define in the gen file.
    :param behaviours: the list of behaviours to define per test in the gen file.
    :type file_dir: str
    :type package_name: str
    :type num_classes: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    if os.path.exists(file_dir + "/" + gen_file_rep.get_gen_file_name()):
        raise FileExistsError("A gen file in this directory already exists: {}".format(file_dir))

    gen_file = open(file_dir + "/" + gen_file_rep.get_gen_file_name(), "w+")
    try:
        __write_new_class_bundle(gen_file, False, package_name, 0, num_classes, num_tests, behaviours)
        gen_file.write("\n")
    finally:
        gen_file.close()


def append_classes(file_path, package_name, num_classes, num_tests, behaviours):
    """
    Appends to an existent gen file.

    If the given package does not exist then this function appends the package to the gen file with the specified number
    of classes in the package, with the specified number of tests in each class, each with the specified behaviours.

    If the given package already exists then this function appends to that same package the specified number of
    additional classes, each of which has the specified number of tests, each of which has the specified behaviours.

    :param file_path:
    :param package_name:
    :param num_classes:
    :param num_tests:
    :param behaviours:
    :return:
    """
    if not os.path.exists(file_path):
        raise FileNotFoundError("No gen file found in this location: {}".format(file_path))

    last_slash_index = file_path.rfind("/")
    if last_slash_index == -1:
        tmp_gen_path = ".tmp_gen"
    else:
        tmp_gen_path = file_path[:last_slash_index] + "/.tmp_gen"

    if os.path.exists(tmp_gen_path):
        os.remove(tmp_gen_path)

    __append_classes(file_path, tmp_gen_path, package_name, num_classes, num_tests, behaviours)


def append_tests(file_path, package_name, class_index, num_tests, behaviours):
    """
    Appends to an existent gen file.

    If the given package name does not exist this function fails.

    Otherwise, that package must have N > 0 classes defined in it. This function targets the class at the specified
    index in the package. If no such index exists this function fails.

    Otherwise if the index exists, this function appends the specified number of tests to that class, each of which has
    the specified behaviours.

    :param file_path: the path to the gen file.
    :param package_name: the package name to append to.
    :param class_index: the index of the class to append to.
    :param num_tests: the number of tests to append to the class.
    :param behaviours: the behaviours defined per each appended test.
    :type package_name: str
    :type class_index: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    if not os.path.exists(file_path):
        raise FileNotFoundError("No gen file found in this location: {}".format(file_path))

    last_slash_index = file_path.rfind("/")
    if last_slash_index == -1:
        tmp_gen_path = ".tmp_gen"
    else:
        tmp_gen_path = file_path[:last_slash_index] + "/.tmp_gen"

    if os.path.exists(tmp_gen_path):
        os.remove(tmp_gen_path)

    __append_tests(file_path, tmp_gen_path, package_name, class_index, num_tests, behaviours)


def __append_classes(gen_file_path, tmp_gen_path, package_name, num_classes, num_tests, behaviours):
    """
    Appends the specified number of classes, each with the specified number of tests and behaviours defined, to the
    gen file given that these classes are all under the specified package name.

    :param gen_file_path: The existent gen file to append to.
    :param tmp_gen_path: The temporary gen file we make the actual appends to and then use to overwrite the gen file.
    :param package_name: the package name of the classes to append.
    :param num_classes: the number of classes to append.
    :param num_tests: the number of tests per new classes to append.
    :param behaviours: the behaviours defined per appended tests.
    :type package_name: str
    :type num_classes: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    tmp_gen_file = open(tmp_gen_path, "w+")
    try:
        have_written = False
        found_package = False
        appended = False
        max_class_index = 0

        with open(gen_file_path) as gen_file:
            for line in gen_file:
                stripped_line = line.strip()

                if stripped_line.startswith(gen_file_rep.get_start_of_package_desc()):
                    defined_package = gen_file_rep.get_package_name(stripped_line)
                    if defined_package == package_name:
                        # Then the package we want to append already exists, now we wait until we reach the end of it.
                        found_package = True
                    elif found_package and not appended:
                        # We found our package and this isn't it so we must be at the next package. Before writing that
                        # next package we append to the current one now.
                        __write_bundle(tmp_gen_file, max_class_index + 1, max_class_index + 1 + num_classes, num_tests, behaviours)
                        appended = True
                elif found_package and not appended and stripped_line.startswith(gen_file_rep.get_start_of_test_desc()):
                    # We found our package and haven't appended yet so we need to keep tracking the max class index
                    # so that we can append starting at the next available index.
                    class_index = gen_file_rep.get_class_index(stripped_line)
                    max_class_index = max(max_class_index, class_index)

                # Write this line into the temporary file (if first time do not begin with newline.
                tmp_gen_file.write("{}{}".format("\n" if have_written else "", stripped_line))
                have_written = True

        # If we found no matching package then we append all these new classes & tests to the end of the file.
        # If we found a matching package and did not append yet, then we do the append now.
        # Else, there is nothing to do.
        if not found_package:
            __write_new_class_bundle(tmp_gen_file, True, package_name, 0, num_classes, num_tests, behaviours)
        elif found_package and not appended:
            __write_bundle(tmp_gen_file, max_class_index + 1, max_class_index + 1 + num_classes, num_tests, behaviours)

        # Delete the old gen file and replace it with the temporary one we've been writing to.
        tmp_gen_file.write("\n")
        os.remove(gen_file_path)
        os.rename(tmp_gen_path, gen_file_path)
    finally:
        tmp_gen_file.close()


def __append_tests(gen_file_path, tmp_gen_path, package_name, class_index, num_tests, behaviours):
    """
    Appends the specified number of tests, each with the specified behaviours, to the gen file given that these tests
    are all under the specified class index which in turn is under the specified package name.

    :param gen_file_path: the gen file.
    :param tmp_gen_path: The temporary gen file we make the actual appends to and then use to overwrite the gen file.
    :param package_name: the package name of the class we will append to.
    :param class_index: the index of the class we will append to.
    :param num_tests: the number of tests to append to the class.
    :param behaviours: the behaviours to append per test.
    :type package_name: str
    :type class_index: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    tmp_gen_file = open(tmp_gen_path, "w+")
    try:
        have_written = False
        inside_package = False
        found_class = False
        appended = False
        max_test_index = 0

        with open(gen_file_path) as gen_file:
            for line in gen_file:
                stripped_line = line.strip()

                if stripped_line.startswith(gen_file_rep.get_start_of_package_desc()):
                    defined_package = gen_file_rep.get_package_name(stripped_line)
                    if defined_package == package_name:
                        inside_package = True
                    elif inside_package and found_class and not appended:
                        # We were just inside our package and we found our class to append to. The fact we are no longer
                        # inside the package means it was the last entry in that package, so we append now.
                        __write_tests(tmp_gen_file, class_index, max_test_index + 1, max_test_index + 1 + num_tests, behaviours)
                        appended = True
                        inside_package = False
                    else:
                        inside_package = False
                elif inside_package and stripped_line.startswith(gen_file_rep.get_start_of_test_desc()):
                    curr_class_index = gen_file_rep.get_class_index(stripped_line)
                    if curr_class_index == class_index:
                        # This is the class we want to append to, we must track the maximum test index so we know the
                        # correct value to begin appending at.
                        found_class = True
                        test_index = gen_file_rep.get_test_index(stripped_line)
                        max_test_index = max(max_test_index, test_index)
                    elif not appended and curr_class_index == class_index + 1:
                        # We have not appended yet and we are at the class index above the one we want to append to so
                        # this must be the correct place for us to append before moving on to the next class.
                        __write_tests(tmp_gen_file, class_index, max_test_index + 1, max_test_index + 1 + num_tests, behaviours)
                        appended = True

                # Write this line into the temporary file (if first time do not begin with newline.
                tmp_gen_file.write("{}{}".format("\n" if have_written else "", stripped_line))
                have_written = True

        if found_class and not appended:
            # If we found our class but have not appended then it must have been the last class in the file, so we
            # append now.
            __write_tests(tmp_gen_file, class_index, max_test_index + 1, max_test_index + 1 + num_tests, behaviours)
            appended = True

        if not appended:
            raise AssertionError("Failed to find class index {} in package {} in gen file.".format(class_index, package_name))

        # Delete the old gen file and replace it with the temporary one we've been writing to.
        tmp_gen_file.write("\n")
        os.remove(gen_file_path)
        os.rename(tmp_gen_path, gen_file_path)
    finally:
        tmp_gen_file.close()


def __write_new_class_bundle(gen_file, use_newline, package_name, start_class_index, end_class_index, num_tests, behaviours):
    """
    Write a new class & test bundle to the gen file.

    :param gen_file: the gen file.
    :param use_newline: whether or not to begin this new bundle with a newline.
    :param package_name: the package name of the bundle.
    :param start_class_index: the starting index of the classes to write, inclusive.
    :param end_class_index: the ending index of the classes to write, exclusive.
    :param num_tests: the number of tests to write per class.
    :param behaviours: the behaviours to write per test.
    :type use_newline: bool
    :type package_name: str
    :type start_class_index: int
    :type end_class_index: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    gen_file.write("{}{}".format("\n" if use_newline else "", gen_file_rep.construct_package_description(package_name)))
    for i in range(start_class_index, end_class_index):
        if num_tests == 0:
            gen_file.write("\n{}".format(gen_file_rep.construct_test_description(i, -1, None)))
        elif num_tests > 0:
            for j in range(num_tests):
                gen_file.write("\n{}".format(gen_file_rep.construct_test_description(i, j, behaviours)))
        else:
            raise AssertionError("Cannot construct gen file with class with negative num tests.")


def __write_bundle(gen_file, start_class_index, end_class_index, num_tests, behaviours):
    """
    Write a class & test bundle to the gen file. Assumption is that we are at the appropriate line in the file to write
    and that the package name has already been defined for us (this is used during appends).

    :param gen_file: the gen file.
    :param start_class_index: the starting index of the classes to write, inclusive.
    :param end_class_index: the ending index of the classes to write, exclusive.
    :param num_tests: the number of tests to write per class.
    :param behaviours: the behaviours to write per test.
    :type start_class_index: int
    :type end_class_index: int
    :type num_tests: int
    :type behaviours: list[int]
    :return: None
    """
    for i in range(start_class_index, end_class_index):
        if num_tests == 0:
            gen_file.write("\n{}".format(gen_file_rep.construct_test_description(i, -1, None)))
        elif num_tests > 0:
            for j in range(num_tests):
                gen_file.write("\n{}".format(gen_file_rep.construct_test_description(i, j, behaviours)))
        else:
            raise AssertionError("Cannot construct gen file with class with negative num tests.")


def __write_tests(gen_file, class_index, start_test_index, end_test_index, behaviours):
    """
    Write only the given class to the gen file with tests whose indices are over the specified range with the specified
    behaviours. Assumption is that we are at the appropriate line in the file to write and that the package name has
    already been defined for us (this is used during appends)

    :param gen_file: the gen file.
    :param class_index: the class index to write.
    :param start_test_index: the starting index of the tests to write, inclusive.
    :param end_test_index: the ending index of the tests to write, exclusive.
    :param behaviours: the behaviours to write per test.
    :type class_index: int
    :type start_test_index: int
    :type end_test_index: int
    :type behaviours: list[int]
    :return: None
    """
    if end_test_index - start_test_index == 0:
        gen_file.write("\n{}".format(gen_file_rep.construct_test_description(class_index, -1, None)))
    elif end_test_index - start_test_index > 0:
        for i in range(start_test_index, end_test_index):
            gen_file.write("\n{}".format(gen_file_rep.construct_test_description(class_index, i, behaviours)))
    else:
        raise AssertionError("Cannot construct gen file: test end index {} precedes start index {}.".format(end_test_index, start_test_index))
