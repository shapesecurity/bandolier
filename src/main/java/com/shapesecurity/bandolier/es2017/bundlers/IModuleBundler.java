package com.shapesecurity.bandolier.es2017.bundlers;

import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface IModuleBundler {
	@NotNull
	Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, Module> modules) throws Exception;

	@NotNull
	Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, Module> modules) throws Exception;
}
