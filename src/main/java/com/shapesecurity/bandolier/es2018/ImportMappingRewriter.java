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
package com.shapesecurity.bandolier.es2018;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2018.ast.ExportAllFrom;
import com.shapesecurity.shift.es2018.ast.ExportDeclaration;
import com.shapesecurity.shift.es2018.ast.ExportFrom;
import com.shapesecurity.shift.es2018.ast.Import;
import com.shapesecurity.shift.es2018.ast.ImportDeclaration;
import com.shapesecurity.shift.es2018.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.es2018.ast.ImportNamespace;
import com.shapesecurity.shift.es2018.ast.Module;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Rewrites imports based on a provided mapping from old paths to new ones.
 */
public class ImportMappingRewriter {


	@Nonnull
	private final Map<String, String> importMap;

	/**
	 * Rewrites imports based on the provided mapping
	 * @param importMap maps the current import path to a new one, must contain a mapping for every
	 *                  import in the module
	 */
	public ImportMappingRewriter(@Nonnull Map<String, String> importMap) {
		this.importMap = importMap;
	}

	/**
	 * rewrites all imports in the module based on the map the class was constructed with
	 * @param module the module to rewrite
	 * @return a module with all imports rewritten based on the map
	 */
	@Nonnull
	public Module rewrite(@Nonnull Module module) {
		ImmutableList<ImportDeclarationExportDeclarationStatement> items =
			module.items.bind(x -> rewritePaths(x));

		return new Module(module.directives, items);
	}

	@Nonnull
	private String lookupMapping(@Nonnull String path) {
		assert this.importMap.containsKey(path);

		return this.importMap.get(path);
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewritePaths(
			ImportDeclarationExportDeclarationStatement statement) {
		if (statement instanceof ImportDeclaration) {
			return rewriteImportDeclaration((ImportDeclaration) statement);
		} else if (statement instanceof ExportDeclaration) {
			return rewriteExportDeclaration((ExportDeclaration) statement);
		} else {
			return ImmutableList.of(statement); // do not transform other statements
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImportDeclaration(ImportDeclaration declaration) {
		if (declaration instanceof Import) {
			return rewriteImport((Import) declaration);
		} else if (declaration instanceof ImportNamespace) {
			return rewriteImportNamespace((ImportNamespace) declaration);
		} else {
			return ImmutableList.empty(); //This should never happen!
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImport(Import imp) {
		return ImmutableList.of(new Import(imp.defaultBinding, imp.namedImports,
				this.lookupMapping(imp.moduleSpecifier)));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteImportNamespace(ImportNamespace imp) {
		return ImmutableList.of(new ImportNamespace(imp.defaultBinding, imp.namespaceBinding,
				this.lookupMapping(imp.moduleSpecifier)));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportDeclaration(ExportDeclaration declaration) {
		if (declaration instanceof ExportAllFrom) {
			return rewriteExportAllFrom((ExportAllFrom) declaration);
		} else if (declaration instanceof ExportFrom) {
			return rewriteExportFrom((ExportFrom) declaration);
		} else {
			return ImmutableList.of(declaration);
		}
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportAllFrom(ExportAllFrom exp) {
		return ImmutableList.of(new ExportAllFrom(this.lookupMapping(exp.moduleSpecifier)));
	}

	private ImmutableList<ImportDeclarationExportDeclarationStatement> rewriteExportFrom(ExportFrom exp) {
		ExportFrom newExp = new ExportFrom(exp.namedExports, importMap.get(exp.moduleSpecifier));
		return ImmutableList.of(newExp);
	}
}
