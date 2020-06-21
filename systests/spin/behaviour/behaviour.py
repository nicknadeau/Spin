class Behaviour:
    def __init__(self, code, test_name=None, class_name=None):
        """
        Creates a new behaviour from an int code. Some behaviours also require a class name and/or test name as well.

        :param code: the integer code representing this behaviour.
        :param class_name: the class name of the test this behaviour is applied to.
        :param test_name: the name of the test this behaviour is applied to.
        :type code: int
        :type class_name: str
        :type test_name: str
        """
        self.content = []
        self.source_objects = set()
        self.fields = set()
        self.imports = set()
        self.throws = False
        self.desc = ""
        self.terminates = False

        if code < -1:
            raise ValueError("Unrecognized behaviour code: {}".format(code))
        elif code == 0:
            self.desc = "empty"
        elif code == 1:
            self.content = ["Thread.sleep(100);"]
            self.throws = True
            self.desc = "sleep-short"
        elif code == 2:
            self.content = ["throw new RuntimeException();"]
            self.desc = "throw-runtime"
            self.terminates = True
        elif code == 3:
            if class_name is None or test_name is None:
                raise ValueError("Behaviour code {} requires non-None class name and test name".format(code))
            self.content = ['System.out.println("{}:{}");'.format(class_name, test_name)]
            self.desc = "stdout-dull"
        elif code == 4:
            if class_name is None or test_name is None:
                raise ValueError("Behaviour code {} requires non-None class name and test name".format(code))
            self.content = ['System.err.println("{}:{}");'.format(class_name, test_name)]
            self.desc = "stderr-dull"
        elif code == 5:
            if test_name is None:
                raise ValueError("Behaviour code {} requires non-None test name".format(code))
            self.content = ["this.num{}Invokes++;".format(test_name)]
            self.fields = {"private int num{}Invokes = 0;".format(test_name)}
            self.desc = "count-test"
        elif code == 6:
            self.content = ["NUM_CLASS_INVOKES.incrementAndGet();"]
            self.fields = {"private static final AtomicInteger NUM_CLASS_INVOKES = new AtomicInteger(0);"}
            self.imports = {"java.util.concurrent.atomic.AtomicInteger;"}
            self.throws = False
            self.desc = "count-class"
            self.terminates = False
        elif code == 7:
            if test_name is None:
                raise ValueError("Behaviour code {} requires non-None test name".format(code))
            self.content = ["System.out.println(this.num{}Invokes);".format(test_name)]
            self.fields = {"private int num{}Invokes = 0;".format(test_name)}
            self.desc = "stdout-test-count"
        elif code == 8:
            if test_name is None:
                raise ValueError("Behaviour code {} requires non-None test name".format(code))
            self.content = ["System.err.println(this.num{}Invokes);".format(test_name)]
            self.fields = {"private int num{}Invokes = 0;".format(test_name)}
            self.desc = "stderr-test-count"
        elif code == 9:
            self.content = ["System.out.println(NUM_CLASS_INVOKES.get());"]
            self.fields = {"private static final AtomicInteger NUM_CLASS_INVOKES = new AtomicInteger(0);"}
            self.imports = {"java.util.concurrent.atomic.AtomicInteger;"}
            self.desc = "stdout-class-count"
        elif code == 10:
            self.content = ["System.err.println(NUM_CLASS_INVOKES.get());"]
            self.fields = {"private static final AtomicInteger NUM_CLASS_INVOKES = new AtomicInteger(0);"}
            self.imports = {"java.util.concurrent.atomic.AtomicInteger;"}
            self.desc = "stderr-class-count"
        elif code == 11:
            self.source_objects = {"s.a.Ticker"}
            self.content = ["this.ticker.tick();"]
            self.fields = {"private final Ticker ticker = new Ticker();"}
            self.imports = {"s.a.Ticker;"}
            self.desc = "source-tick"
        elif code == 12:
            self.source_objects = {"s.a.Ticker"}
            self.content = ["System.out.println(this.ticker.getTicks());"]
            self.fields = {"private final Ticker ticker = new Ticker();"}
            self.imports = {"s.a.Ticker;"}
            self.desc = "stdout-get-ticks"
        elif code == 13:
            self.source_objects = {"s.a.Ticker"}
            self.content = ["System.err.println(this.ticker.getTicks());"]
            self.fields = {"private final Ticker ticker = new Ticker();"}
            self.imports = {"s.a.Ticker;"}
            self.desc = "stderr-get-ticks"

    def __str__(self):
        return "Behaviour({})".format(self.desc)

    def merge(self, other):
        """
        Merges this behaviour with the other behaviour and returns the new merged behaviour. Copies of the two
        behaviours attributes are made so that modifications to them does not modify this new behaviour.

        Order does matter here. The other behaviour will be applied after this behaviour.

        :param other: the other behaviour to apply after this one in the merge.
        :return: the new behaviour.
        :type other: Behaviour
        :return:type: Behaviour
        """
        if self.terminates:
            raise AssertionError("Cannot merge {} because this is a terminating behaviour.".format(self))
        new_behaviour = Behaviour(-1)
        new_behaviour.source_objects = self.source_objects.copy() | other.source_objects.copy()
        new_behaviour.content = self.content.copy() + other.content.copy()
        new_behaviour.fields = self.fields.copy() | other.fields.copy()
        new_behaviour.imports = self.imports.copy() | other.imports.copy()
        new_behaviour.throws = self.throws or other.throws
        new_behaviour.desc = "{}, {}".format(self.desc, other.desc)
        new_behaviour.terminates = other.terminates
        return new_behaviour
