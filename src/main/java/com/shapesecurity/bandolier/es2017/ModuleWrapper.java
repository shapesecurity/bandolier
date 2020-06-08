package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2017.ast.Directive;
import com.shapesecurity.shift.es2017.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.es2017.ast.Module;

import javax.annotation.Nonnull;

public class ModuleWrapper extends Module {

	public ModuleWrapper(@Nonnull ImmutableList<Directive> directives, @Nonnull ImmutableList<ImportDeclarationExportDeclarationStatement> items) {
		super(directives, items);
	}

	public ModuleWrapper(@Nonnull Module module) {
		super(module.directives, module.items);
	}

	@Override
	public boolean equals(Object object) {
		return this == object;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@Nonnull
	public boolean contentEquals(Object object) {
		return object instanceof ModuleWrapper && this.directives.equals(((ModuleWrapper)object).directives) && this.items.equals(((ModuleWrapper)object).items);
	}
}
