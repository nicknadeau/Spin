from spin.behaviour.behaviour import Behaviour


def create_behaviour_from_codes(behaviour_codes, class_name, test_name):
    """
    Creates a single behaviour that describes the list of behaviours given (by code) for the test of the given name,
    which is defined in the class with the given name.

    This function can be used to chain multiple behaviours together into a single one that will be applied to the test.

    :param behaviour_codes: the int codes representing the behaviours to encapsulate.
    :param class_name: the name of the class the test is defined in.
    :param test_name: the name of the test.
    :return: the behaviour.
    :type behaviour_codes: list[int]
    :type class_name: str
    :type test_name: str
    :return:type: Behaviour
    """
    if len(behaviour_codes) == 1:
        return Behaviour(behaviour_codes[0], test_name, class_name)
    elif len(behaviour_codes) > 1:
        behaviour = None
        for code in behaviour_codes:
            curr_behaviour = Behaviour(code, test_name, class_name)
            behaviour = curr_behaviour if behaviour is None else behaviour.merge(curr_behaviour)
        return behaviour
    else:
        return None
