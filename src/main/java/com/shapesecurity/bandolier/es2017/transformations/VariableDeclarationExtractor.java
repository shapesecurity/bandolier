package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.MultiHashTable;
import com.shapesecurity.shift.es2017.scope.Declaration;
import com.shapesecurity.shift.es2017.scope.Scope;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// determines all declared global variables for a module, linked to a scope analysis variable
public class VariableDeclarationExtractor {

	private VariableDeclarationExtractor() {

	}

	public static MultiHashTable<String, Variable> extractAllDeclaredVariables(@Nonnull Scope scope) {
		ImmutableList<Variable> sortedVariables = ImmutableList.from(
			StreamSupport.stream(
				ImmutableList.from(scope.variables().toArray(new Variable[0]))
					.filter(variable -> variable.declarations.length > 0 && !variable.declarations.exists(declaration -> declaration.kind == Declaration.Kind.Import))
					.spliterator(),
				false)
				// Sorting by name is sufficient to be deterministic because a given scope can only have one variable of a given name
				.sorted(Comparator.comparing(v -> v.name)).collect(Collectors.toList())
		);

		return scope.children.foldLeft(
			(acc, child) -> acc.merge(extractAllDeclaredVariables(child)),
			sortedVariables.foldLeft((acc, variable) -> acc.put(variable.name, variable), MultiHashTable.emptyUsingEquality())
		);
	}
}
