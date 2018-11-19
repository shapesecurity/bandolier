package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.MultiHashTable;
import com.shapesecurity.shift.es2017.ast.*;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.reducer.MonoidalReducer;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

// determines all declared global variables for a module, linked to a scope analysis variable
public class GlobalVariableExtractor {

	private GlobalVariableExtractor() {

	}

	public static MultiHashTable<String, Variable> extractAllDeclaredVariables(@Nonnull Program module, @Nonnull GlobalScope globalScope, @Nonnull ScopeLookup lookup) {
		DeclaredVariableReducer reducer = new DeclaredVariableReducer(globalScope, lookup);
		return Director.reduceProgram(reducer, module);
	}

	public static MultiHashTable<String, Variable> extractAllDeclaredVariables(@Nonnull Program module) {
		GlobalScope globalScope = module instanceof Module ? ScopeAnalyzer.analyze((Module) module) : ScopeAnalyzer.analyze((Script) module);
		return extractAllDeclaredVariables(module, globalScope, new ScopeLookup(globalScope));
	}

	private static final class DeclaredVariableReducer extends MonoidalReducer<MultiHashTable<String, Variable>>  {

		@Nonnull
		private final ScopeLookup lookup;
		@Nonnull
		private final GlobalScope globalScope;

		public DeclaredVariableReducer(@Nonnull GlobalScope globalScope, @Nonnull ScopeLookup lookup) {
			super(new MonoidMultiHashTableEqualityMerge<>());
			this.lookup = lookup;
			this.globalScope = globalScope;
		}

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceBindingIdentifier(@Nonnull BindingIdentifier node) {
			if (node.name.equals("*default*")) {
				return this.identity();
			}
			Maybe<Variable> maybeVariable = lookup.findVariableDeclaredBy(node);
			if (maybeVariable.isJust()) {
				return this.identity().put(node.name, maybeVariable.fromJust());
			}
			List<Variable> matchingUndeclaredGlobals = globalScope.variables().stream().filter(variable -> variable.declarations.length == 0 && variable.name.equals(node.name)).collect(Collectors.toList());
			if (matchingUndeclaredGlobals.size() == 1) {
				return this.identity().put(node.name, matchingUndeclaredGlobals.get(0)); // this handles imports which define a variable but not declare it? scope analysis bug?
			} else {
				throw new RuntimeException("Unable to handle rogue binding identifier, " + matchingUndeclaredGlobals.size() + " matching candidates for non-declared definitions. (you are probably missing a module)");
			}
		}

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceImport(
				@Nonnull Import node,
				@Nonnull Maybe<MultiHashTable<String, Variable>> defaultBinding,
				@Nonnull ImmutableList<MultiHashTable<String, Variable>> namedImports) {
			return MultiHashTable.emptyUsingEquality();
		}

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceImportNamespace(
				@Nonnull ImportNamespace node,
				@Nonnull Maybe<MultiHashTable<String, Variable>> defaultBinding,
				@Nonnull MultiHashTable<String, Variable> namespaceBinding) {
			return MultiHashTable.emptyUsingEquality();
		}

		// we don't care about sub function scopes

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceFunctionBody(
				@Nonnull FunctionBody node,
				@Nonnull ImmutableList<MultiHashTable<String, Variable>> directives,
				@Nonnull ImmutableList<MultiHashTable<String, Variable>> statements) {
			return MultiHashTable.emptyUsingEquality();
		}

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceFormalParameters(
				@Nonnull FormalParameters node,
				@Nonnull ImmutableList<MultiHashTable<String, Variable>> items,
				@Nonnull Maybe<MultiHashTable<String, Variable>> rest) {
			return MultiHashTable.emptyUsingEquality();
		}

		@Nonnull
		@Override
		public MultiHashTable<String, Variable> reduceBlock(
				@Nonnull Block node,
				@Nonnull ImmutableList<MultiHashTable<String, Variable>> statements) {
			MultiHashTable<String, Variable> table = fold(statements);
			MultiHashTable<String, Variable> returning = MultiHashTable.emptyUsingEquality();
			// filter
			for (Pair<String, ImmutableList<Variable>> pair : table.entries()) {
				returning = pair.right.foldLeft(((acc, var) -> var.declarations.exists((decl) -> decl.kind.isFunctionScoped) ? acc.put(pair.left, var) : acc), returning);
			}
			return returning;
		}
	}
}
