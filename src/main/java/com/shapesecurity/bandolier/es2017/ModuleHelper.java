package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2018.ast.ExportAllFrom;
import com.shapesecurity.shift.es2018.ast.ExportDeclaration;
import com.shapesecurity.shift.es2018.ast.ExportFrom;
import com.shapesecurity.shift.es2018.ast.Import;
import com.shapesecurity.shift.es2018.ast.ImportDeclaration;
import com.shapesecurity.shift.es2018.ast.ImportNamespace;
import com.shapesecurity.shift.es2018.ast.Module;

import javax.annotation.Nonnull;

public abstract class ModuleHelper {

	private ModuleHelper() {

	}


	/**
	 * Returns a list of raw string module specifiers for a given module.
	 * @param module module to interpret
	 * @return list of dependent module specifiers.
	 */
	@Nonnull
	public static ImmutableList<String> getModuleDependencies(@Nonnull Module module) {
		return module.items.bind(s -> {
			if (s instanceof ImportDeclaration) {
				if (s instanceof Import) {
					return ImmutableList.of(((Import) s).moduleSpecifier);
				} else if (s instanceof ImportNamespace) {
					return ImmutableList.of(((ImportNamespace) s).moduleSpecifier);
				}
			} else if (s instanceof ExportDeclaration) {
				if (s instanceof ExportAllFrom) {
					return ImmutableList.of(((ExportAllFrom) s).moduleSpecifier);
				} else if (s instanceof ExportFrom) {
					return ImmutableList.of(((ExportFrom) s).moduleSpecifier);
				}
			}
			return ImmutableList.empty();
		});
	}
}
