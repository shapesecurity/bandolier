package com.shapesecurity.bandolier.es2018.bundlers;

import com.shapesecurity.bandolier.es2018.ModuleWrapper;
import com.shapesecurity.bandolier.es2018.transformations.DeadCodeElimination;
import com.shapesecurity.bandolier.es2018.transformations.ImportExportConnector;
import com.shapesecurity.bandolier.es2018.transformations.VariableCollisionResolver;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2018.ast.BindingIdentifier;
import com.shapesecurity.shift.es2018.ast.CallExpression;
import com.shapesecurity.shift.es2018.ast.ClassDeclaration;
import com.shapesecurity.shift.es2018.ast.Directive;
import com.shapesecurity.shift.es2018.ast.Export;
import com.shapesecurity.shift.es2018.ast.ExpressionStatement;
import com.shapesecurity.shift.es2018.ast.FormalParameters;
import com.shapesecurity.shift.es2018.ast.FunctionBody;
import com.shapesecurity.shift.es2018.ast.FunctionDeclaration;
import com.shapesecurity.shift.es2018.ast.FunctionExpression;
import com.shapesecurity.shift.es2018.ast.Module;
import com.shapesecurity.shift.es2018.ast.Script;
import com.shapesecurity.shift.es2018.ast.ThisExpression;
import com.shapesecurity.shift.es2018.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.es2018.parser.EarlyError;
import com.shapesecurity.shift.es2018.parser.EarlyErrorChecker;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.stream.Collectors;

// merges modules by resolving variable collision, scheduling, and merging modules.
public class PiercedModuleBundler implements IModuleBundler {

	@Override
	@Nonnull
	public Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, Module> modules) {
		HashTable<String, ModuleWrapper> newModules = HashTable.emptyUsingEquality();
		for (Map.Entry<String, Module> mapEntry : modules.entrySet()) {
			Module module = mapEntry.getValue();
			if (options.exportStrategy == BundlerOptions.ExportStrategy.ALL_GLOBALS) {
				module = new Module(module.directives, module.items.map(item -> {
					if (item instanceof VariableDeclarationStatement) {
						return new Export(((VariableDeclarationStatement) item).declaration);
					} else if (item instanceof FunctionDeclaration) {
						return new Export((FunctionDeclaration) item);
					} else if (item instanceof ClassDeclaration) {
						return new Export((ClassDeclaration) item);
					}
					return item;
				}));
			}
			newModules = newModules.put(mapEntry.getKey(), new ModuleWrapper(module));
		}
		VariableCollisionResolver.ResolvedResult result = VariableCollisionResolver.resolveCollisions(newModules);
		HashTable<String, ModuleWrapper> specifierToModule = newModules.map(module -> result.moduleMap.get(module).fromJust());
		Pair<Script, String> scriptAndGlobalParameter = ImportExportConnector.combineModules(options, result.moduleMap.get(newModules.get(entry).fromJust()).fromJust(), result, specifierToModule);
		Script combined = scriptAndGlobalParameter.left;
		combined = DeadCodeElimination.removeAllUnusedDeclarations(combined);
		return new Script(ImmutableList.empty(), ImmutableList.of(
				new ExpressionStatement(new CallExpression(
						new FunctionExpression(false, false, Maybe.empty(), new FormalParameters(ImmutableList.of(new BindingIdentifier(scriptAndGlobalParameter.right)), Maybe.empty()),
								new FunctionBody(ImmutableList.of(new Directive("use strict")), combined.statements)
						),
						ImmutableList.of(new ThisExpression())
				))
		));
	}



	@Override
	@Nonnull
	public Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, Module> modules) {
		return Pair.of(bundleEntrypoint(options, entry, modules), ImmutableList.from(modules.values().stream().map(EarlyErrorChecker::validate).collect(Collectors.toList())).foldLeft(ImmutableList::append, ImmutableList.empty()));
	}
}
