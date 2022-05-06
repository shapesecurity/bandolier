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

import com.shapesecurity.shift.es2018.ast.Module;
import com.shapesecurity.shift.es2018.parser.JsError;
import com.shapesecurity.shift.es2018.parser.Parser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstracts the concept of checking if a resource exists and loading it as a string or module.
 */
public interface IResourceLoader {
	@Nonnull
	Boolean exists(@Nonnull Path path);

	@Nonnull
	String loadResource(@Nonnull Path path) throws IOException;

	@Nonnull
	default Module loadModule(@Nonnull Path path) throws IOException, JsError {
		return Parser.parseModule(this.loadResource(path));
	}
}
