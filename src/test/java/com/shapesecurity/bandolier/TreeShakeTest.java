package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.shapesecurity.bandolier.TestUtils.testSource;
import static com.shapesecurity.bandolier.TestUtils.testTreeSource;

public class TreeShakeTest {
    Map<String, String> modules;
    IResourceLoader loader;
    private static IResolver resolver = new FileSystemResolver();

    @Before
    public void setup() {
        modules = new HashMap<>();
    }

    @Test
    public void shake_it() throws Exception {
        modules.put("/a", "export var a = 1");
        modules.put("/main", "import { a } from '/a'; export var result = a + 1");
        loader = new TestLoader(modules);

        testTreeSource("/main", "", resolver, loader);
    }
}
