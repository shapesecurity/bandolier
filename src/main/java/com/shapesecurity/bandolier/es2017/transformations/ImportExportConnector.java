package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Tuple3;
import com.shapesecurity.functional.data.*;
import com.shapesecurity.shift.es2017.ast.*;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.ast.operators.BinaryOperator;
import com.shapesecurity.shift.es2017.ast.operators.UnaryOperator;
import com.shapesecurity.shift.es2017.path.BranchGetter;
import com.shapesecurity.shift.es2017.path.BranchIterator;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.reducer.Reducer;
import com.shapesecurity.shift.es2017.reducer.WrappedReducer;
import com.shapesecurity.shift.es2017.scope.*;

import javax.annotation.Nonnull;
import java.util.*;

// connects a list of modules via import/export statements.
public class ImportExportConnector {

	private ImportExportConnector() {

	}

	/**
	 *
	 * Recursively extracts bindings. Used to resolve exports of object/array bindings.
	 *
	 * @param localExported current module-local exports
	 * @param export current export declaration
	 * @param binding current binding
	 * @param lookup global scope lookup
	 * @return a new module-local export list
	 */
	private static HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> handleBinding(@Nonnull HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> localExported, @Nonnull ExportDeclaration export, @Nonnull BindingBindingWithDefault binding, @Nonnull ScopeLookup lookup, @Nonnull HashTable<String, String> exportNames) {
		if (binding instanceof BindingIdentifier) {
			Variable variable = lookup.findVariableDeclaredBy((BindingIdentifier) binding).fromJust();
			localExported = localExported.put(exportNames.get(variable.name).orJust(variable.name), HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of(export, variable)));
		} else if (binding instanceof ObjectBinding) {
			for (BindingProperty property : ((ObjectBinding) binding).properties) {
				if (property instanceof BindingPropertyIdentifier) {
					Variable variable = lookup.findVariableDeclaredBy(((BindingPropertyIdentifier) property).binding).fromJust();
					localExported = localExported.put(exportNames.get(variable.name).orJust(variable.name), HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of(export, variable)));
				} else if (property instanceof BindingPropertyProperty) {
					localExported = handleBinding(localExported, export, ((BindingPropertyProperty) property).binding, lookup, exportNames);
				}
			}
		} else if (binding instanceof ArrayBinding) {
			for (Maybe<BindingBindingWithDefault> maybeBinding : ((ArrayBinding) binding).elements) {
				if (maybeBinding.isNothing()) {
					continue;
				}
				BindingBindingWithDefault subBinding = maybeBinding.fromJust();
				if (subBinding instanceof BindingPropertyIdentifier) {
					Variable variable = lookup.findVariableDeclaredBy(((BindingPropertyIdentifier) subBinding).binding).fromJust();
					localExported = localExported.put(exportNames.get(variable.name).orJust(variable.name), HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of(export, variable)));
				} else if (subBinding instanceof BindingWithDefault) {
					localExported = handleBinding(localExported, export, ((BindingWithDefault) subBinding).binding, lookup, exportNames);
				}
			}
		}
		return localExported;
	}

	/**
	 *
	 * Recursively extracts binding identifiers from a binding.
	 *
	 * @param binding current binding
	 * @param bindings current bindings
	 * @return a new module-local export list
	 */
	private static ImmutableSet<String> extractBindings(@Nonnull BindingBindingWithDefault binding, @Nonnull ImmutableSet<String> bindings) {
		if (binding instanceof BindingIdentifier) {
			bindings = bindings.put(((BindingIdentifier) binding).name);
		} else if (binding instanceof ObjectBinding) {
			for (BindingProperty property : ((ObjectBinding) binding).properties) {
				if (property instanceof BindingPropertyIdentifier) {
					bindings = bindings.put(((BindingPropertyIdentifier) property).binding.name);
				} else if (property instanceof BindingPropertyProperty) {
					bindings = extractBindings(((BindingPropertyProperty) property).binding, bindings);
				}
			}
		} else if (binding instanceof ArrayBinding) {
			for (Maybe<BindingBindingWithDefault> maybeBinding : ((ArrayBinding) binding).elements) {
				if (maybeBinding.isNothing()) {
					continue;
				}
				BindingBindingWithDefault subBinding = maybeBinding.fromJust();
				if (subBinding instanceof BindingPropertyIdentifier) {
					bindings = bindings.put(((BindingPropertyIdentifier) subBinding).binding.name);
				} else if (subBinding instanceof BindingWithDefault) {
					bindings = extractBindings(((BindingWithDefault) subBinding).binding, bindings);
				}
			}
		}
		return bindings;
	}

	/**
	 * Resolves an import to a globally scoped variable.
	 *
	 * @param exported all exports
	 * @param module current module
	 * @param name import specifier
	 * @return variable of export, safe to reference
	 */
	private static Maybe<Variable> resolveImport(@Nonnull HashTable<Module, HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>> exported,
										  @Nonnull Module module,
										  @Nonnull String name) {
		Maybe<HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> table = exported.get(module).fromJust().get(name);
		if (table.isNothing()) {
			return Maybe.empty();
		}
		return table.map(map -> map.iterator().next().right.right);
	}

	/**
	 * This method is responsible for resolving proxy export statements such as:
	 *```
	 * 		export * from './module.js'
	 *```
	 * or
	 *```
	 * 		export {myVar} from './module.js'
	 *```
	 * by merging the `extracted` and `renamingMap` tables as appropriate.
	 *
	 * @param module current working module
	 * @param exported global export mapping
	 * @param globalExportsDesired a map of all global proxy exports, requester to source
	 * @param specificExportsDesired a map of all specific proxy exports, requester to name to source
	 * @param updated a current set of modules which have been invalidated in the current iteration of all or updated modules. this will be iterated on the next round.
	 * @param renamingMap the current renaming map of original modules, with modification applied via the merging in this method. used to resolve specifiers in differing modules.
	 * @return a tuple of a new exported table, updated set, and new renaming map, respectively.
	 */
	private static Tuple3<HashTable<Module, HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>>, ImmutableSet<Module>, HashTable<Module, HashTable<String, String>>> resolveProxyExports(
			@Nonnull Module module,
			@Nonnull HashTable<Module, HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>> exported,
			@Nonnull MultiHashTable<Module, Module> globalExportsDesired,
			@Nonnull HashTable<Module, MultiHashTable<String, Pair<Module, Maybe<String>>>> specificExportsDesired,
			@Nonnull ImmutableSet<Module> updated,
			@Nonnull HashTable<Module, HashTable<String, String>> renamingMap) {
		HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> moduleExported = exported.get(module).fromJust();
		ImmutableList<Module> wantAll = globalExportsDesired.get(module);
		HashTable<String, String> ourRenamingMap = renamingMap.get(module).fromJust();
		for (Module wantingModule : wantAll) {
			HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> wantingExported = exported.get(wantingModule).orJust(HashTable.emptyUsingEquality());

			boolean update = false;
			for (Pair<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> pair : moduleExported) {
				if (!pair.left.equals("default")) {
					Maybe<HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> wantingExportedLocal = wantingExported.get(pair.left);
					if (wantingExportedLocal.isNothing()) {
						wantingExported = wantingExported.put(pair.left, pair.right.foldLeft((acc, subPair) -> acc.put(Maybe.of(module), subPair.right), HashTable.emptyUsingEquality()));
						update = true;
					} else if (!wantingExportedLocal.fromJust().containsKey(Maybe.of(module)) && !wantingExportedLocal.fromJust().containsKey(Maybe.empty())) {
						update = wantingExportedLocal.fromJust().length > 0;
						wantingExported = wantingExported.put(pair.left, HashTable.emptyUsingEquality()); // nothing will ever export, ambiguous
					}
				}
			}
			if (update) {
				exported = exported.put(wantingModule, wantingExported);
				updated = updated.put(wantingModule);
				renamingMap = renamingMap.put(wantingModule, renamingMap.get(wantingModule).fromJust().merge(ourRenamingMap));
			}
		}
		MultiHashTable<String, Pair<Module, Maybe<String>>> subExports = specificExportsDesired.get(module).orJust(MultiHashTable.emptyUsingEquality());
		for (Pair<String, ImmutableList<Pair<Module, Maybe<String>>>> exports : subExports.entries()) {
			Maybe<HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> maybeLocalExported = moduleExported.get(exports.left);
			if (maybeLocalExported.isNothing()) {
				continue; // should become available in later iterations
			}
			HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>> localExported = maybeLocalExported.fromJust();
			if (localExported.length == 0) {
				throw new RuntimeException("Ambiguous proxy export: '" + exports.left + "'");
			}
			// the following search is not ideal, but adding an inverse map in this case, would probably be slower.
			Maybe<Pair<String, String>> ourMapping = ourRenamingMap.find(pair -> pair.right.equals(exports.left));
			for (Pair<Module, Maybe<String>> exportInfo : exports.right) {
				HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> wantingExported = exported.get(exportInfo.left).orJust(HashTable.emptyUsingEquality());
				String exportName = exportInfo.right.orJust(exports.left);
				if (!wantingExported.containsKey(exportName)) {
					exported = exported.put(exportInfo.left, wantingExported.put(exportName, wantingExported.get(exportName).orJust(HashTable.emptyUsingEquality()).merge(localExported)));
					updated = updated.put(exportInfo.left);
					if (ourMapping.isJust()) {
						renamingMap = renamingMap.put(exportInfo.left, renamingMap.get(exportInfo.left).fromJust().put(ourMapping.fromJust().left, ourMapping.fromJust().right));
					}
				}
			}
		}
		return new Tuple3<>(exported, updated, renamingMap);
	}

	private enum ModuleState {
		INSTANTIATING, INSTANTIATED, ERROR
	}

	/**
	 * Schedules each module according to the proper execution order.
	 *
	 * @param module entrypoint module to schedule. (always executed last)
	 * @param schedule mutable list for schedule to be added onto
	 * @param scheduleSet already scheduled modules
	 * @param dependingOn mapping of module dependencies
	 * @param index internal counter, always initialise to 0
	 * @param stack internal stack for scheduling, always initialise to empty list
	 * @param stackSet internal set for scheduling, always initialise to empty set
	 * @param moduleAncestorIndex  internal counter, always initialise to 0
	 * @return an index and state of a module scheduled. the index is used internally, and is to be discarded. success can be determined by checking the module state is `ModuleState.INSTANTIATED`
	 */
	private static Pair<Integer, ModuleState> scheduleModule(@Nonnull Module module, @Nonnull LinkedList<Module> schedule, @Nonnull HashSet<Module> scheduleSet, @Nonnull HashMap<Module, LinkedList<Module>> dependingOn, int index, @Nonnull LinkedList<Module> stack, @Nonnull HashSet<Module> stackSet, @Nonnull HashMap<Module, Integer> moduleAncestorIndex) {
		if (scheduleSet.contains(module)) {
			return Pair.of(index, ModuleState.INSTANTIATED);
		} else if (stackSet.contains(module)) {
			return Pair.of(index, ModuleState.INSTANTIATING);
		}
		stack.push(module);
		stackSet.add(module);
		moduleAncestorIndex.put(module, index);
		int originalIndex = index;
		++index;
		LinkedList<Module> children = dependingOn.get(module);
		if (children != null) {
			Iterator<Module> subModules = children.descendingIterator();
			while (subModules.hasNext()) {
				Module subModule = subModules.next();
				Pair<Integer, ModuleState> pair = scheduleModule(subModule, schedule, scheduleSet, dependingOn, index, stack, stackSet, moduleAncestorIndex);
				index = pair.left;
				if (pair.right == ModuleState.INSTANTIATING) {
					if (!stackSet.contains(subModule)) {
						return Pair.of(-1, ModuleState.ERROR);
					}
					int ancestorIndex = moduleAncestorIndex.get(module);
					int subAncestorIndex = moduleAncestorIndex.get(subModule);
					if (subAncestorIndex < ancestorIndex) {
						ancestorIndex = subAncestorIndex;
					}
					moduleAncestorIndex.put(module, ancestorIndex);
				} else if (pair.right == ModuleState.ERROR) {
					return Pair.of(-1, ModuleState.ERROR);
				}
			}
		}
		int ancestorIndex = moduleAncestorIndex.get(module);
		if (ancestorIndex > originalIndex || stack.stream().filter(subModule -> subModule.equals(module)).count() != 1) {
			return Pair.of(-1, ModuleState.ERROR);
		}
		if (ancestorIndex == originalIndex) {
			Module subModule;
			do {
				if (stack.size() == 0) {
					return Pair.of(-1, ModuleState.ERROR);
				}
				subModule = stack.pop();
				scheduleSet.add(subModule);
				schedule.add(subModule);
			} while (!subModule.equals(module));
		}
		return Pair.of(index, ModuleState.INSTANTIATED);
	}

	private static ImmutableSet<Module> recurRecursiveDependencyChecker(@Nonnull Module module, @Nonnull HashSet<Module> nonSelfReferentialModules, @Nonnull HashMap<Module, LinkedList<Module>> dependingOn, @Nonnull ImmutableSet<Module> recurring) {
		if (recurring.contains(module)) {
			return ImmutableSet.<Module>emptyUsingIdentity().put(module);
		}
		LinkedList<Module> dependingOnLocal = dependingOn.get(module);
		if (dependingOnLocal == null) {
			nonSelfReferentialModules.add(module);
			return ImmutableSet.emptyUsingIdentity();
		}
		ImmutableSet<Module> dependencies = ImmutableList.from(dependingOnLocal).uniqByIdentity();
		ImmutableSet<Module> subDependencies = dependencies;
		ImmutableSet<Module> subRecurring = recurring.put(module);
		for (Module subModule : dependencies) {
			subDependencies = subDependencies.union(recurRecursiveDependencyChecker(subModule, nonSelfReferentialModules, dependingOn, subRecurring));
		}

		for (Module subDependency : subDependencies) {
			if (subRecurring.contains(subDependency)) {
				return subDependencies;
			}
		}
		nonSelfReferentialModules.add(module);
		return subDependencies;
	}

	/**
	 * combines a set of modules, given a VariableNameGenerator guaranteed not to have collisions or errors, and a map of previous bound renaming, to satisfy importing.
	 *
	 * @param entry entrypoint module
	 * @param nameGenerator guaranteed not to produce collisions, in any module.
	 * @param modules mapping of path to modules for combination
	 * @param originalRenamingMap a per-module renaming map that has been applied from the original modules. used to satisfy cross-module failures due to importing.
	 * @return a complete script
	 */
	public static Script combineModules(@Nonnull BundlerOptions options, @Nonnull Module entry, @Nonnull VariableNameGenerator nameGenerator, @Nonnull HashTable<String, Module> modules, @Nonnull HashTable<Module, HashTable<String, String>> originalRenamingMap) {
		// lookups per module
		HashTable<Module, GlobalScope> globalScopes = modules.foldLeft((acc, pair) -> acc.put(pair.right, ScopeAnalyzer.analyze(pair.right)), HashTable.emptyUsingEquality());
		HashTable<Module, ScopeLookup> lookups = modules.foldLeft((acc, pair) -> acc.put(pair.right, new ScopeLookup(globalScopes.get(pair.right).fromJust())), HashTable.emptyUsingEquality());
		// all exported variables, per module, tracks the export the import was delivered from.
		HashTable<Module, HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>> exported = HashTable.emptyUsingIdentity();
		// namespace proxy export requests
		MultiHashTable<Module, Module> globalExportsDesired = MultiHashTable.emptyUsingIdentity();
		// named proxy export requests
		HashTable<Module, MultiHashTable<String, Pair<Module, Maybe<String>>>> specificExportsDesired = HashTable.emptyUsingIdentity();
		// current renaming map, to be applied at end
		HashTable<String, String> renamingMap = HashTable.emptyUsingEquality();
		// map of exports to names for default exports.
		IdentityHashMap<ExportDefault, String> exportExpressionNames = new IdentityHashMap<>();

		HashTable<Module, HashTable<String, String>> invertedOriginalRenamingMaps = originalRenamingMap.map(map -> map.foldLeft((acc, pair) -> acc.put(pair.right, pair.left), HashTable.emptyUsingEquality()));

		// perform initial export extraction and prepare for proxy export resolution
		for (Pair<String, Module> pair : modules) {
			ScopeLookup lookup = lookups.get(pair.right).fromJust();
			HashTable<String, String> exportNames = invertedOriginalRenamingMaps.get(pair.right).fromJust();//
			HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> localExported = exported.get(pair.right).orJust(HashTable.emptyUsingEquality());

			for (ImportDeclarationExportDeclarationStatement item : pair.right.items) {
				if (item instanceof ExportAllFrom) {
					Module from = modules.get(((ExportAllFrom) item).moduleSpecifier).fromJust();
					globalExportsDesired = globalExportsDesired.put(from, pair.right);
				} else if (item instanceof ExportFrom) {
					Module from = modules.get(((ExportFrom) item).moduleSpecifier).fromJust();
					specificExportsDesired = ((ExportFrom) item).namedExports
							.foldLeft((acc, name) ->
									acc.put(from, acc.get(from).orJust(MultiHashTable.emptyUsingEquality()).put(name.name, Pair.of(pair.right, name.exportedName)))
							, specificExportsDesired);
				} else if (item instanceof ExportLocals) {
					for (ExportLocalSpecifier specifier : ((ExportLocals) item).namedExports) {
						Variable referencedVariable = lookup.findVariableReferencedBy(specifier.name);
						String name = specifier.exportedName.orJust(specifier.name.name);
						name = exportNames.get(name).orJust(name);
						localExported = localExported.put(name, HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, referencedVariable)));
					}
				} else if (item instanceof Export) {
					FunctionDeclarationClassDeclarationVariableDeclaration declaration = ((Export) item).declaration;
					if (declaration instanceof FunctionDeclaration) {
						Variable variable = lookup.findVariableDeclaredBy(((FunctionDeclaration) declaration).name).fromJust();
						String name = variable.name;
						name = exportNames.get(name).orJust(name);
						localExported = localExported.put(name, HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, variable)));
					} else if (declaration instanceof ClassDeclaration) {
						Variable variable = lookup.findVariableDeclaredBy(((ClassDeclaration) declaration).name).fromJust();
						String name = variable.name;
						name = exportNames.get(name).orJust(name);
						localExported = localExported.put(name, HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, variable)));
					} else if (declaration instanceof VariableDeclaration) {
						for (VariableDeclarator declarator : ((VariableDeclaration) declaration).declarators) {
							localExported = handleBinding(localExported, (ExportDeclaration) item, declarator.binding, lookup, exportNames);
						}
					} else {
						throw new RuntimeException("Not reached");
					}
				} else if (item instanceof ExportDefault) {
					FunctionDeclarationClassDeclarationExpression body = ((ExportDefault) item).body;
					if (body instanceof FunctionDeclaration) {
						BindingIdentifier identifier = ((FunctionDeclaration) body).name;
						exportExpressionNames.put((ExportDefault) item, identifier.name);
						Variable variable = lookup.findVariableDeclaredBy(identifier).fromJust();
						localExported = localExported.put("default", HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, variable)));
					} else if (body instanceof ClassDeclaration) {
						Variable variable = lookup.findVariableDeclaredBy(((ClassDeclaration) body).name).fromJust();
						exportExpressionNames.put((ExportDefault) item, ((ClassDeclaration) body).name.name);
						localExported = localExported.put("default", HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, variable)));
					} else {
						String name = nameGenerator.next();
						exportExpressionNames.put((ExportDefault) item, name);
						localExported = localExported.put("default", HashTable.<Maybe<Module>, Pair<ExportDeclaration, Variable>>emptyUsingEquality().put(Maybe.empty(), Pair.of((ExportDeclaration) item, new Variable(name, ImmutableList.empty(), ImmutableList.of(new Declaration(new BindingIdentifier(name), Declaration.Kind.Var))))));
					}
				}
			}

			exported = exported.put(pair.right, localExported);
		}

		// name of global object for resolving internal usage of standard JS APIs
		String globalBinding = nameGenerator.next();

		// proxy export resolution
		if (globalExportsDesired.entries().length > 0 || specificExportsDesired.length > 0) {
			// prepare for proxy export resolution
			final MultiHashTable<Module, Module> finalGlobalExportsDesired = globalExportsDesired;
			final HashTable<Module, MultiHashTable<String, Pair<Module, Maybe<String>>>> finalSpecificExportsDesired = specificExportsDesired;
			ImmutableSet<Module> updated = exported.keys().foldAbelian((module, acc) -> finalGlobalExportsDesired.get(module).isNotEmpty() || finalSpecificExportsDesired.get(module).isJust() ? acc.put(module) : acc, ImmutableSet.emptyUsingIdentity());
			boolean someUpdate = false;
			// iterate while we have any potentially erroneous resolved modules.
			while (updated.length() > 0) {
				ImmutableSet<Module> nextUpdated = ImmutableSet.emptyUsingIdentity();
				for (Module module : updated) {
					someUpdate = true;
					// see resolveProxyExports for specifics.
					Tuple3<HashTable<Module, HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>>, ImmutableSet<Module>, HashTable<Module, HashTable<String, String>>> tuple = resolveProxyExports(module, exported, globalExportsDesired, specificExportsDesired, nextUpdated, originalRenamingMap);
					exported = tuple.a;
					nextUpdated = nextUpdated.union(tuple.b);
					originalRenamingMap = tuple.c;
				}
				updated = nextUpdated;
			}
			if (someUpdate) {
				invertedOriginalRenamingMaps = originalRenamingMap.map(map -> map.foldLeft((acc, pair) -> acc.put(pair.right, pair.left), HashTable.emptyUsingEquality()));
			}
			for (Pair<Module, MultiHashTable<String, Pair<Module, Maybe<String>>>> subExports : specificExportsDesired) {
				for (Pair<String, ImmutableList<Pair<Module, Maybe<String>>>> exports : subExports.right.entries()) {
					Maybe<HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> maybeLocalExported = exported.get(subExports.left).fromJust().get(exports.left);
					if (maybeLocalExported.isNothing()) {
						throw new RuntimeException("Unresolved proxy export");
					}
				}
			}

		}

		// build dependency graph
		HashMap<Module, HashSet<Module>> dependedBy = new HashMap<>();
		HashMap<Module, LinkedList<Module>> dependingOn = new HashMap<>();
		for (Pair<String, Module> pair : modules) {
			LinkedList<Module> dependents = null;
			for (ImportDeclarationExportDeclarationStatement item : pair.right.items) {
				if (item instanceof ImportDeclaration || item instanceof ExportFrom || item instanceof ExportAllFrom) {
					if (dependents == null) {
						dependents = dependingOn.computeIfAbsent(pair.right, mod -> new LinkedList<>());
					}
					String moduleSpecifier;
					if (item instanceof ImportDeclaration) {
						moduleSpecifier = ((ImportDeclaration) item).moduleSpecifier;
					} else if (item instanceof ExportFrom) {
						moduleSpecifier = ((ExportFrom) item).moduleSpecifier;
					} else {
						moduleSpecifier = ((ExportAllFrom) item).moduleSpecifier;
					}
					Module from = modules.get(moduleSpecifier).fromJust();
					dependedBy.computeIfAbsent(from, mod -> new HashSet<>()).add(pair.right);
					dependents.add(from);
				}
			}
		}

		// schedule all modules, see https://tc39.github.io/ecma262/#sec-innermoduleinstantiation
		// note that export name pre-binding is accomplished by name hoisting of function-scoped declarations.

		LinkedList<Module> schedule = new LinkedList<>();
		Pair<Integer, ModuleState> result = scheduleModule(entry, schedule, new HashSet<>(), dependingOn, 0, new LinkedList<>(), new HashSet<>(), new HashMap<>());
		if (result.right != ModuleState.INSTANTIATED) {
			throw new RuntimeException("Failed to schedule modules.");
		}

		// ensure we scheduled everything -- modules will not be loaded if not imported somewhere.
		if (schedule.size() != modules.length) {
			throw new RuntimeException("Not all modules were scheduled: " + schedule.size() + " / " + modules.length + ".");
		}

		// reference to export object
		HashTable<Module, String> moduleExportReference = HashTable.emptyUsingIdentity();
		for (Module module : schedule) {
			moduleExportReference = moduleExportReference.put(module, nameGenerator.next());
		}

		// export object property initialisation names
		HashTable<Module, String> moduleExportDefinedName = HashTable.emptyUsingIdentity();
		for (Module module : schedule) {
			moduleExportDefinedName = moduleExportDefinedName.put(module, nameGenerator.next());
		}

		final HashTable<Module, String> finalModuleExportDefinedName = moduleExportDefinedName;

		MultiHashTable<Module, BindingIdentifier> importedBindings = MultiHashTable.emptyUsingIdentity();

		final HashTable<Module, HashTable<String, String>> inverseExports = exported.foldLeft((acc, pair) -> pair.right.foldLeft((subAcc, subPair) -> subPair.right.foldLeft((subSubAcc, subSubPair) -> subSubAcc.put(pair.left, subSubAcc.get(pair.left).orJustLazy(HashTable::emptyUsingEquality).put(subSubPair.right.right.name, subPair.left)), subAcc), acc), HashTable.emptyUsingIdentity());

		HashSet<VariableReference> unresolvedImportReferences = new HashSet<>();

		HashSet<Module> usedExportModules = new HashSet<>();
		usedExportModules.add(entry);

		HashSet<Module> nonSelfDependentModules = new HashSet<>();

		recurRecursiveDependencyChecker(entry, nonSelfDependentModules, dependingOn, ImmutableSet.emptyUsingIdentity());

		if (options.throwOnCircularDependency && nonSelfDependentModules.size() != schedule.size()) {
			throw new RuntimeException("There are " + (schedule.size() - nonSelfDependentModules.size()) + " self-dependent (circular) modules out of " + schedule.size() + ".");
		}

		// process import naming
		for (Module module : schedule) {
			for (ImportDeclarationExportDeclarationStatement item : module.items) {
				if (item instanceof Import) {
					Import importItem = (Import) item;
					Module from = modules.get(importItem.moduleSpecifier).fromJust();
					if (importItem.defaultBinding.isJust()) {
						Maybe<Variable> variable = resolveImport(exported, from, "default");
						if (variable.isNothing()) {
							if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.COMPILE_ERROR) {
								throw new RuntimeException("Unresolved import: \"default\"");
							} else {
								lookups.get(module).fromJust().findVariableDeclaredBy(importItem.defaultBinding.fromJust()).fromJust().references.map(reference -> reference.node).iterator().forEachRemaining(unresolvedImportReferences::add);
								continue;
							}
						}
						String name = importItem.defaultBinding.fromJust().name;
						renamingMap = renamingMap.put(name, variable.fromJust().name);
						importedBindings = importedBindings.put(module, importItem.defaultBinding.fromJust());
					}
					for (ImportSpecifier specifier : importItem.namedImports) {
						importedBindings = importedBindings.put(module, specifier.binding);
						if (specifier.name.isJust()) {
							String specifierName = specifier.name.fromJust();
							Maybe<Variable> variable = resolveImport(exported, from, specifierName);
							if (variable.isNothing()) {
								if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.COMPILE_ERROR) {
									throw new RuntimeException("Unresolved import: \"" + specifierName + "\"");
								} else {
									lookups.get(module).fromJust().findVariableDeclaredBy(specifier.binding).fromJust().references.map(reference -> reference.node).iterator().forEachRemaining(unresolvedImportReferences::add);
									continue;
								}
							}
							renamingMap = renamingMap.put(specifier.binding.name, variable.fromJust().name);
						} else {
							Maybe<Variable> maybeVariable = resolveImport(exported, from, specifier.binding.name);
							if (maybeVariable.isNothing()) {
								if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.COMPILE_ERROR) {
									throw new RuntimeException("Unresolved import: \"" + specifier.binding.name + "\"");
								} else {
									lookups.get(module).fromJust().findVariableDeclaredBy(specifier.binding).fromJust().references.map(reference -> reference.node).iterator().forEachRemaining(unresolvedImportReferences::add);
									continue;
								}
							}
							Variable variable = maybeVariable.fromJust();
							String name = invertedOriginalRenamingMaps.get(module).fromJust().get(variable.name).orJust(variable.name);
							if (!specifier.binding.name.equals(name)) {
								renamingMap = renamingMap.put(specifier.binding.name, variable.name);
							}
						}
					}
				} else if (item instanceof ImportNamespace) {
					ImportNamespace importItem = (ImportNamespace) item;
					Module from = modules.get(importItem.moduleSpecifier).fromJust();
					if (importItem.defaultBinding.isJust()) {
						Maybe<Variable> variable = resolveImport(exported, from, "default");
						if (variable.isNothing()) {
							if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.COMPILE_ERROR) {
								throw new RuntimeException("Unresolved import: \"default\"");
							} else {
								lookups.get(module).fromJust().findVariableDeclaredBy(importItem.defaultBinding.fromJust()).fromJust().references.map(reference -> reference.node).iterator().forEachRemaining(unresolvedImportReferences::add);
								continue;
							}
						}
						String name = importItem.defaultBinding.fromJust().name;
						renamingMap = renamingMap.put(name, variable.fromJust().name);
						importedBindings = importedBindings.put(module, importItem.defaultBinding.fromJust());
					}
					usedExportModules.add(from);
					renamingMap = renamingMap.put(importItem.namespaceBinding.name, moduleExportReference.get(from).fromJust());
				}
			}
		}

		final HashTable<String, Module> finalModuleExportReferenceInverted = moduleExportReference.foldLeft((acc, pair) -> acc.put(pair.right, pair.left), HashTable.emptyUsingEquality());

		final HashTable<Module, ImmutableSet<Variable>> finalImportedBindings = importedBindings.toHashTable((module, list) -> list.map(identifier -> lookups.get(module).fromJust().findVariableDeclaredBy(identifier)).filter(Maybe::isJust).map(Maybe::fromJust).uniqByIdentity());

		final HashTable<VariableReference, Module> finalImportedReferences = finalImportedBindings.foldLeft((acc, pair) -> acc.merge(pair.right.foldAbelian((variable, subAcc) -> variable.references.map(reference -> reference.node).foldLeft((subSubAcc, node) -> subSubAcc.put(node, pair.left), subAcc), HashTable.emptyUsingIdentity())), HashTable.emptyUsingIdentity());

		final HashTable<String, String> finalRenamingMap = renamingMap;

		HashMap<Module, Pair<Module, ImmutableList<ObjectProperty>>> reducedModuleMap = new HashMap<>();

		// prepare module export AST nodes
		for (Module module : schedule) {
			Reducer<Node> reducer = new WrappedReducer<>((originalNode, newNode) -> {
				// throw TypeErrors for illegal assignment
				if (originalNode instanceof AssignmentExpression && (options.dangerLevel != BundlerOptions.DangerLevel.DANGEROUS || options.throwOnImportAssignment)) {
					for (Pair<BranchGetter, Node> pair : new BranchIterator(((AssignmentExpression) originalNode).binding)) {
						if (pair.right instanceof AssignmentTargetIdentifier) {
							String name = ((AssignmentTargetIdentifier) pair.right).name;
							name = finalRenamingMap.get(name).orJust(name);
							if (finalModuleExportReferenceInverted.containsKey(name) || finalImportedReferences.containsKey((VariableReference) pair.right)) {
								if (options.throwOnImportAssignment) {
									throw new RuntimeException("Illegal assignment to import: " + name);
								}
								return new CallExpression(new FunctionExpression(false, false, Maybe.empty(), new FormalParameters(ImmutableList.empty(), Maybe.empty()), new FunctionBody(ImmutableList.empty(), ImmutableList.of(
										new ExpressionStatement(((AssignmentExpression) newNode).expression),
										new ThrowStatement(new NewExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "TypeError"), ImmutableList.empty()))
								))), ImmutableList.empty());
							}
						}
					}
				} else if (originalNode instanceof VariableReference && unresolvedImportReferences.contains(originalNode)) {
					// handle undefined imports
					if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.DEFAULT_TO_UNDEFINED) {
						return new UnaryExpression(UnaryOperator.Void, new LiteralNumericExpression(0));
					} else if (options.importUnresolvedResolutionStrategy == BundlerOptions.ImportUnresolvedResolutionStrategy.THROW_ON_REFERENCE) {
						return new CallExpression(new FunctionExpression(false, false, Maybe.empty(), new FormalParameters(ImmutableList.empty(), Maybe.empty()),
								new FunctionBody(ImmutableList.empty(), ImmutableList.of(new ThrowStatement(
										new NewExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "ReferenceError"), ImmutableList.of(new LiteralStringExpression(((VariableReference) originalNode).name + " is not defined")))
								)))
						), ImmutableList.empty());
					}
				}
				if (originalNode instanceof ExportDefault) {
					exportExpressionNames.put((ExportDefault) newNode, exportExpressionNames.get(originalNode));
				}
				return newNode;
			}, new VariableRenamingReducer(renamingMap, lookups.get(module), globalScopes.get(module)));
			HashTable<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> localExported = exported.get(module).fromJust();
			Pair<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>>[] localExportEntries = localExported.entries().toArray(new Pair[0]);
			Arrays.sort(localExportEntries, Comparator.comparing(o -> o.left));
			ImmutableList<ObjectProperty> objectProperties = ImmutableList.empty();

			// prepare object properties for export
			for (int i = localExportEntries.length - 1; i >= 0; --i) {
				Pair<String, HashTable<Maybe<Module>, Pair<ExportDeclaration, Variable>>> localExportEntry = localExportEntries[i];
				String propertyName = invertedOriginalRenamingMaps.get(entry).fromJust().get(localExportEntry.left).orJust(localExportEntry.left);
				if (propertyName.equals("*default*")) {
					propertyName = localExportEntry.left;
				}
				Maybe<Pair<ExportDeclaration, Variable>> localSource = localExportEntry.right.get(Maybe.empty());
				if (options.dangerLevel == BundlerOptions.DangerLevel.SAFE && !nonSelfDependentModules.contains(module)) {
					FunctionBody body;
					if (localSource.isJust()) {
						Expression sourceObject = new IdentifierExpression(moduleExportDefinedName.get(module).fromJust());
						usedExportModules.add(module);
						body = new FunctionBody(ImmutableList.empty(), ImmutableList.of(
								new IfStatement(new BinaryExpression(new LiteralStringExpression(propertyName), BinaryOperator.In, sourceObject),
										new ReturnStatement(Maybe.of(new IdentifierExpression(localExportEntry.right.entries().maybeHead().fromJust().right.right.name))),
										Maybe.of(new ThrowStatement(new NewExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "ReferenceError"), ImmutableList.of(new LiteralStringExpression(propertyName + " is not defined"))))))
						));
					} else {
						Pair<Maybe<Module>, Pair<ExportDeclaration, Variable>> remoteSource = localExportEntry.right.entries().maybeHead().fromJust();
						Expression sourceObject = new IdentifierExpression(moduleExportDefinedName.get(remoteSource.left.fromJust()).fromJust());
						usedExportModules.add(remoteSource.left.fromJust());
						body = new FunctionBody(ImmutableList.empty(), ImmutableList.of(
								new IfStatement(new BinaryExpression(new LiteralStringExpression(propertyName), BinaryOperator.In, sourceObject),
										new ReturnStatement(Maybe.of(new StaticMemberExpression(new IdentifierExpression(moduleExportReference.get(remoteSource.left.fromJust()).fromJust()), inverseExports.get(remoteSource.left.fromJust()).fromJust().get(localExportEntry.right.entries().maybeHead().fromJust().right.right.name).fromJust()))),
										Maybe.of(new ThrowStatement(new NewExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "ReferenceError"), ImmutableList.of(new LiteralStringExpression(propertyName + " is not defined"))))))
						));
					}
					objectProperties = objectProperties.cons(new Getter(new StaticPropertyName(propertyName), body));
				} else {
					if (localSource.isJust()) {
						objectProperties = objectProperties.cons(new DataProperty(new StaticPropertyName(propertyName), new IdentifierExpression(localExportEntry.right.entries().maybeHead().fromJust().right.right.name)));
					} else {
						Maybe<Pair<Maybe<Module>, Pair<ExportDeclaration, Variable>>> maybeRemoteSource = localExportEntry.right.entries().maybeHead();
						if (maybeRemoteSource.isJust()) {
							Pair<Maybe<Module>, Pair<ExportDeclaration, Variable>> remoteSource = maybeRemoteSource.fromJust();
							objectProperties = objectProperties.cons(new DataProperty(new StaticPropertyName(propertyName), new IdentifierExpression(exported.get(remoteSource.left.fromJust()).fromJust().get(inverseExports.get(remoteSource.left.fromJust()).fromJust().get(remoteSource.right.right.name).fromJust()).fromJust().entries().maybeHead().fromJust().right.right.name)));
						} // else omitted due to ambiguity
					}
				}
			}
			reducedModuleMap.put(module, Pair.of((Module) Director.reduceModule(reducer, module), objectProperties));
		}

		// replace all imports/exports as appropriate
		ImmutableList<Statement> statements = ImmutableList.empty();
		for (Module module : schedule) {
			String exportName = moduleExportReference.get(module).fromJust();
			// safety reduced module, and list of exported properties
			Pair<Module, ImmutableList<ObjectProperty>> reducedModulePair = reducedModuleMap.get(module);
			ImmutableList<Statement> finalAppend = ImmutableList.empty();
			if (usedExportModules.contains(module)) {
				// declare export object if safe
				if (options.dangerLevel == BundlerOptions.DangerLevel.SAFE) {

					if (!nonSelfDependentModules.contains(module)) { // TDZ required
						statements = statements.cons(new VariableDeclarationStatement(new VariableDeclaration(
								VariableDeclarationKind.Var,
								ImmutableList.of(new VariableDeclarator(new BindingIdentifier(exportName), Maybe.of(new ObjectExpression(reducedModulePair.right.cons(new DataProperty(new StaticPropertyName("__proto__"), new LiteralNullExpression()))))))
						)));

						// set toStringTag
						statements = statements.cons(new IfStatement(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Symbol"), new ExpressionStatement(new CallExpression(new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Object"), "defineProperty"), ImmutableList.of(new IdentifierExpression(exportName), new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Symbol"), "toStringTag"), new ObjectExpression(ImmutableList.of(new DataProperty(new StaticPropertyName("value"), new LiteralStringExpression("Module"))))))), Maybe.empty()));

						// freeze export object
						statements = statements.cons(new ExpressionStatement(new AssignmentExpression(new AssignmentTargetIdentifier(exportName), new CallExpression(new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Object"), "freeze"), ImmutableList.of(new IdentifierExpression(exportName))))));

						// declare export initialisation object
						statements = statements.cons(new VariableDeclarationStatement(new VariableDeclaration(
								VariableDeclarationKind.Var,
								ImmutableList.of(new VariableDeclarator(new BindingIdentifier(moduleExportDefinedName.get(module).fromJust()), Maybe.of(new ObjectExpression(ImmutableList.empty()))))
						)));
					} else {
						finalAppend = finalAppend.cons(new VariableDeclarationStatement(new VariableDeclaration(
								VariableDeclarationKind.Var,
								ImmutableList.of(new VariableDeclarator(new BindingIdentifier(exportName), Maybe.of(new ObjectExpression(reducedModulePair.right.cons(new DataProperty(new StaticPropertyName("__proto__"), new LiteralNullExpression()))))))
						)));

						// set toStringTag
						finalAppend = finalAppend.cons(new IfStatement(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Symbol"), new ExpressionStatement(new CallExpression(new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Object"), "defineProperty"), ImmutableList.of(new IdentifierExpression(exportName), new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Symbol"), "toStringTag"), new ObjectExpression(ImmutableList.of(new DataProperty(new StaticPropertyName("value"), new LiteralStringExpression("Module"))))))), Maybe.empty()));

						// freeze export object
						finalAppend = finalAppend.cons(new ExpressionStatement(new AssignmentExpression(new AssignmentTargetIdentifier(exportName), new CallExpression(new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Object"), "freeze"), ImmutableList.of(new IdentifierExpression(exportName))))));

						finalAppend = finalAppend.reverse();
					}
				} else {
					finalAppend = finalAppend.cons(new VariableDeclarationStatement(new VariableDeclaration(
							VariableDeclarationKind.Var,
							ImmutableList.of(new VariableDeclarator(new BindingIdentifier(exportName), Maybe.of(new ObjectExpression(reducedModulePair.right.cons(new DataProperty(new StaticPropertyName("__proto__"), new LiteralNullExpression()))))))
					)));

					// freeze export object
					if (options.dangerLevel == BundlerOptions.DangerLevel.BALANCED) {
						finalAppend = finalAppend.cons(new ExpressionStatement(new AssignmentExpression(new AssignmentTargetIdentifier(exportName), new CallExpression(new StaticMemberExpression(new StaticMemberExpression(new IdentifierExpression(globalBinding), "Object"), "freeze"), ImmutableList.of(new IdentifierExpression(exportName))))));
						finalAppend = finalAppend.reverse();
					}
				}
			}


			String defaultRenamed = originalRenamingMap.get(module).fromJust().get("*default*").orJust("");

			final HashTable<String, String> invertedOriginalRenamingMap = invertedOriginalRenamingMaps.get(module).fromJust().remove(defaultRenamed);

			// output the module's contents, replacing import/export statements as needed
			HashTable<BindingIdentifier, Variable> allBindings = finalImportedBindings.get(module).orJust(ImmutableSet.emptyUsingIdentity()).foldAbelian((variable, acc) -> variable.declarations.foldLeft((subAcc, declaration) -> subAcc.put(declaration.node, variable), acc), HashTable.emptyUsingIdentity());
			ImmutableSet<VariableReference> declaredReferences = ImmutableSet.emptyUsingIdentity();
			for (ImportDeclarationExportDeclarationStatement item : reducedModulePair.left.items) {
				for (Pair<BranchGetter, Node> pair : new BranchIterator(item)) {
					if (pair.right instanceof BindingIdentifier) {
						Maybe<Variable> declared = allBindings.get((BindingIdentifier) pair.right);
						if (declared.isNothing()) {
							continue;
						}
						declaredReferences = declaredReferences.putAll(declared.fromJust().references.map(reference -> reference.node));
					}
				}
				ImmutableList<Statement> toAppend = ImmutableList.empty();
				if (item instanceof ImportDeclaration || item instanceof ExportDeclaration) {
					ImmutableList<String> names = ImmutableList.empty();
					if (item instanceof Export) {
						FunctionDeclarationClassDeclarationVariableDeclaration declaration = ((Export) item).declaration;
						if (declaration instanceof FunctionDeclaration || declaration instanceof ClassDeclaration) {
							toAppend = toAppend.cons((Statement) declaration);
							String name;
							if (declaration instanceof FunctionDeclaration) {
								name = ((FunctionDeclaration) declaration).name.name;
							} else {
								name = ((ClassDeclaration) declaration).name.name;
							}
							names = names.cons(name);
						} else if (declaration instanceof VariableDeclaration) {
							toAppend = toAppend.cons(new VariableDeclarationStatement((VariableDeclaration) declaration));
							ImmutableList<String> newNames = ((VariableDeclaration) declaration).declarators.foldLeft((acc, declarator) -> extractBindings(declarator.binding, ImmutableSet.emptyUsingEquality()).foldAbelian((binding, subAcc) -> acc.cons(binding), acc), ImmutableList.empty());
							names = newNames.foldLeft(ImmutableList::cons, names);
						} else {
							throw new RuntimeException("Not reached");
						}
					} else if (item instanceof ExportDefault) {
						ExportDefault exportDefault = (ExportDefault) item;
						FunctionDeclarationClassDeclarationExpression body = exportDefault.body;
						names = names.cons("default");
						if (body instanceof FunctionDeclaration) {
							FunctionDeclaration declaration = (FunctionDeclaration) body;
							String name = declaration.name.name;
							if (invertedOriginalRenamingMaps.get(module).fromJust().get(name).orJust("").equals("*default*")) {
								name = "default";
							}
							Expression expressionBody = new StaticMemberExpression(new ObjectExpression(ImmutableList.of(new DataProperty(new StaticPropertyName(name), new FunctionExpression(declaration.isAsync, declaration.isGenerator, Maybe.empty(), declaration.params, declaration.body)))), name);
							statements = statements.append(ImmutableList.of(new VariableDeclarationStatement(new VariableDeclaration(VariableDeclarationKind.Var, ImmutableList.of(new VariableDeclarator(new BindingIdentifier(exportExpressionNames.get(exportDefault)), Maybe.of(expressionBody)))))));
							continue;
						} else if (body instanceof ClassDeclaration) {
							toAppend = toAppend.cons(((Statement) body));
						} else {
							Expression expressionBody = (Expression) body;
							if (body instanceof FunctionExpression) {
								expressionBody = new StaticMemberExpression(new ObjectExpression(ImmutableList.of(new DataProperty(new StaticPropertyName("default"), expressionBody))), "default");
							}
							toAppend = toAppend.cons((new VariableDeclarationStatement(new VariableDeclaration(VariableDeclarationKind.Var, ImmutableList.of(new VariableDeclarator(new BindingIdentifier(exportExpressionNames.get(exportDefault)), Maybe.of(expressionBody)))))));
						}
					} else if (item instanceof ExportLocals) {
						names = ((ExportLocals) item).namedExports.foldLeft((acc, specifier) -> acc.cons(specifier.exportedName.orJust(specifier.name.name)), names);
					} else if (item instanceof ExportFrom) {
						names = ((ExportFrom) item).namedExports.foldLeft((acc, specifier) -> acc.cons(specifier.exportedName.orJust(specifier.name)), names);
					} else {
						continue; // output nothing
					}
					if (options.dangerLevel == BundlerOptions.DangerLevel.SAFE && usedExportModules.contains(module) && !nonSelfDependentModules.contains(module)) {
						toAppend = names.foldLeft((acc, name) -> acc.cons(new ExpressionStatement(new AssignmentExpression(new StaticMemberAssignmentTarget(new IdentifierExpression(finalModuleExportDefinedName.get(module).fromJust()), invertedOriginalRenamingMap.get(name).orJust(name)), new LiteralNumericExpression(1)))), toAppend);
					}
				} else {
					toAppend = toAppend.cons((Statement) item);
				}
				statements = toAppend.reverse().foldLeft(ImmutableList::cons, statements);
			}
			statements = finalAppend.foldLeft(ImmutableList::cons, statements);
		}

		// IIFE to match module semantics, reverse statements (prepended statements, not appended)
		Script script = new Script(ImmutableList.empty(), ImmutableList.of(
				new ExpressionStatement(new CallExpression(new FunctionExpression(false, false, Maybe.empty(), new FormalParameters(ImmutableList.of(new BindingIdentifier(globalBinding)), Maybe.empty()), new FunctionBody(
						ImmutableList.of(new Directive("use strict")), statements.cons(
								new ReturnStatement(Maybe.of(new IdentifierExpression(moduleExportReference.get(entry).fromJust())))
						).reverse())),
						ImmutableList.of(new ThisExpression())
				))
		));

		return script;
	}

}