package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.ast.Module;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface IModuleBundler {
	@NotNull
	Script bundleEntrypoint(String entry, Map<String, Module> modules) throws Exception;
}
