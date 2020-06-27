def validate_class(class_db_entry, test_db_entries, class_to_verify):
    """
    Validates the results of all the tests in the class and the class itself. If anything is wrong then this function
    raises an AssertionError.

    Expects the class_db_entry to be a single row of format: (id, name, num_tests, num_success, num_failures, duration)
    Expects the test_db_entries to be rows of format: (name, is_success, stdout, stderr, duration)

    :param class_db_entry: The single class database entry for the class.
    :param test_db_entries: The database entries for each test in the class.
    :param class_to_verify: The class to verify information for all its tests.
    :return: None
    """
    package_name = class_to_verify["package_name"]
    class_name = class_to_verify["class_name"]
    tests_to_verify = class_to_verify["tests"]

    num_successes = 0
    num_failures = 0
    duration_total = 0

    for test_db_entry in test_db_entries:
        num_class_invokes_by_test = 0
        num_test_invokes = 0
        num_ticks = 0
        min_sleep_duration_millis = 0
        failed = False

        test_name = test_db_entry[0]
        behaviour = tests_to_verify[test_name]
        duration_total += test_db_entry[4]

        for code in behaviour.codes:
            if code == 1:
                min_sleep_duration_millis += 100
            elif code == 2:
                is_success = int(test_db_entry[1])
                if is_success != 0:
                    raise AssertionError("Expected failure for behaviour code {}".format(code))
                failed = True
            elif code == 3:
                stdout = test_db_entry[2]
                out = "{}:{}".format(class_name, test_name)
                if out not in stdout:
                    raise AssertionError("Expected stdout '{}' for behaviour code {}".format(out, code))
            elif code == 4:
                stderr = test_db_entry[3]
                err = "{}:{}".format(class_name, test_name)
                if err not in stderr:
                    raise AssertionError("Expected stderr '{}' for behaviour code {}".format(err, code))
            elif code == 5:
                num_test_invokes += 1
            elif code == 6:
                num_class_invokes_by_test += 1
            elif code == 7:
                stdout = test_db_entry[2]
                out = "testInvokes={}".format(num_test_invokes)
                if out not in stdout:
                    raise AssertionError("Expected stdout '{}' for behaviour code {}".format(out, code))
            elif code == 8:
                stderr = test_db_entry[3]
                err = "testInvokes={}".format(num_test_invokes)
                if err not in stderr:
                    raise AssertionError("Expected stderr '{}' for behaviour code {}".format(err, code))
            elif code == 9:
                start_string = "classInvokes="
                end_string = "\n"
                stdout = test_db_entry[2]

                start_index = stdout.rfind(start_string)
                if start_index == -1:
                    raise AssertionError("Expected stdout '{}' for behaviour code {}".format(start_string, code))

                count_string = stdout[(start_index + len(start_string)):]
                end_index = count_string.find(end_string)
                count = int(count_string if end_index == -1 else count_string[:end_index])

                # Since tests run concurrently all we can say is count must be at least # of times this test invoked it.
                if count < num_class_invokes_by_test:
                    raise AssertionError("Expected class num invokes to be at least {} for behaviour {}".format(num_class_invokes_by_test, code))
            elif code == 10:
                start_string = "classInvokes="
                end_string = "\n"
                stderr = test_db_entry[3]

                start_index = stderr.rfind(start_string)
                if start_index == -1:
                    raise AssertionError("Expected stderr '{}' for behaviour code {}".format(start_string, code))

                count_string = stderr[(start_index + len(start_string)):]
                end_index = count_string.find(end_string)
                count = int(count_string if end_index == -1 else count_string[:end_index])

                # Since tests run concurrently all we can say is count must be at least # of times this test invoked it.
                if count < num_class_invokes_by_test:
                    raise AssertionError("Expected class num invokes to be at least {} for behaviour {}".format(num_class_invokes_by_test, code))
            elif code == 11:
                num_ticks += 1
            elif code == 12:
                start_string = "ticks="
                end_string = "\n"
                stdout = test_db_entry[2]

                start_index = stdout.rfind(start_string)
                if start_index == -1:
                    raise AssertionError("Expected stdout '{}' for behaviour code {}".format(start_string, code))

                count_string = stdout[(start_index + len(start_string)):]
                end_index = count_string.find(end_string)
                count = int(count_string if end_index == -1 else count_string[:end_index])
                if count != num_ticks:
                    raise AssertionError("Expected num ticks {} for behaviour {}".format(num_ticks, code))
            elif code == 13:
                start_string = "ticks="
                end_string = "\n"
                stderr = test_db_entry[3]

                start_index = stderr.rfind(start_string)
                if start_index == -1:
                    raise AssertionError("Expected stderr '{}' for behaviour code {}".format(start_string, code))

                count_string = stderr[(start_index + len(start_string)):]
                end_index = count_string.find(end_string)
                count = int(count_string if end_index == -1 else count_string[:end_index])
                if count != num_ticks:
                    raise AssertionError("Expected num ticks {} for behaviour {}".format(num_ticks, code))

        # Increment num success/failure depending on this test's outcome.
        if not failed:
            num_successes += 1
        else:
            num_failures += 1

        # Verify that the test's duration is at least the expected minimum.
        duration_millis = test_db_entry[4] / 1000000
        if duration_millis < min_sleep_duration_millis:
            raise AssertionError("Duration {} less than min {} for behaviour code {}".format(duration_millis, min_sleep_duration_millis, code))

    expected_num_tests = class_db_entry[2]
    expected_num_successes = class_db_entry[3]
    expected_num_failures = class_db_entry[4]
    expected_duration = class_db_entry[5]

    if expected_num_tests != len(test_db_entries):
        raise AssertionError("Expected {} tests in class {}.{} but found {}".format(expected_num_tests, package_name, class_name, len(test_db_entries)))
    if expected_num_successes != num_successes:
        raise AssertionError("Expected {} successes in class {}.{} but found {}".format(expected_num_successes, package_name, class_name, num_successes))
    if expected_num_failures != num_failures:
        raise AssertionError("Expected {} failures in class {}.{} but found {}".format(expected_num_failures, package_name, class_name, num_failures))
    if expected_duration != duration_total:
        raise AssertionError("Expected {} duration for class {}.{} but found {}".format(expected_duration, package_name, class_name, duration_total))
