package spin.example;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import spin.example.singleton.ResourceSingleton;

public class Example1Test {

    @Test
    public void test1() {
        Resource resource = ResourceSingleton.singleton();
        resource.hit();
    }

    @Test
    public void test2() {
        // This forces us to load a dependency just so we can make sure we are gathering all dependencies correctly.
        Matchers matchers = new Matchers();
        Assert.assertNotEquals(this, matchers);

        Resource resource = ResourceSingleton.singleton();
        resource.hit();
        System.out.println(resource.getHits());
        System.err.println("I am test2");
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }
}
