package spin.example.dir;

import org.junit.Test;
import spin.example.Resource;
import spin.example.singleton.ResourceSingleton;

public class Example2Test {

    @Test
    public void test1() {
        Resource resource = ResourceSingleton.singleton();
        resource.hit();
        System.out.println(resource.getHits());
    }

    @Test
    public void test2() {
        Resource resource = ResourceSingleton.singleton();
        resource.hit();
        System.out.println(resource.getHits());
        System.err.println("I am test2");
    }

    @Test
    public void test3() {
        Resource resource = ResourceSingleton.singleton();
        resource.hit();
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }
}
