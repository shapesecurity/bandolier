package com.shapesecurity.bandolier;

import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Script;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class ModuleBundler {

	protected final Map<String, Module> modules;

	public ModuleBundler(Map<String, Module> modules) {
		this.modules = modules;
	}

	public abstract @NotNull Script bundleEntrypoint(String entry);
}
