def construct_test_description(class_index, test_index, behaviours):
    """
    Returns a line describing a test to be written to the gen file.

    :param class_index: the index of the test class in the package.
    :param test_index: the index of the test in the class.
    :param behaviours: the test behaviours.
    :return: the description
    :type class_index: int
    :type test_index: int
    :type behaviours: list[int]
    :return:type: str
    """
    return "{}C{}|t{}|{}".format(get_start_of_test_desc(), class_index, test_index, behaviours)


def construct_package_description(package_name):
    """
    Returns a line describing a test package to be written to the gen file.

    :param package_name: the package name.
    :return: the description.
    :type package_name: str
    :return:type: str
    """
    return "{}{}".format(get_start_of_package_desc(), package_name)


def get_gen_file_name():
    """
    Returns the name of the gen file.

    :return: the gen file name.
    :return:type: str
    """
    return "gen_file"


def get_start_of_test_desc():
    """
    Returns the start or prefix of a gen file test description.

    :return: the start of the test description.
    :return:type: str
    """
    return "t="


def get_start_of_package_desc():
    """
    Returns the start or prefix of a gen file package description.

    :return: the start of the package description.
    :return:type: str
    """
    return "p="


def get_class_index(test_description):
    """
    Returns the index of the class in the given gen file test description.

    :param test_description: the test description.
    :return: the class index described.
    :type test_description: str
    :return:type int
    """
    return int(test_description[3:4])


def get_test_index(test_description):
    """
    Returns the index of the test in the given gen file test description.

    :param test_description: the test description.
    :return: the test index described.
    :type test_description: str
    :return:type: int
    """
    return int(test_description[6:7])


def get_package_name(package_description):
    """
    Returns the package name in the given gen file test description.

    :param package_description: the package description.
    :return: the package name described.
    :type package_description: str
    :return:type: str
    """
    return package_description[2:]
