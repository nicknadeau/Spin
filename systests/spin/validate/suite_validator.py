import os

from spin.behaviour import behaviour_verifier
from spin.builder import gen_file_rep
from spin.database import postgresDb


def validate_suite(gen_file_path, db_config_path, suite_id):
    """
    Verifies that the suite with the given id is correct. That is, this method verifies that all of the data in the
    database for the suite, including all of its class data and all of the test results data in the database, are all
    as expected.

    This function uses the given gen file to determine the expected behaviours of all the tests and classes it is
    verifying. This function will instantiate a postgresQL database using the information found in the given database
    config file.

    This function raises an AssertionError if anything violates the expected state.

    :param gen_file_path: The path of the gen file.
    :param db_config_path: The path of the database config file.
    :param suite_id: The id of the suite to verify.
    :return: None
    """
    db_connection = postgresDb.create_database_connection(db_config_path)
    try:
        __validate_suite_internal(gen_file_path, db_connection, suite_id)
    finally:
        db_connection.close()


def __validate_suite_internal(gen_file_path, db_connection, suite_id):
    """
    Verifies that the suite with the given id is correct. That is, this method verifies that all of the data in the
    database for the suite, including all of its class data and all of the test results data in the database, are all
    as expected.

    This function uses the given gen file to determine the expected behaviours of all the tests and classes it is
    verifying.

    This function raises an AssertionError if anything violates the expected state.

    :param gen_file_path: The path of the gen file.
    :param db_connection: The SQL database connection.
    :param suite_id: The id of the suite to verify.
    :return: None
    """
    if not os.path.exists(gen_file_path):
        raise FileNotFoundError("No gen file found in this location: {}".format(gen_file_path))

    package_name = None
    curr_class_name = None
    class_to_verify = {}

    with open(gen_file_path) as gen_file:
        for line in gen_file:
            stripped_line = line.strip()

            # Reconstruct the fully-qualified class names, test names and test behaviours for each test listed in the
            # gen file.
            if gen_file_rep.is_package_desc(stripped_line):
                package_name = gen_file_rep.get_package_name(stripped_line)
            elif gen_file_rep.is_test_desc(stripped_line):
                class_name = gen_file_rep.get_class_name(stripped_line)
                test_name = gen_file_rep.get_test_name(stripped_line)
                test_behaviour = gen_file_rep.get_test_behaviour(stripped_line)

                # If this is our first pass we set the package name, class name and this test's information.
                if curr_class_name is None:
                    curr_class_name = class_name
                    class_to_verify["package_name"] = package_name
                    class_to_verify["class_name"] = class_name
                    class_to_verify["tests"] = {test_name: test_behaviour}

                if curr_class_name == class_name:
                    class_to_verify["tests"][test_name] = test_behaviour
                else:
                    # Then we have moved on to the next class, time to validate the current class first.
                    __validate_class(db_connection, suite_id, class_to_verify)

                    # Now clear the class to verify and load this first entry in.
                    curr_class_name = class_name
                    class_to_verify.clear()
                    class_to_verify["package_name"] = package_name
                    class_to_verify["class_name"] = curr_class_name
                    class_to_verify["tests"] = {test_name: test_behaviour}

    # We still have to validate the last class we looked at.
    __validate_class(db_connection, suite_id, class_to_verify)

    # Finally, we validate that the data in the suite is derivative of the data in all its classes.
    __validate_suite(db_connection, suite_id)


def __validate_class(db_connection, suite_id, class_to_verify):
    """
    Verifies that the class whose verification information is captured in the given class_to_verify object has the
    correct data. Raises an AssertionError if not.

    :param db_connection: The database connection.
    :param suite_id: The suite id.
    :param class_to_verify: The class to verify information.
    :return: None
    """
    cursor = db_connection.cursor()
    try:
        fully_qualified_class_name = "{}.{}".format(class_to_verify["package_name"], class_to_verify["class_name"])

        class_query = "SELECT id, name, num_tests, num_success, num_failures, duration FROM test_class " \
                      + "WHERE name='{}' AND suite={}".format(fully_qualified_class_name, suite_id)
        cursor.execute(class_query)
        if cursor.rowcount != 1:
            raise AssertionError("Expected to find exactly 1 class entry but found {}".format(cursor.rowcount))
        class_db_entry = cursor.fetchone()

        test_query = "SELECT name, is_success, stdout, stderr, duration FROM test WHERE class={}".format(class_db_entry[0])
        cursor.execute(test_query)
        test_db_entries = cursor.fetchall()

        behaviour_verifier.validate_class(class_db_entry, test_db_entries, class_to_verify)

    finally:
        cursor.close()


def __validate_suite(db_connection, suite_id):
    """
    Verifies that the suite with the given id in the database has the correct data. Raises an AssertionError if not.

    :param db_connection: The database connection.
    :param suite_id: The suite id.
    :return: None
    """
    cursor = db_connection.cursor()
    try:
        cursor.execute("SELECT num_tests, num_success, num_failures, duration FROM test_suite WHERE id={}".format(suite_id))
        if cursor.rowcount != 1:
            raise AssertionError("Expected to find exactly 1 suite entry but found {}".format(cursor.rowcount))
        suite_db_entry = cursor.fetchone()

        # Verify that the suite reports the correct number of tests.
        cursor.execute("SELECT SUM(num_tests) FROM test_class WHERE suite={}".format(suite_id))
        num_classes = int(cursor.fetchone()[0])
        if suite_db_entry[0] != num_classes:
            raise AssertionError("Expected {} tests in suite {} but found {}".format(num_classes, suite_id, suite_db_entry[0]))

        # Verify that the suite reports the correct number of test successes & failures.
        cursor.execute("SELECT SUM(num_success) FROM test_class WHERE suite={}".format(suite_id))
        num_success = int(cursor.fetchone()[0])
        if suite_db_entry[1] != num_success:
            raise AssertionError("Expected {} successes in suite {} but found {}".format(num_success, suite_id, suite_db_entry[1]))

        cursor.execute("SELECT SUM(num_failures) FROM test_class WHERE suite={}".format(suite_id))
        num_fails = int(cursor.fetchone()[0])
        if suite_db_entry[2] != num_fails:
            raise AssertionError("Expected {} failures in suite {} but found {}".format(num_fails, suite_id, suite_db_entry[2]))

        # Finally, verify that the suite duration is the sum of all its tests' durations.
        cursor.execute("SELECT SUM(duration) FROM test_class WHERE suite={}".format(suite_id))
        duration_total = int(cursor.fetchone()[0])
        if suite_db_entry[3] != duration_total:
            raise AssertionError("Expected suite {} duration to be {} but found {}".format(suite_id, duration_total, suite_db_entry[3]))

    finally:
        cursor.close()
