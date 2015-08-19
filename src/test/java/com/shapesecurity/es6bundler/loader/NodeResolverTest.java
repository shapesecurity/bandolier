package com.shapesecurity.es6bundler.loader;


import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NodeResolverTest {

	private NodeResolver resolver;


	class MockPathLoader implements IResourceLoader {

		@NotNull
		@Override
		public Boolean exists(@NotNull Path path) {
			Map<String, Boolean> fs = new HashMap<>();
			fs.put("/foo/bar/baz", true);
			fs.put("/foo/bar/node_modules/f.js", true);
			fs.put("/node_modules/g.js", true);

			fs.put("/my/dir/index.js", true);

			return fs.containsKey(path.toString());
		}

		@NotNull
		@Override
		public String loadResource(@NotNull Path path) throws IOException {
			return "";
		}
	}

	@Before
	public void setup() {
		this.resolver = new NodeResolver(new MockPathLoader());
	}

	@Test
	public void resolverTest() {
		assertEquals("/foo/bar/baz", this.resolver.resolve(Paths.get("/foo/bar"), "./baz"));
		assertEquals("/foo/bar/node_modules/f.js", this.resolver.resolve(Paths.get("/foo/bar"), "f"));
		assertEquals("/node_modules/g.js", this.resolver.resolve(Paths.get("/foo/bar"), "g"));

		assertEquals("/my/dir/index.js", this.resolver.resolve(Paths.get("/my"), "./dir"));

	}
}
