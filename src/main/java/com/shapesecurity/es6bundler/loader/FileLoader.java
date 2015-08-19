package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileLoader implements IResourceLoader {
	@NotNull
	@Override
	public Boolean exists(@NotNull Path path) {
		return Files.exists(path);
	}

	@NotNull
	@Override
	public String loadResource(@NotNull Path path) throws IOException {
		return Files.readAllLines(path).toString();
	}
}

