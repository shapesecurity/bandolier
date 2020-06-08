package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.shift.es2017.ast.Module;

import javax.annotation.Nonnull;

public class ModuleWrapper {

	@Nonnull
	public Module module;

	public ModuleWrapper(@Nonnull Module module) {
		this.module = module;
	}

	@Nonnull
	public boolean contentEquals(Object object) {
		return object instanceof ModuleWrapper && this.module.equals(((ModuleWrapper) object).module);
	}
}
