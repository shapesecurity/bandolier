/*
 * Copyright 2016 Shape Security, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.bundlers.IModuleBundler;
import com.shapesecurity.bandolier.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.loader.FileLoader;
import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2016.ast.*;
import com.shapesecurity.shift.es2016.ast.Module;
import com.shapesecurity.shift.es2016.ast.Script;
import com.shapesecurity.shift.es2016.parser.JsError;
import com.shapesecurity.shift.es2016.parser.Parser;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Bundler {

	/**
	 * Bundles the module at the specified path using the default resolver and loaders
	 * @param filePath path to the module
	 * @return a bundled script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull
	Script bundle(@NotNull Path filePath) throws ModuleLoaderException {
		return bundle(filePath, new FileSystemResolver(), new FileLoader(), new StandardModuleBundler());
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script.
	 *
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException when the module fails to load
	 */
	public static @NotNull Script bundle(@NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundleString(loader.loadResource(filePath), filePath, resolver, loader, bundler);
		} catch (IOException e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved.
	 * @param mod the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull Script bundleString(@NotNull String mod, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundler.bundleEntrypoint(filePath.toString(), loadDependencies(Parser.parseModule(mod), filePath, resolver, loader));
		} catch (Exception e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Recursively loads all the modules referenced by the input module.
	 *
	 * @param filePath is the path to the input module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load
	 * @return is a map from module names (path to modules) to the loaded modules.
	 * @throws ModuleLoaderException when the module fails to load
	 */
	private static @NotNull HashTable<String, BandolierModule> loadDependencies(@NotNull Module module, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader)
		throws ModuleLoaderException {

		HashTable<String, BandolierModule> loadedModules = HashTable.emptyUsingEquality();
		ImmutableList<String> toLoad = ImmutableList.empty();
		ImportResolvingRewriter rewriter = new ImportResolvingRewriter(resolver);
		Module rewritten = rewriter.rewrite(module, filePath.getParent());
		loadedModules = loadedModules.put(filePath.toString(), new BandolierModule(filePath.toString(), rewritten));
		toLoad = toLoad.cons(filePath.toString());

		while (!toLoad.isEmpty()) {
			String root = toLoad.maybeHead().fromJust();
			toLoad = toLoad.maybeTail().fromJust();
			for (String dependency : loadedModules.get(root).fromJust().getDependencies()) {
				if (!loadedModules.containsKey(dependency)) {
					try {
						switch (getFileExtension(dependency)) {
							case "json":
								module = Parser.parseModule("export default (" + loader.loadResource(Paths.get(dependency)) + ");");
								break;
							case "js":
							case "esm":
							default:
								module = Parser.parseModule(loader.loadResource(Paths.get(dependency)));
						}
					} catch (IOException | JsError e) {
						throw new ModuleLoaderException(dependency, e);
					}
					rewritten = rewriter.rewrite(module, Paths.get(dependency).getParent());
					loadedModules = loadedModules.put(dependency, new BandolierModule(dependency, rewritten));
					toLoad = toLoad.cons(dependency);
				}
			}
		}

		return loadedModules;
	}

	private static @NotNull String getFileExtension(@NotNull String filename) {
		int i = filename.lastIndexOf('.');
		if (i < 0) return "";
		return filename.substring(i + 1);
	}
}
