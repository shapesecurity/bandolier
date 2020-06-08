package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2017.ast.Directive;
import com.shapesecurity.shift.es2017.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.es2017.ast.Module;

import javax.annotation.Nonnull;

public class ModuleWrapper {

	@Nonnull
	public Module module;

	public ModuleWrapper(@Nonnull ImmutableList<Directive> directives, @Nonnull ImmutableList<ImportDeclarationExportDeclarationStatement> items) {
		this.module = new Module(directives, items);
	}

	public ModuleWrapper(@Nonnull Module module) {
		this.module = module;
	}

	@Nonnull
	public boolean contentEquals(Object object) {
		return object instanceof ModuleWrapper && this.module.equals(((ModuleWrapper) object).module);
	}
}
