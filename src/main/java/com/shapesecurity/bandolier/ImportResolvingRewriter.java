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


import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.ExportAllFrom;
import com.shapesecurity.shift.ast.ExportDeclaration;
import com.shapesecurity.shift.ast.ExportFrom;
import com.shapesecurity.shift.ast.Import;
import com.shapesecurity.shift.ast.ImportDeclaration;
import com.shapesecurity.shift.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.ast.ImportNamespace;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.bandolier.loader.IResolver;

import java.nio.file.Path;


/**
 * Rewrites all of the import paths to the appropriately resolve paths.
 */
public class ImportResolvingRewriter {
	private final IResolver resolver;

	/**
	 * By default use the standard file system resolver that simply converts relative paths to
	 * absolute paths
	 */
	public ImportResolvingRewriter() {
		this(new FileSystemResolver());
	}

	/**
	 * Create a new rewriter with the provided resolver.
	 * @param resolver used to resolve each import path.
	 */
	public ImportResolvingRewriter(IResolver resolver) {
		this.resolver = resolver;
	}

	private String resolvePath(Path root, String path) {
		return this.resolver.resolve(root, path);

	}

	/**
	 * Rewrites the module
	 * @param module the module to rewrite
	 * @param path represents the path to the current module being rewritten
	 * @return
	 */
	public Module rewrite(Module module, Path path) {
		ImmutableList<ImportDeclarationExportDeclarationStatement> items = module.getItems().bind(x -> rewritePaths(x, path));

		return new Module(module.directives, items);
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewritePaths(ImportDeclarationExportDeclarationStatement statement, Path path) {
		if (statement instanceof ImportDeclaration) {
			return rewriteImportDeclaration((ImportDeclaration) statement, path);
		} else if (statement instanceof ExportDeclaration) {
			return rewriteExportDeclaration((ExportDeclaration) statement, path);
		} else {
			return ImmutableList.of((Statement) statement); // do not transform other statements
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImportDeclaration(ImportDeclaration declaration, Path path) {
		if (declaration instanceof Import) {
			return rewriteImport((Import) declaration, path);
		} else if (declaration instanceof ImportNamespace) {
			return rewriteImportNamespace((ImportNamespace) declaration, path);
		} else {
			return ImmutableList.empty(); // this should never happen!
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImport(Import imp, Path path) {
		return ImmutableList.of(
			new Import(imp.getDefaultBinding(),
					   imp.getNamedImports(),
					   resolvePath(path, imp.getModuleSpecifier())));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImportNamespace(ImportNamespace imp, Path path) {
		return ImmutableList.of(
			new ImportNamespace(imp.getDefaultBinding(),
								imp.getNamespaceBinding(),
								resolvePath(path, imp.getModuleSpecifier())));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportDeclaration(ExportDeclaration declaration, Path path) {
		if (declaration instanceof ExportAllFrom) {
			return rewriteExportAllFrom((ExportAllFrom) declaration, path);
		} else if (declaration instanceof ExportFrom) {
			return rewriteExportFrom((ExportFrom) declaration, path);
		} else {
			return ImmutableList.of(declaration);
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportAllFrom(ExportAllFrom exp, Path path) {
		return ImmutableList.of(new ExportAllFrom(resolvePath(path, exp.getModuleSpecifier())));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportFrom(ExportFrom exp, Path path) {
		ExportFrom newExp = new ExportFrom(exp.getNamedExports(), exp.getModuleSpecifier().map(x -> resolvePath(path, x)));
		return ImmutableList.of(newExp);
	}
}
