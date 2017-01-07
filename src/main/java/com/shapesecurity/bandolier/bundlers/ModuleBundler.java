package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.bandolier.BandolierModule;
import com.shapesecurity.shift.ast.Script;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class ModuleBundler {

	protected final Map<String, ? extends BandolierModule> modules;

	public ModuleBundler(Map<String, ? extends BandolierModule> modules) {
		this.modules = modules;
	}

	public abstract @NotNull Script bundleEntrypoint(String entry) throws Exception;
}
