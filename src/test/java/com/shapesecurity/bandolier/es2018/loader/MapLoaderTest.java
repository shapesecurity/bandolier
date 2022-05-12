package com.shapesecurity.bandolier.es2018.loader;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MapLoaderTest {
    @Test
    public void testPresence() throws Throwable {
        Map<Path, String> map = new HashMap<>();
        map.put(Paths.get("/a"), "a");
        IResourceLoader loader = new MapLoader(map);
        Assert.assertTrue(loader.exists(Paths.get("/a")));
        Assert.assertEquals("a", loader.loadResource(Paths.get("/a")));
        Assert.assertFalse(loader.exists(Paths.get("/b")));
    }

    @Test
    public void testNormalisation() throws Throwable {
        Map<Path, String> map = new HashMap<>();
        map.put(Paths.get("/a"), "a");
        IResourceLoader loader = new MapLoader(map);
        assertContents(loader, "/a", "a");
        assertContents(loader, "a", "a");
        assertContents(loader, "/b/../c/../a", "a");
        assertContents(loader, "/a/b/..", "a");
        assertContents(loader, "../a", "a");
        assertContents(loader, "/a/.", "a");
        assertContents(loader, "////./a", "a");
    }

    private void assertContents(IResourceLoader loader, String path, String contents) throws IOException {
        Assert.assertTrue(loader.exists(Paths.get(path)));
        Assert.assertEquals(contents, loader.loadResource(Paths.get(path)));
    }
}
