import sys
from json import JSONDecoder


def usage():
    """
    Prints the usage description of this program to the console.
    """
    print("\nUSAGE: python json_parser.py <response type> <action> <response>")
    print("\nresponse type:")
    print("--run_suite")
    print("\tParses the response of a run-suite request.")
    print("\naction:")
    print("--is_successful")
    print("\tReturns True iff the given response is successful, otherwise False")
    print("--get_suite_id")
    print("\tReturns the suite id in the response.")
    print("--get_error")
    print("\tReturns the error message in the response.")
    print("\nresponse: the json response returned from Spin.")


def is_run_suite_successful(response):
    """
    Returns true iff the is_success property of the json response is true, otherwise returns false.

    :param response: The Spin server response.
    :return whether the response was successful.
    :type response: str
    """
    return str(JSONDecoder().decode(response)['is_success']).lower()


def get_suite_id_from_run_suite_response(response):
    """
    Returns the suite id returned in the run_suite response from Spin.

    :param response: The Spin server response.
    :return the suite id.
    :type response: str
    """
    return JSONDecoder().decode(response)['response']['suite_id']


def get_error_message(response):
    """
    Returns the error message returned in the response from Spin.

    :param response: The Spin server response.
    :return the error message.
    :type response: str
    """
    return JSONDecoder().decode(response)['error']


if __name__ == '__main__':
    if len(sys.argv) != 4:
        print("No action defined")
        print(usage())
        quit(1)

    response_type = sys.argv[1]
    if response_type == '--run_suite':
        action = sys.argv[2]
        if action == '--is_successful':
            print(is_run_suite_successful(sys.argv[3]))
        elif action == '--get_suite_id':
            print(get_suite_id_from_run_suite_response(sys.argv[3]))
        elif action == '--get_error':
            print(get_error_message(sys.argv[3]))
        else:
            print("Unrecognized action: {}".format(sys.argv[2]))
            usage()
            quit(1)
    else:
        print("Unrecognized response type: {}".format(sys.argv[1]))
        usage()
        quit(1)
