package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.bandolier.es2017.ModuleWrapper;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.MultiHashTable;
import com.shapesecurity.shift.es2017.ast.AssignmentTargetIdentifier;
import com.shapesecurity.shift.es2017.ast.IdentifierExpression;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.Reference;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// detects and resolves variable conflicts in a set of modules
public class VariableCollisionResolver {

	private VariableCollisionResolver() {

	}

	public static final class ResolvedResult {
		@Nonnull
		public final HashTable<ModuleWrapper, ModuleWrapper> moduleMap;
		@Nonnull
		public final VariableNameGenerator nameGenerator;
		@Nonnull
		public final HashTable<ModuleWrapper, HashTable<Variable, String>> renamingMap;

		public ResolvedResult(@Nonnull HashTable<ModuleWrapper, ModuleWrapper> moduleMap, @Nonnull VariableNameGenerator nameGenerator, @Nonnull HashTable<ModuleWrapper, HashTable<Variable, String>> renamingMap) {
			this.moduleMap = moduleMap;
			this.nameGenerator = nameGenerator;
			this.renamingMap = renamingMap;
		}
	}

	@Nonnull
	private static HashTable<ModuleWrapper, HashTable<Variable, String>> extractAndRenameVariables(@Nonnull VariableNameGenerator nameGenerator, @Nonnull HashTable<ModuleWrapper, HashTable<Variable, String>> renamingMap, @Nonnull ModuleWrapper module, @Nonnull ImmutableList<Variable> variables) {
		return renamingMap.put(
			module,
			variables.foldLeft(
				(variableAcc, renaming) ->
					variableAcc.put(renaming, nameGenerator.next()),
				renamingMap.get(module).orJust(HashTable.emptyUsingIdentity())
			)
		);
	}

	private static ImmutableList<Variable> throughVariables(ScopeLookup lookup, GlobalScope moduleScope) {
		return orderedEntries(moduleScope.through).map(throughItem -> {
			Reference reference = throughItem.right.head;
			return reference.node instanceof IdentifierExpression ?
				lookup.findVariableReferencedBy((IdentifierExpression) reference.node) :
				lookup.findVariableReferencedBy((AssignmentTargetIdentifier) reference.node);
		});
	}

	private static <K extends Comparable<K>, V> ImmutableList<Pair<K, V>> orderedEntries(@Nonnull HashTable<K, V> table) {
		ArrayList<Pair<K, V>> entries = new ArrayList<>(table.length);
		for (Pair<K, V> entry : table) {
			entries.add(entry);
		}
		entries.sort(Comparator.comparing(p -> p.left));
		return ImmutableList.from(entries);
	}

	public static ResolvedResult resolveCollisions(@Nonnull HashTable<String, ModuleWrapper> modules) {
		HashTable<ModuleWrapper, GlobalScope> globalScopes = modules.foldLeft((acc, pair) -> acc.put(pair.right, ScopeAnalyzer.analyze(pair.right.module)), HashTable.emptyUsingIdentity());
		HashTable<ModuleWrapper, ScopeLookup> scopeLookups = modules.foldLeft((acc, pair) -> acc.put(pair.right, new ScopeLookup(globalScopes.get(pair.right).fromJust())), HashTable.emptyUsingIdentity());
		ImmutableSet<String> allNames = modules.foldLeft((acc, module) -> acc.union(VariableReferenceExtractor.extractAllReferencedVariableNames(globalScopes.get(module.right).fromJust())), ImmutableSet.emptyUsingEquality());
		ImmutableList<ModuleWrapper> sortedModules = ImmutableList.from(StreamSupport.stream(modules.entries().spliterator(), false).sorted(Comparator.comparing(pair1 -> pair1.left)).map(pair -> pair.right).collect(Collectors.toList()));

		HashTable<String, ImmutableList<Pair<ModuleWrapper, ImmutableList<Variable>>>> allDeclaredVariables = sortedModules.foldLeft((acc, module) ->
			VariableDeclarationExtractor.extractAllDeclaredVariables(globalScopes.get(module).fromJust()).entries()
				.foldLeft((subAcc, pair) ->
					subAcc.put(pair.left, Pair.of(
						module,
						pair.right
					)),
					acc
				),
			MultiHashTable.<String, Pair<ModuleWrapper, ImmutableList<Variable>>>emptyUsingEquality()
		).toHashTable(list -> list);

		ImmutableList<Pair<String, ImmutableList<Pair<ModuleWrapper, ImmutableList<Variable>>>>> allDeclaredVariablesEntriesSorted =
			ImmutableList.from(
				StreamSupport.stream(allDeclaredVariables.entries().spliterator(), false)
				.sorted(Comparator.comparing(pair1 -> pair1.left))
				.collect(Collectors.toList())
			);

		ImmutableList<Variable> throughVariables = sortedModules.foldLeft((acc, module) -> acc.append(
			throughVariables(scopeLookups.get(module).fromJust(), globalScopes.get(module).fromJust())
		), ImmutableList.empty());

		VariableNameGenerator nameGenerator = new VariableNameGenerator(allNames.union(throughVariables.map(variable -> variable.name).uniqByEquality()));

		HashTable<ModuleWrapper, HashTable<Variable, String>> renamingMaps =
			allDeclaredVariablesEntriesSorted.filter(pair -> pair.right.length > 1)
			.map(pair -> pair.right.maybeTail().fromJust())
			.foldLeft((acc, item) ->
				item.foldLeft((subAcc, pair) ->
					extractAndRenameVariables(nameGenerator, subAcc, pair.left, pair.right),
					acc
				),
				throughVariables.foldLeft((acc, variable) ->
						allDeclaredVariables.get(variable.name).maybe(acc, declarations ->
							acc.merge(
								declarations.foldLeft(
									(subAcc, pair) ->
										extractAndRenameVariables(nameGenerator, subAcc, pair.left, pair.right),
										acc
								)
							)
						),
					HashTable.emptyUsingIdentity()
				)
			);

		// missing modules has no renaming to do
		ImmutableSet<ModuleWrapper> missingModules = modules.entries().map(pair -> pair.right).uniqByIdentity();
		HashTable<ModuleWrapper, ModuleWrapper> finishedModules = HashTable.emptyUsingIdentity();
		for (Pair<ModuleWrapper, HashTable<Variable, String>> modulePair : renamingMaps) {
			ModuleWrapper newModule = new ModuleWrapper((Module) Director.reduceModule(new VariableRenamingReducer(modulePair.right, Maybe.empty(), scopeLookups.get(modulePair.left).fromJust()), modulePair.left.module));
			finishedModules = finishedModules.put(modulePair.left, newModule);
			missingModules = missingModules.remove(modulePair.left);
		}
		final HashTable<ModuleWrapper, ModuleWrapper> finalFinishedModules = finishedModules;
		renamingMaps = renamingMaps.entries().foldLeft((acc, pair) -> acc.put(finalFinishedModules.get(pair.left).fromJust(), pair.right), HashTable.emptyUsingIdentity());

		for (ModuleWrapper module : missingModules) {
			renamingMaps = renamingMaps.put(module, HashTable.emptyUsingIdentity());
			finishedModules = finishedModules.put(module, module);
		}

		return new ResolvedResult(finishedModules, nameGenerator, renamingMaps);
	}

}
