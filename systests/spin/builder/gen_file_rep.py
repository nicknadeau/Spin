import ast

from spin.behaviour import behaviour_helper


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


def is_package_desc(description):
    """
    Returns True iff this description is a gen file package description.

    :param description: the description to check.
    :return: whether or not it is a package description.
    :type description: str
    :return:type: bool
    """
    return description.startswith(get_start_of_package_desc())


def is_test_desc(description):
    """
        Returns True iff this description is a gen file test description.

        :param description: the description to check.
        :return: whether or not it is a test description.
        :type description: str
        :return:type: bool
        """
    return description.startswith(get_start_of_test_desc())


def get_class_index(test_description):
    """
    Returns the index of the class in the given gen file test description.

    :param test_description: the test description.
    :return: the class index described.
    :type test_description: str
    :return:type int
    """
    return int(__split_test_desc(test_description)[0][1:])


def get_test_index(test_description):
    """
    Returns the index of the test in the given gen file test description.

    :param test_description: the test description.
    :return: the test index described.
    :type test_description: str
    :return:type: int
    """
    return int(__split_test_desc(test_description)[1][1:])


def get_package_name(package_description):
    """
    Returns the package name in the given gen file test description.

    :param package_description: the package description.
    :return: the package name described.
    :type package_description: str
    :return:type: str
    """
    return package_description[2:]


def get_class_name(test_description):
    """
    Returns the name of the class in the given gen file test description.

    :param test_description: the test description.
    :return: the class name.
    :type test_description: str
    :return:type: str
    """
    return __split_test_desc(test_description)[0]


def get_test_name(test_description):
    """
        Returns the name of the test in the given gen file test description.

        :param test_description: the test description.
        :return: the test name.
        :type test_description: str
        :return:type: str
        """
    return __split_test_desc(test_description)[1]


def get_test_behaviour(test_description):
    """
    Returns the behaviour to be applied to the test in the given gen file test description.

    :param test_description: the test description.
    :return: the behaviour.
    :type test_description: str
    :return:type: Behaviour
    """
    split_desc = __split_test_desc(test_description)
    behaviour_codes = ast.literal_eval(split_desc[2])
    return behaviour_helper.create_behaviour_from_codes(behaviour_codes, split_desc[0], split_desc[1])


def __split_test_desc(test_description):
    """
    Returns a list of the 3 components of the given gen file test description.

    The component at index 0 is the class name, the component at index 1 is the test name, and the component at index 2
    is the list of behaviours to be applied to the test (in order).

    :param test_description: the test description.
    :return: the components of the test description.
    :type test_description: str
    :return:type: list[str]
    """
    split_desc = test_description.split("|")
    split_desc[0] = split_desc[0][2:]
    return split_desc

