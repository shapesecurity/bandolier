package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class FileSystemResolver implements IResolver {
	@NotNull
	@Override
	public String resolve(@NotNull Path root, @NotNull String path) {
		if (path.startsWith(".")) {
			return root.resolve(path).normalize().toString();
		}
		return path;
	}
}
