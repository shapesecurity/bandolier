package com.shapesecurity.bandolier.es2017.bundlers;

<<<<<<< HEAD:src/main/java/com/shapesecurity/bandolier/es2017/bundlers/PiercedModuleBundler.java
import com.shapesecurity.bandolier.es2017.transformations.DeadCodeElimination;
import com.shapesecurity.bandolier.es2017.transformations.ImportExportConnector;
import com.shapesecurity.bandolier.es2017.transformations.VariableCollisionResolver;
import com.shapesecurity.bandolier.es2017.transformations.VariableNameGenerator;
=======
>>>>>>> cb04cdf... DCE:src/main/java/com/shapesecurity/bandolier/bundlers/PiercedModuleBundler.java
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Tuple3;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
<<<<<<< HEAD:src/main/java/com/shapesecurity/bandolier/es2017/bundlers/PiercedModuleBundler.java
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.EarlyErrorChecker;
=======
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.shift.es2016.ast.Module;
import com.shapesecurity.shift.es2016.ast.Script;
import com.shapesecurity.shift.es2016.parser.EarlyError;
import com.shapesecurity.shift.es2016.parser.EarlyErrorChecker;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2016.ast.*;
>>>>>>> cb04cdf... DCE:src/main/java/com/shapesecurity/bandolier/bundlers/PiercedModuleBundler.java
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

// merges modules by resolving variable collision, scheduling, and merging modules.
public class PiercedModuleBundler implements IModuleBundler {

	@Override
	public @NotNull Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, Module> modules) {
		HashTable<String, Module> newModules = HashTable.emptyUsingEquality();
		HashTable<String, ImmutableSet<Pair<String, String>>> removedImports = HashTable.emptyUsingIdentity();
		for (Map.Entry<String, Module> mapEntry : modules.entrySet()) {
			Module module = mapEntry.getValue();
			module = new Module(module.directives, module.items.map(item -> {
				if (options.exportStrategy == BundlerOptions.ExportStrategy.ALL_GLOBALS) {
					if (item instanceof VariableDeclarationStatement) {
						return new Export(((VariableDeclarationStatement) item).declaration);
					} else if (item instanceof FunctionDeclaration) {
						return new Export((FunctionDeclaration) item);
					} else if (item instanceof ClassDeclaration) {
						return new Export((ClassDeclaration) item);
					}
				}
				return item;
			}));
			Pair<ImmutableSet<Pair<String, String>>, Module> deadCodePair = DeadCodeElimination.removeAllUnusedDeclarations(module);
			module = deadCodePair.right;
			removedImports = removedImports.put(mapEntry.getKey(), deadCodePair.left);
			newModules = newModules.put(mapEntry.getKey(), module);

		}
		Tuple3<HashTable<String, Module>, VariableNameGenerator, HashTable<Module, HashTable<String, String>>> tuple = VariableCollisionResolver.resolveCollisions(newModules);
        Pair<Script, String> combinedPair =  ImportExportConnector.combineModules(options, tuple.a.get(entry).fromJust(), tuple.b, tuple.a, tuple.c, removedImports);
        Script combined = combinedPair.left;
        return new Script(ImmutableList.empty(), ImmutableList.of(
				new ExpressionStatement(new CallExpression(
						new FunctionExpression(false, Maybe.empty(), new FormalParameters(ImmutableList.of(new BindingIdentifier(combinedPair.right)), Maybe.empty()),
								new FunctionBody(ImmutableList.of(new Directive("use strict")), combined.statements)
						),
						ImmutableList.of(new ThisExpression())
				))
		));
	}



	@Override
	public @NotNull Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, Module> modules) {
		return Pair.of(bundleEntrypoint(options, entry, modules), ImmutableList.from(modules.values().stream().map(EarlyErrorChecker::validate).collect(Collectors.toList())).foldLeft(ImmutableList::append, ImmutableList.empty()));
	}
}
