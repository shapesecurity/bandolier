package com.shapesecurity.bandolier.es2017.bundlers;

import com.shapesecurity.bandolier.es2017.transformations.ImportExportConnector;
import com.shapesecurity.bandolier.es2017.transformations.VariableCollisionResolver;
import com.shapesecurity.bandolier.es2017.transformations.VariableNameGenerator;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Tuple3;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.EarlyErrorChecker;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

// merges modules by resolving variable collision, scheduling, and merging modules.
public class PiercedModuleBundler implements IModuleBundler {

	@Override
	public @NotNull Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, Module> modules) {
		HashTable<String, Module> newModules = HashTable.emptyUsingEquality();
		for (Map.Entry<String, Module> mapEntry : modules.entrySet()) {
			newModules = newModules.put(mapEntry.getKey(), mapEntry.getValue());
		}
		Tuple3<HashTable<String, Module>, VariableNameGenerator, HashTable<Module, HashTable<String, String>>> tuple = VariableCollisionResolver.resolveCollisions(newModules);
		Script script = ImportExportConnector.combineModules(options, tuple.a.get(entry).fromJust(), tuple.b, tuple.a, tuple.c);
		return script;
	}



	@Override
	public @NotNull Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, Module> modules) {
		return Pair.of(bundleEntrypoint(options, entry, modules), ImmutableList.from(modules.values().stream().map(EarlyErrorChecker::validate).collect(Collectors.toList())).foldLeft(ImmutableList::append, ImmutableList.empty()));
	}
}
