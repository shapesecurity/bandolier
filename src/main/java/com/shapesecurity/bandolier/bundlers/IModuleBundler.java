package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.bandolier.BandolierModule;
import com.shapesecurity.shift.ast.Script;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface IModuleBundler {
	@NotNull
	Script bundleEntrypoint(String entry, Map<String, BandolierModule> modules) throws Exception;
}
