package com.shapesecurity.bandolier;

import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Script;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TreeShakingModuleBundler extends ModuleBundler {
	public TreeShakingModuleBundler(Map<String, Module> modules) {
		super(modules);
	}

	@NotNull
	@Override
	public Script bundleEntrypoint(String entry) {
		return null;
	}
}
