package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstracts the concept of checking if a resource exists and loading it as a string.
 */
public interface IResourceLoader {
	@NotNull
	Boolean exists(@NotNull Path path);

	@NotNull
	String loadResource(@NotNull Path path) throws IOException;
}
