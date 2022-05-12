package com.shapesecurity.bandolier.es2018.loader;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RootedFileLoaderTest {
    private static final Path FIXTURES_DIR = Paths.get("src/test/resources/fixtures").toAbsolutePath();

    // regular file exists
    @Test
    public void testFileExists() throws Throwable {
        RootedFileLoader loader = new RootedFileLoader(FIXTURES_DIR);
        Assert.assertTrue(loader.exists(Paths.get("/a")));
        Assert.assertEquals("a", loader.loadResource(Paths.get("/a")));
        Assert.assertTrue(loader.exists(Paths.get("/b")));
        Assert.assertEquals("b", loader.loadResource(Paths.get("/b")));
        Assert.assertFalse(loader.exists(Paths.get("/z")));
    }

    // file in some directory exists
    @Test
    public void testFileInSomeDirectoryExists() throws Throwable {
        RootedFileLoader loader = new RootedFileLoader(FIXTURES_DIR);
        Assert.assertTrue(loader.exists(Paths.get("/c/d")));
        Assert.assertEquals("c/d", loader.loadResource(Paths.get("/c/d")));
        Assert.assertTrue(loader.exists(Paths.get("/c/e/f")));
        Assert.assertEquals("c/e/f", loader.loadResource(Paths.get("/c/e/f")));

        Assert.assertFalse(loader.exists(Paths.get("/c")));
        Assert.assertFalse(loader.exists(Paths.get("/c/e")));
        Assert.assertFalse(loader.exists(Paths.get("/x/y/z")));
    }

    // cannot escape root
    @Test
    public void testCannotEscapeRoot() throws Throwable {
        RootedFileLoader loader = new RootedFileLoader(FIXTURES_DIR.resolve("c"));
        Assert.assertTrue(loader.exists(Paths.get("/d")));
        Assert.assertTrue(loader.exists(Paths.get("/e/f")));
        Assert.assertFalse(loader.exists(Paths.get("/../a")));
        Assert.assertFalse(loader.exists(Paths.get("/../b")));
    }

    // paths are normalised
    @Test
    public void testPathsAreNormalised() throws Throwable {
        RootedFileLoader loader = new RootedFileLoader(FIXTURES_DIR);
        Assert.assertTrue(loader.exists(Paths.get("/c/e/../d")));
        Assert.assertEquals("c/d", loader.loadResource(Paths.get("/c/e/../d")));
        Assert.assertTrue(loader.exists(Paths.get("//./a")));
        Assert.assertEquals("a", loader.loadResource(Paths.get("//./a")));
    }
}
