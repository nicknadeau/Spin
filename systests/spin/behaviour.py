class Behaviour:
    """
    A Behaviour object that encapsulates Java test behaviour. In particular, this object is used to describe the kind of
    behaviour of a Java test in a high-level way.
    """

    def __init__(self, kind=None, kinds=None, content=None, imports=None):
        """
        Creates a new behaviour.

        There are 2 ways of using this constructor:
        1. Provide a non-None kind and ensure kinds, content & imports are all None
        2. Provide kind as None and ensure kinds, content & imports are all non-None

        All outside callers should always use the constructor as specified in the first way. This will produce a new
        behaviour whose kind is the specified kind. It will generate all of the other properties for you.

        The second manner is used internally.

        :param kind: The kind of behaviour captured by this behaviour object.
        :param kinds: The kinds of behaviours captured by this behaviour object.
        :param content: The content of the behaviour object.
        :param imports: The import statements required by the behaviour.
        :type kind: str
        :type kinds: list[str]
        :type content: str
        :type imports: set[str]
        """
        if kind is not None:
            if kinds is not None or content is not None or imports is not None:
                raise ValueError('kind is not None: expected kinds, content & imports to all be None but was not true.')
            self.kinds = [kind]
            if kind == "empty":
                self.content = ""
                self.imports = {"org.junit.Test"}
            else:
                raise ValueError("Unsupported behaviour kind: " + kind)
        else:
            if kinds is None or content is None or imports is None:
                raise ValueError('kind is None: expected kinds, content & imports to all be non-None but was not true.')
            self.kinds = kinds
            self.content = content
            self.imports = imports

    def merge(self, other):
        """
        Returns a new behaviour that is the given behaviour merged with this one. Both this and the other behaviour will
        be unmodified by this merge.

        :param other: The other behaviour to merge this behaviour with.
        :type other: Behaviour
        :return: the new merged behaviour.
        """
        kinds = self.kinds[:]
        for otherKind in other.kinds:
            kinds.append(otherKind)
        content = self.content + other.content
        imports = self.imports.union(other.imports)
        return Behaviour(None, kinds, content, imports)

    def __str__(self):
        return 'Behaviour { kinds: ' + str(self.kinds) + ' }'
