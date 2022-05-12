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
package com.shapesecurity.bandolier.es2018.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

public abstract class CachedResourceLoader implements IResourceLoader {

	private HashMap<Path, String> cachedFiles = new HashMap<>();

	public abstract Boolean existsBackend(@Nonnull Path path);

	public abstract String loadResourceBackend(@Nonnull Path path) throws IOException;

	@Nonnull
	@Override
	public final Boolean exists(@Nonnull Path path) {
		return cachedFiles.containsKey(path) || this.existsBackend(path);
	}

	@Nonnull
	@Override
	public final String loadResource(@Nonnull Path path) throws IOException {
		if (cachedFiles.containsKey(path)) {
			return cachedFiles.get(path);
		}
		String resource = this.loadResourceBackend(path);
		cachedFiles.put(path, resource);
		return resource;
	}
}

