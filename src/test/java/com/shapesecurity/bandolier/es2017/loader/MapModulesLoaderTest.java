package com.shapesecurity.bandolier.es2017.loader;

import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.parser.Parser;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MapModulesLoaderTest {
	@Test
	public void testPresence() throws Throwable {
		Map<Path, Module> map = new HashMap<>();
		Module aModule = Parser.parseModule("a");
		map.put(Paths.get("/a"), aModule);
		IResourceLoader loader = new MapModulesLoader(map);
		Assert.assertTrue(loader.exists(Paths.get("/a")));
		Assert.assertEquals("a", loader.loadResource(Paths.get("/a")));
		Assert.assertSame(aModule, loader.loadModule(Paths.get("/a")));
		Assert.assertFalse(loader.exists(Paths.get("/b")));
	}
}
