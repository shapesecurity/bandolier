package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2016.ast.*;
import com.shapesecurity.shift.es2016.ast.Module;
import org.jetbrains.annotations.NotNull;

public abstract class ModuleHelper {

	private ModuleHelper() {

	}


	/**
	 * Returns a list of raw string module specifiers for a given module.
	 * @param module module to interpret
	 * @return list of dependent module specifiers.
	 */
	public static @NotNull ImmutableList<String> getModuleDependencies(@NotNull Module module) {
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
