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
package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.bundlers.IModuleBundler;
import com.shapesecurity.bandolier.es2017.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2017.loader.FileLoader;
import com.shapesecurity.bandolier.es2017.loader.FileSystemResolver;
import com.shapesecurity.bandolier.es2017.loader.IResolver;
import com.shapesecurity.bandolier.es2017.loader.IResourceLoader;
import com.shapesecurity.bandolier.es2017.loader.ModuleLoaderException;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.JsError;
import com.shapesecurity.shift.es2017.parser.Parser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

public class Bundler {

	/**
	 * Bundles the module at the specified path using the default resolver and loaders
	 * @param options options object
	 * @param filePath path to the module
	 * @return a bundled script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundle(@Nonnull BundlerOptions options, @Nonnull Path filePath) throws ModuleLoaderException {
		return bundle(options, filePath, new FileSystemResolver(), new FileLoader(), new PiercedModuleBundler());
	}

	/**
	 * Bundles the module at the specified path using the default resolver and loaders, uses DEFAULT_OPTIONS.
	 * @param filePath path to the module
	 * @return a bundled script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundle(@Nonnull Path filePath) throws ModuleLoaderException {
		return bundle(BundlerOptions.DEFAULT_OPTIONS, filePath);
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script.
	 *
	 * @param options options object
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException when the module fails to load
	 */
	@Nonnull
	public static Script bundle(@Nonnull BundlerOptions options, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundleModule(options, loader.loadModule(filePath), filePath, resolver, loader, bundler);
		} catch (IOException | JsError e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script, uses DEFAULT_OPTIONS.
	 *
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException when the module fails to load
	 */
	@Nonnull
	public static Script bundle(@Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundle(BundlerOptions.DEFAULT_OPTIONS, filePath, resolver, loader, bundler);
	}

	/**
	 * Bundles the module provided as a string along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved.
	 * @param options options object
	 * @param source the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundleString(@Nonnull BundlerOptions options, @Nonnull String source, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundler.bundleEntrypoint(options, filePath.toAbsolutePath().normalize().toString(), loadDependencies(Parser.parseModule(source), filePath, resolver, loader));
		} catch (Exception e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved, uses DEFAULT_OPTIONS.
	 * @param source the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundleString(@Nonnull String source, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundleString(BundlerOptions.DEFAULT_OPTIONS, source, filePath, resolver, loader, bundler);
	}

	/**
	 * Bundles the module provided as a parsed Module along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved.
	 * @param options options object
	 * @param mod the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundleModule(@Nonnull BundlerOptions options, @Nonnull Module mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundler.bundleEntrypoint(options, filePath.toAbsolutePath().normalize().toString(), loadDependencies(mod, filePath, resolver, loader));
		} catch (Exception e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a parsed Module along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved, uses DEFAULT_OPTIONS.
	 * @param mod the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Script bundleModule(@Nonnull Module mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundleModule(BundlerOptions.DEFAULT_OPTIONS, mod, filePath, resolver, loader, bundler);
	}

	/**
	 * Bundles the module at the specified path using the default resolver and loaders.
	 * @param options options object
	 * @param filePath path to the module
	 * @return a bundled script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleWithEarlyErrors(@Nonnull BundlerOptions options, @Nonnull Path filePath) throws ModuleLoaderException {
		return bundleWithEarlyErrors(options, filePath, new FileSystemResolver(), new FileLoader(), new PiercedModuleBundler());
	}

	/**
	 * Bundles the module at the specified path using the default resolver and loaders, uses DEFAULT_OPTIONS.
	 * @param filePath path to the module
	 * @return a bundled script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleWithEarlyErrors(@Nonnull Path filePath) throws ModuleLoaderException {
		return bundleWithEarlyErrors(BundlerOptions.DEFAULT_OPTIONS, filePath);
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script.
	 *
	 * @param options options object
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException when the module fails to load
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleWithEarlyErrors(@Nonnull BundlerOptions options, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundleModuleWithEarlyErrors(options, loader.loadModule(filePath), filePath, resolver, loader, bundler);
		} catch (IOException | JsError e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script, uses DEFAULT_OPTIONS.
	 *
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException when the module fails to load
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleWithEarlyErrors(@Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundleWithEarlyErrors(BundlerOptions.DEFAULT_OPTIONS, filePath, resolver, loader, bundler);
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved.
	 * @param options options object
	 * @param mod the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleStringWithEarlyErrors(@Nonnull BundlerOptions options, @Nonnull String mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundler.bundleEntrypointWithEarlyErrors(options, filePath.toAbsolutePath().normalize().toString(), loadDependencies(Parser.parseModule(mod), filePath, resolver, loader));
		} catch (Exception e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved, uses DEFAULT_OPTIONS.
	 * @param mod the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleStringWithEarlyErrors(@Nonnull String mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundleStringWithEarlyErrors(BundlerOptions.DEFAULT_OPTIONS, mod, filePath, resolver, loader, bundler);
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved.
	 * @param options options object
	 * @param mod the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleModuleWithEarlyErrors(@Nonnull BundlerOptions options, @Nonnull Module mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		try {
			return bundler.bundleEntrypointWithEarlyErrors(options, filePath.toAbsolutePath().normalize().toString(), loadDependencies(mod, filePath, resolver, loader));
		} catch (Exception e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script. Deterministic as long as the bundler has no sources of nondeterminism other than the ordering
	 * of its input map, and the resolver and loader are well-behaved, uses DEFAULT_OPTIONS.
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script with early errors
	 * @throws ModuleLoaderException
	 */
	@Nonnull
	public static Pair<Script, ImmutableList<EarlyError>> bundleModuleWithEarlyErrors(@Nonnull Module mod, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader, IModuleBundler bundler) throws ModuleLoaderException {
		return bundleModuleWithEarlyErrors(BundlerOptions.DEFAULT_OPTIONS, mod, filePath, resolver, loader, bundler);
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
	@Nonnull
	static Map<String, Module> loadDependencies(@Nonnull Module module, @Nonnull Path filePath, @Nonnull IResolver resolver, @Nonnull IResourceLoader loader)
		throws ModuleLoaderException {

		Map<String, Module> loadedModules = new LinkedHashMap<>();
		LinkedList<String> toLoad = new LinkedList<>();
		ImportResolvingRewriter rewriter = new ImportResolvingRewriter(resolver);
		filePath = filePath.toAbsolutePath().normalize();
		Module rewritten = rewriter.rewrite(module, filePath.getParent());
		loadedModules.put(filePath.toString(), rewritten);
		toLoad.add(filePath.toString());

		while (!toLoad.isEmpty()) {
			String root = toLoad.remove();
			for (String dependency : ModuleHelper.getModuleDependencies(loadedModules.get(root))) {
				if (!loadedModules.containsKey(dependency)) {
					try {
						switch (getFileExtension(dependency)) {
							case "json":
								module = Parser.parseModule("export default (" + loader.loadResource(Paths.get(dependency)) + ");");
								break;
							case "js":
							case "esm":
							default:
								module = loader.loadModule(Paths.get(dependency));
						}
					} catch (IOException | JsError e) {
						throw new ModuleLoaderException(dependency, e);
					}
					rewritten = rewriter.rewrite(module, Paths.get(dependency).getParent());
					loadedModules.put(dependency, rewritten);
					toLoad.add(dependency);
				}
			}
		}

		return loadedModules;
	}

	@Nonnull
	private static String getFileExtension(@Nonnull String filename) {
		int i = filename.lastIndexOf('.');
		if (i < 0) return "";
		return filename.substring(i + 1);
	}
}
