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
package com.shapesecurity.bandolier.es2017.loader;

import com.google.gson.Gson;

import com.shapesecurity.functional.data.Maybe;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Resolves a path using node resolving semantics.
 *
 * <ul>
 *     <li> a path beginning with
 *     <code>/</code> is an absolute path
 *     </li>
 *
 *     <li> a path beginning with either
 *     <code>./</code>
 *     or
 *     <code>../</code>
 *     is a relative path
 *     </li>
 *     <li>
 *         if the path begins with anything else the resolver adds <code>node_modules</code> to the current directory
 *         if <code>node_modules</code> exists. If <code>node_modules</code> does not exist, the resolver
 *         recursively walks the parent directory looking for <code>node_modules</code>.
 *     </li>
 *     <li>
 *     </li>
 * </ul>
 *
 *  If the resolved path is a directory, the resolver looks in <code>package.json</code> (if it exists)
 *  for the <code>main</code> field to determine the main module.
 *
 * @see <a href="https://nodejs.org/api/modules.html#modules_loading_from_node_modules_folders">Node Modules</a>
 */
public class NodeResolver implements IResolver {

	@NotNull
	private final IResourceLoader loader;

	public NodeResolver() {
		this(new FileLoader());
	}

	/**
	 * Create a new resolver for node modules with the specified resource loader.
	 * @param loader Used to determine if a resource exists
	 */
	public NodeResolver(@NotNull IResourceLoader loader) {
		this.loader = loader;
	}


	@NotNull
	@Override
	public String resolve(@NotNull Path root, @NotNull String path) {
		if (path.startsWith(".") || path.startsWith("/")) {
			Path toCheck = path.startsWith(".") ? root.resolve(path).normalize() : Paths.get(path);

			Maybe<String> f = resolveAsFile(toCheck);
			if (f.isNothing()) {
				return resolveAsDir(toCheck).orJust(path);
			}
			if (f.isJust()) {
				return f.fromJust();
			}
		}
		// attempt to find the file in node_modules, fallback to
		// the passed in path
		return resolveNodeModules(root, path).orJust(path);
	}

	@NotNull
	private Maybe<String> resolveNodeModules(Path cwd, @NotNull String path) {
		if (cwd == null) { return Maybe.empty(); }

		Path toCheck = cwd.resolve("node_modules").resolve(path);

		Maybe<String> f = resolveAsFile(toCheck);
		if (f.isNothing()) {
			f = resolveAsDir(toCheck);
		}
		if (f.isJust()) {
			return f;
		}

		return resolveNodeModules(cwd.getParent(), path);
	}


	@NotNull
	private Maybe<String> resolveAsFile(@NotNull Path path) {
		String pathJs = path.toString() + ".js";
		String pathJson = path.toString() + ".json";

		if (this.loader.exists(path) && !this.hasDirFiles(path)) {
			return Maybe.of(path.toString());
		} else if (this.loader.exists(Paths.get(pathJs)) && !this.hasDirFiles(Paths.get(pathJs))) {
			return Maybe.of(pathJs);
		} else if (this.loader.exists(Paths.get(pathJson)) && !this.hasDirFiles(Paths.get(pathJson))) {
			return Maybe.of(pathJson);
		}
		return Maybe.empty();

	}

	// Since the resolver should support resources that do not have a concept of "directory" (for
	// example a JAR file), we need to check if a path might have resources inside of it.
	@NotNull
	private Boolean hasDirFiles(@NotNull Path path) {
		return this.loader.exists(path.resolve("package.json")) ||
				this.loader.exists(path.resolve("index.js")) ||
				this.loader.exists(path.resolve("index.json"));
	}


	@NotNull
	private Maybe<String> resolveAsDir(@NotNull Path path) {
		if (this.loader.exists(path.resolve("package.json"))) {
			Gson gson = new Gson();
			try {
				String json = this.loader.loadResource(path.resolve("package.json"));
				NodePackageJson packageJson = gson.fromJson(json, NodePackageJson.class);
				if (packageJson.main != null) {
					return resolveAsFile(path.resolve(packageJson.main));
				}
			} catch (IOException e) {
				return Maybe.empty();
			}
		}
		if (this.loader.exists(path.resolve("index.js"))) {
			return Maybe.of(path.resolve("index.js").toString());
		}
		if (this.loader.exists(path.resolve("index.json"))) {
			return Maybe.of(path.resolve("index.json").toString());
		}
		return Maybe.empty();
	}

	class NodePackageJson {
		public String main;
	}

}

