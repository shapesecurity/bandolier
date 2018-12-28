package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Tuple3;
import com.shapesecurity.functional.data.*;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;

import javax.annotation.Nonnull;

// detects global variable conflicts in a set of modules
public class VariableCollisionResolver {

	private VariableCollisionResolver() {

	}

	public static Tuple3<HashTable<String, Module>, VariableNameGenerator, HashTable<Module, HashTable<String, String>>> resolveCollisions(@Nonnull HashTable<String, Module> modules) {
		HashTable<String, GlobalScope> globalScopes = modules.foldLeft((acc, pair) -> acc.put(pair.left, ScopeAnalyzer.analyze(pair.right)), HashTable.emptyUsingIdentity());
		HashTable<String, ImmutableSet<String>> names = modules.foldLeft((acc, module) -> GlobalVariableExtractor.extractAllDeclaredVariables(module.right, globalScopes.get(module.left).fromJust(), new ScopeLookup(globalScopes.get(module.left).fromJust())).entries().map(pair -> pair.left).foldLeft((subAcc, name) -> subAcc.put(name, module.left), acc), MultiHashTable.<String, String>emptyUsingEquality()).toHashTable(ImmutableList::uniqByEquality);
		HashTable<String, ImmutableSet<String>> throughNames = globalScopes.foldLeft((acc, pair) -> acc.merge(pair.right.through.foldLeft((subAcc, subPair) -> subAcc.put(subPair.left, subAcc.get(subPair.left).orJust(ImmutableSet.emptyUsingEquality()).put(pair.left)), acc)), HashTable.emptyUsingEquality());
		VariableNameGenerator nameGenerator = new VariableNameGenerator(names.keys().union(throughNames.keys()));
		for (Pair<String, ImmutableSet<String>> pair : throughNames) {
			Maybe<ImmutableSet<String>> otherModules = names.get(pair.left);
			if (otherModules.isNothing()) {
				continue;
			}
			names = names.put(pair.left, names.get(pair.left).fromJust().union(pair.right));
		}
		HashTable<String, HashTable<String, String>> renamingMaps = modules.foldLeft((acc, module) -> acc.put(module.left, HashTable.<String, String>emptyUsingEquality().put("*default*", nameGenerator.next())), HashTable.emptyUsingEquality());
		for (Pair<String, ImmutableSet<String>> pair : names) {
			ImmutableSet<String> through = throughNames.get(pair.left).orJust(ImmutableSet.emptyUsingEquality());
			if (pair.right.length() > 1) {
				renamingMaps = pair.right.foldAbelian((str, acc) -> through.contains(str) ? acc : acc.put(str, acc.get(str).fromJust().put(pair.left, nameGenerator.next())), renamingMaps);
			}
		}

		HashTable<Module, HashTable<String, String>> moduleRenamingMaps = HashTable.emptyUsingEquality();
		HashTable<String, Module> finishedModules = HashTable.emptyUsingEquality();
		for (Pair<String, HashTable<String, String>> modulePair : renamingMaps) {
			String path = modulePair.left;
			Module newModule = (Module) Director.reduceModule(new VariableRenamingReducer(modulePair.right, Maybe.empty(), Maybe.empty()), modules.get(path).fromJust());
			moduleRenamingMaps = moduleRenamingMaps.put(newModule, modulePair.right);
			finishedModules = finishedModules.put(path, newModule);
		}
		return new Tuple3<>(finishedModules, nameGenerator, moduleRenamingMaps);
	}

}
