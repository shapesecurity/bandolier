package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.MultiHashTable;
import com.shapesecurity.shift.es2017.ast.*;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.reducer.Director;
import com.shapesecurity.shift.es2017.reducer.MonoidalReducer;
import com.shapesecurity.shift.es2017.scope.Declaration;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.Scope;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

// determines all declared global variables for a module, linked to a scope analysis variable
public class VariableDeclarationExtractor {

	private VariableDeclarationExtractor() {

	}

	public static MultiHashTable<String, Variable> extractAllDeclaredVariables(@Nonnull Scope globalScope) {
		return globalScope.children.foldLeft(
			(acc, scope) -> acc.merge(extractAllDeclaredVariables(scope)),
			ImmutableList.from(globalScope.variables().toArray(new Variable[0]))
				.filter(variable -> variable.declarations.length > 0 && !variable.declarations.exists(declaration -> declaration.kind == Declaration.Kind.Import))
				.foldLeft((acc, variable) -> acc.put(variable.name, variable), MultiHashTable.emptyUsingEquality())
		);
	}
}
