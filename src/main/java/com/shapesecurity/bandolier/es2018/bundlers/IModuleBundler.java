package com.shapesecurity.bandolier.es2018.bundlers;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2018.ast.Module;
import com.shapesecurity.shift.es2018.ast.Script;
import com.shapesecurity.shift.es2018.parser.EarlyError;

import javax.annotation.Nonnull;
import java.util.Map;

public interface IModuleBundler {
	@Nonnull
	Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, Module> modules) throws Exception;

	@Nonnull
	Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, Module> modules) throws Exception;
}
