package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Tuple3;
import com.shapesecurity.functional.data.*;
import com.shapesecurity.shift.es2017.ast.AssignmentTargetIdentifier;
import com.shapesecurity.shift.es2017.ast.IdentifierExpression;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;

// detects and resolves variable conflicts in a set of modules
public class VariableCollisionResolver {

	private VariableCollisionResolver() {

	}

	public static final class ResolvedResult {
		@Nonnull
		public final HashTable<Module, Module> moduleMap;
		@Nonnull
		public final VariableNameGenerator nameGenerator;
		@Nonnull
		public final HashTable<Module, HashTable<Variable, String>> renamingMap;

		public ResolvedResult(@Nonnull HashTable<Module, Module> moduleMap, @Nonnull VariableNameGenerator nameGenerator, @Nonnull HashTable<Module, HashTable<Variable, String>> renamingMap) {
			this.moduleMap = moduleMap;
			this.nameGenerator = nameGenerator;
			this.renamingMap = renamingMap;
		}
	}

	@Nonnull
	private static HashTable<Module, HashTable<Variable, String>> extractAndRenameVariables(@Nonnull VariableNameGenerator nameGenerator, @Nonnull HashTable<Module, HashTable<Variable, String>> renamingMap, @Nonnull Module module, @Nonnull ImmutableList<Variable> variables) {
		return renamingMap.put(
			module,
			variables.foldLeft(
				(variableAcc, renaming) ->
					variableAcc.put(renaming, nameGenerator.next()),
				renamingMap.get(module).orJust(HashTable.emptyUsingIdentity())
			)
		);
	}

	public static ResolvedResult resolveCollisions(@Nonnull HashTable<String, Module> modules) {
		HashTable<Module, GlobalScope> globalScopes = modules.foldLeft((acc, pair) -> acc.put(pair.right, ScopeAnalyzer.analyze(pair.right)), HashTable.emptyUsingIdentity());
		HashTable<Module, ScopeLookup> scopeLookups = modules.foldLeft((acc, pair) -> acc.put(pair.right, new ScopeLookup(globalScopes.get(pair.right).fromJust())), HashTable.emptyUsingIdentity());
		ImmutableSet<String> allNames = modules.foldLeft((acc, module) -> acc.union(VariableReferenceExtractor.extractAllReferencedVariableNames(globalScopes.get(module.right).fromJust())), ImmutableSet.emptyUsingEquality());

		HashTable<String, ImmutableSet<Pair<Module, ImmutableList<Variable>>>> allDeclaredVariables = modules.foldLeft((acc, module) ->
			VariableDeclarationExtractor.extractAllDeclaredVariables(globalScopes.get(module.right).fromJust()).entries()
				.foldLeft((subAcc, pair) ->
					subAcc.put(pair.left, Pair.of(module.right, pair.right)),
					acc
				),
			MultiHashTable.<String, Pair<Module, ImmutableList<Variable>>>emptyUsingEquality()
		).toHashTable(ImmutableList::uniqByEquality);

		ImmutableSet<Variable> throughVariables = globalScopes.foldLeft((acc, pair) -> acc.union(pair.right.through.foldLeft((subAcc, subPair) -> subAcc.putAll(subPair.right.map(reference -> {
			ScopeLookup lookup = scopeLookups.get(pair.left).fromJust();
			return reference.node instanceof IdentifierExpression ?
				lookup.findVariableReferencedBy((IdentifierExpression) reference.node) :
				lookup.findVariableReferencedBy((AssignmentTargetIdentifier) reference.node);
		})), acc)), ImmutableSet.emptyUsingIdentity());

		VariableNameGenerator nameGenerator = new VariableNameGenerator(allNames.union(throughVariables.map(variable -> variable.name)));

		HashTable<Module, HashTable<Variable, String>> renamingMaps =
			allDeclaredVariables.entries().filter(pair -> pair.right.length() > 1)
			.map(pair -> pair.right.toList().maybeTail().fromJust())
			.foldLeft((acc, item) ->
				item.foldLeft((subAcc, pair) ->
					extractAndRenameVariables(nameGenerator, subAcc, pair.left, pair.right),
					acc
				),
				throughVariables.foldAbelian((variable, acc) ->
						allDeclaredVariables.get(variable.name).maybe(acc, declarations ->
							acc.merge(
								declarations.foldAbelian(
									(pair, subAcc) ->
										extractAndRenameVariables(nameGenerator, subAcc, pair.left, pair.right),
										acc
								)
							)
						),
					HashTable.emptyUsingIdentity()
				)
			);

		// missing modules has no renaming to do
		ImmutableSet<Module> missingModules = modules.entries().map(pair -> pair.right).uniqByIdentity();
		HashTable<Module, Module> finishedModules = HashTable.emptyUsingIdentity();
		for (Pair<Module, HashTable<Variable, String>> modulePair : renamingMaps) {
			Module newModule = (Module) Director.reduceModule(new VariableRenamingReducer(modulePair.right, Maybe.empty(), scopeLookups.get(modulePair.left).fromJust()), modulePair.left);
			finishedModules = finishedModules.put(modulePair.left, newModule);
			missingModules = missingModules.remove(modulePair.left);
		}
		final HashTable<Module, Module> finalFinishedModules = finishedModules;
		renamingMaps = renamingMaps.entries().foldLeft((acc, pair) -> acc.put(finalFinishedModules.get(pair.left).fromJust(), pair.right), HashTable.emptyUsingIdentity());

		for (Module module : missingModules) {
			renamingMaps = renamingMaps.put(module, HashTable.emptyUsingEquality());
			finishedModules = finishedModules.put(module, module);
		}

		return new ResolvedResult(finishedModules, nameGenerator, renamingMaps);
	}

}
