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

public class ModuleLoaderException extends Exception {
	public ModuleLoaderException(@Nonnull String module, @Nonnull Exception cause) {
		super("Module Loader Exception: module " + module + " cannot be loaded: " + cause.getMessage(), cause);
	}
}