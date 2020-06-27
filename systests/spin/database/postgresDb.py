import psycopg2


def create_database_connection(db_config):
    """
    Creates and returns a new database connection.
    :param db_config: the path to the database config file.
    :type db_config: str
    :return: the new database connection.
    """
    db_name = None
    user = None
    password = None
    host = None
    port = None

    config_file = open(db_config, 'r')
    lines = config_file.readlines()
    for line in lines:
        stripped_line = line.strip()
        if stripped_line.startswith('database='):
            db_name = stripped_line[9:]
        elif stripped_line.startswith('user='):
            user = stripped_line[5:]
        elif stripped_line.startswith('password='):
            password = stripped_line[9:]
        elif stripped_line.startswith('host='):
            host = stripped_line[5:]
        elif stripped_line.startswith('port='):
            port = stripped_line[5:]

    return psycopg2.connect("dbname={} user={} password={} host={} port={}".format(db_name, user, password, host, port))