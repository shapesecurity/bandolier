package com.shapesecurity.es6bundler.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ClassResourceLoader implements IResourceLoader {

	private final Class klass;

	public ClassResourceLoader(Class klass) {
		this.klass = klass;
	}

	@NotNull
	@Override
	public Boolean exists(@NotNull Path path) {
		return this.getStream(path.toString()) != null;
	}

	@NotNull
	@Override
	public String loadResource(@NotNull Path path) throws IOException {
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

