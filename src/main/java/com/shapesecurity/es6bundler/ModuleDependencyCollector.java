package com.shapesecurity.es6bundler;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.Monoid;
import com.shapesecurity.shift.ast.ExportAllFrom;
import com.shapesecurity.shift.ast.ExportFrom;
import com.shapesecurity.shift.ast.Import;
import com.shapesecurity.shift.ast.ImportNamespace;
import com.shapesecurity.shift.visitor.MonoidalReducer;

import org.jetbrains.annotations.NotNull;


/**
 * ModuleDependencyCollector reduces a module to a list of module specifiers, imported by the given
 * module.
 */
public class ModuleDependencyCollector extends MonoidalReducer<ImmutableList<String>> {
	public ModuleDependencyCollector() {
		super(new ImportCollectionMonoid());
	}

	@NotNull
	@Override
	public ImmutableList<String> reduceImport(@NotNull Import node, @NotNull Maybe<ImmutableList<String>> defaultBinding, @NotNull ImmutableList<ImmutableList<String>> namedImports) {
		return ImmutableList.from(node.getModuleSpecifier());
	}

	@NotNull
	@Override
	public ImmutableList<String> reduceImportNamespace(@NotNull ImportNamespace node, @NotNull Maybe<ImmutableList<String>> defaultBinding, @NotNull ImmutableList<String> namespaceBinding) {
		return ImmutableList.from(node.getModuleSpecifier());
	}

	@NotNull
	@Override
	public ImmutableList<String> reduceExportFrom(@NotNull ExportFrom node, @NotNull ImmutableList<ImmutableList<String>> namedExports) {
		Maybe<String> specifier = node.getModuleSpecifier();
		if (specifier.isJust())
			return ImmutableList.from(specifier.just());
		else
			return ImmutableList.nil();
	}

	@NotNull
	@Override
	public ImmutableList<String> reduceExportAllFrom(@NotNull ExportAllFrom node) {
		return ImmutableList.from(node.getModuleSpecifier());
	}

	private static final class ImportCollectionMonoid implements Monoid<ImmutableList<String>> {
		@NotNull
		@Override
		public ImmutableList<String> identity() {
			return ImmutableList.nil();
		}

		@NotNull
		@Override
		public ImmutableList<String> append(ImmutableList<String> a, ImmutableList<String> b) {
			return a.append(b);
		}
	}
}
