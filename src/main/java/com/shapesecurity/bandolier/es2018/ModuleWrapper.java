package com.shapesecurity.bandolier.es2018;

import com.shapesecurity.shift.es2018.ast.Module;

import javax.annotation.Nonnull;

public class ModuleWrapper {
	@Nonnull
	public Module module;

	public ModuleWrapper(@Nonnull Module module) {
		this.module = module;
	}
}
