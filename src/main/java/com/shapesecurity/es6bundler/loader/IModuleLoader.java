package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface IModuleLoader {
	/**
	 * Loads the module located at the input path.
	 *
	 * @param path is the absolute path to the module to be loaded.
	 * @return the module
	 */
	@NotNull
	String loadModule(@NotNull Path path) throws ModuleLoaderException;
}
