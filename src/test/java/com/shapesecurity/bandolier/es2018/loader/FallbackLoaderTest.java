package com.shapesecurity.bandolier.es2018.loader;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FallbackLoaderTest {

    // neither
    @Test
    public void testNeither() throws Throwable {
        IResourceLoader loader = new FallbackLoader(EmptyLoader.getInstance(), EmptyLoader.getInstance());
        Assert.assertFalse(loader.exists(Paths.get("")));
    }

    // primary only
    @Test
    public void testPrimary() throws Throwable {
        Map<Path, String> map = new HashMap<>();
        map.put(Paths.get("/z"), "z");
        IResourceLoader loaderA = new MapLoader(map);
        IResourceLoader loader = new FallbackLoader(loaderA, EmptyLoader.getInstance());
        Assert.assertTrue(loader.exists(Paths.get("/z")));
        Assert.assertEquals("z", loader.loadResource(Paths.get("/z")));
    }

    // fallback only
    @Test
    public void testFallback() throws Throwable {
        Map<Path, String> map = new HashMap<>();
        map.put(Paths.get("/z"), "z");
        IResourceLoader loaderA = new MapLoader(map);
        IResourceLoader loader = new FallbackLoader(EmptyLoader.getInstance(), loaderA);
        Assert.assertTrue(loader.exists(Paths.get("/z")));
        Assert.assertEquals("z", loader.loadResource(Paths.get("/z")));
    }

    // both
    @Test
    public void testBoth() throws Throwable {
        Map<Path, String> mapA = new HashMap<>();
        mapA.put(Paths.get("/a"), "a");
        mapA.put(Paths.get("/c"), "A");
        IResourceLoader loaderA = new MapLoader(mapA);

        Map<Path, String> mapB = new HashMap<>();
        mapB.put(Paths.get("/b"), "b");
        mapB.put(Paths.get("/c"), "B");
        IResourceLoader loaderB = new MapLoader(mapB);

        IResourceLoader loader = new FallbackLoader(loaderA, loaderB);
        Assert.assertTrue(loader.exists(Paths.get("/a")));
        Assert.assertEquals("a", loader.loadResource(Paths.get("/a")));
        Assert.assertTrue(loader.exists(Paths.get("/b")));
        Assert.assertEquals("b", loader.loadResource(Paths.get("/b")));
        Assert.assertTrue(loader.exists(Paths.get("/c")));
        Assert.assertEquals("A", loader.loadResource(Paths.get("/c")));
        Assert.assertFalse(loader.exists(Paths.get("/z")));
    }

    @Test
    public void testMany() throws Throwable {
        Map<Path, String> mapA = new HashMap<>();
        mapA.put(Paths.get("/abc"), "A");
        IResourceLoader loaderA = new MapLoader(mapA);

        Map<Path, String> mapB = new HashMap<>();
        mapB.put(Paths.get("/abc"), "B");
        mapB.put(Paths.get("/bc"), "B");
        IResourceLoader loaderB = new MapLoader(mapB);

        Map<Path, String> mapC = new HashMap<>();
        mapC.put(Paths.get("/abc"), "C");
        mapC.put(Paths.get("/bc"), "C");
        mapC.put(Paths.get("/c"), "C");
        IResourceLoader loaderC = new MapLoader(mapC);

        IResourceLoader loader = new FallbackLoader(loaderA, loaderB, loaderC);
        Assert.assertTrue(loader.exists(Paths.get("/abc")));
        Assert.assertEquals("A", loader.loadResource(Paths.get("/abc")));
        Assert.assertTrue(loader.exists(Paths.get("/bc")));
        Assert.assertEquals("B", loader.loadResource(Paths.get("/bc")));
        Assert.assertTrue(loader.exists(Paths.get("/c")));
        Assert.assertEquals("C", loader.loadResource(Paths.get("/c")));
        Assert.assertFalse(loader.exists(Paths.get("/z")));
    }
}
