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

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class FileSystemResolver implements IResolver {
	@Nonnull
	@Override
	public String resolve(@Nonnull Path root, @Nonnull String path) {
		if (path.startsWith(".")) {
			return root.resolve(path).normalize().toString();
		}
		return path;
	}
}
