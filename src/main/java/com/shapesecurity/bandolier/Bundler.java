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

import com.shapesecurity.bandolier.loader.FileLoader;
import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.ArrayExpression;
import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.BinaryExpression;
import com.shapesecurity.shift.ast.BindingBindingWithDefault;
import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.ConditionalExpression;
import com.shapesecurity.shift.ast.DataProperty;
import com.shapesecurity.shift.ast.Directive;
import com.shapesecurity.shift.ast.ExportAllFrom;
import com.shapesecurity.shift.ast.ExportFrom;
import com.shapesecurity.shift.ast.Expression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.FormalParameters;
import com.shapesecurity.shift.ast.FunctionBody;
import com.shapesecurity.shift.ast.FunctionDeclaration;
import com.shapesecurity.shift.ast.FunctionExpression;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.IfStatement;
import com.shapesecurity.shift.ast.Import;
import com.shapesecurity.shift.ast.ImportNamespace;
import com.shapesecurity.shift.ast.LiteralBooleanExpression;
import com.shapesecurity.shift.ast.LiteralNumericExpression;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.NewExpression;
import com.shapesecurity.shift.ast.Node;
import com.shapesecurity.shift.ast.ObjectExpression;
import com.shapesecurity.shift.ast.ObjectProperty;
import com.shapesecurity.shift.ast.ReturnStatement;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.SpreadElementExpression;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.ast.StaticMemberExpression;
import com.shapesecurity.shift.ast.StaticPropertyName;
import com.shapesecurity.shift.ast.ThisExpression;
import com.shapesecurity.shift.ast.ThrowStatement;
import com.shapesecurity.shift.ast.UnaryExpression;
import com.shapesecurity.shift.ast.VariableDeclaration;
import com.shapesecurity.shift.ast.VariableDeclarationKind;
import com.shapesecurity.shift.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.ast.VariableDeclarator;
import com.shapesecurity.shift.ast.operators.BinaryOperator;
import com.shapesecurity.shift.ast.operators.UnaryOperator;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class Bundler {

	/**
	 * Bundles the module at the specified path using the default resolver and loaders
	 * @param filePath path to the module
	 * @return a bundled script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull Script bundle(@NotNull Path filePath) throws ModuleLoaderException {
		return bundle(filePath, new FileSystemResolver(), new FileLoader());
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
	public static @NotNull Script bundle(@NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			return bundleString(loader.loadResource(filePath), filePath, resolver, loader);
		} catch (IOException e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script.
	 * @param mod the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull Script bundleString(@NotNull String mod, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			Module module = Parser.parseModule(mod);
			Map<String, Module> modules = loadDependencies(module, filePath, resolver, loader);

			StandardModuleBundler bundler = new StandardModuleBundler(modules);
			return bundler.bundleEntrypoint(filePath.toString());
		} catch (JsError e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}


	public static @NotNull Script bundleWithTreeShaking(@NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			return bundleStringWithTreeShaking(loader.loadResource(filePath), filePath, resolver, loader);
		} catch (IOException e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	public static @NotNull Script bundleStringWithTreeShaking(@NotNull String mod, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			Module module = Parser.parseModule(mod);
			Map<String, Module> modules = loadDependencies(module, filePath, resolver, loader);

			TreeShakingModuleBundler bundler = new TreeShakingModuleBundler(modules);
			return bundler.bundleEntrypoint(filePath.toString());
		} catch (JsError e) {
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
	private static @NotNull Map<String, Module> loadDependencies(@NotNull Module module, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader)
		throws ModuleLoaderException {

		Map<String, Module> loadedModules = new HashMap<>();
		LinkedList<String> toLoad = new LinkedList<>();
		ImportResolvingRewriter rewriter = new ImportResolvingRewriter(resolver);
		Module rewritten = rewriter.rewrite(module, filePath.getParent());
		loadedModules.put(filePath.toString(), rewritten);
		toLoad.add(filePath.toString());

		while (!toLoad.isEmpty()) {
			String root = toLoad.remove();
			for (String dependency : collectDirectDependencies(loadedModules.get(root))) {
				if (!loadedModules.containsKey(dependency)) {
					try {
						module = Parser.parseModule(loader.loadResource(Paths.get(dependency)));
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

	static ImmutableList<String> collectDirectDependencies(@NotNull Module m) {
		return m.items.bind(s -> {
			if (s instanceof Import) {
				return ImmutableList.of(((Import) s).getModuleSpecifier());
			} else if (s instanceof ImportNamespace) {
				return ImmutableList.of(((ImportNamespace) s).getModuleSpecifier());
			} else if (s instanceof ExportAllFrom) {
				return ImmutableList.of(((ExportAllFrom) s).getModuleSpecifier());
			} else if (s instanceof ExportFrom) {
				return ((ExportFrom) s).getModuleSpecifier().toList();
			}
			return ImmutableList.empty();
		});
	}
}
