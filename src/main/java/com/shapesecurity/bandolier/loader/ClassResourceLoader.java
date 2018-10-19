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
package com.shapesecurity.bandolier.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;

public class ClassResourceLoader extends CachedResourceLoader {

	private final Class klass;

	public ClassResourceLoader(Class klass) {
		this.klass = klass;
	}

	@NotNull
	@Override
	public Boolean existsBackend(@NotNull Path path) {
		return this.getStream(path.toString()) != null;
	}

	@NotNull
	@Override
	public String loadResourceBackend(@NotNull Path path) throws IOException {
		InputStream stream = this.getStream(path.toString());
		if (stream == null) {
			throw new IOException("Cannot load resource: " + path.toString());
		}

		return this.readFile(stream);
	}

	@Nullable
	private InputStream getStream(@NotNull String path) {
		return this.klass.getResourceAsStream(path);
	}

	@NotNull
	private String readFile(@NotNull InputStream stream) throws IOException {
		InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(reader);

		StringBuilder stringBuilder = new StringBuilder();
		String line = bufferedReader.readLine();
		while (line != null) {
			stringBuilder.append(line).append('\n');
			line = bufferedReader.readLine();
		}

		return stringBuilder.toString();
	}
}

