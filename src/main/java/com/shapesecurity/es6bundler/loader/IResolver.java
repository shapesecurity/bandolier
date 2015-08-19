package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface IResolver {
	/**
	 * Given a root and a (potentially relative) path, resolves the path to an absolute path.
	 */
	@NotNull
	String resolve(@NotNull Path root, @NotNull String path);
}
