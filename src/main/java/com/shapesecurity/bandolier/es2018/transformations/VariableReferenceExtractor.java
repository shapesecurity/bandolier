package com.shapesecurity.bandolier.es2018.transformations;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.shift.es2018.scope.Scope;
import com.shapesecurity.shift.es2018.scope.Variable;

import javax.annotation.Nonnull;

// determines all referenced variable names for a module,
public class VariableReferenceExtractor {

	private VariableReferenceExtractor() {

	}

	public static ImmutableSet<String> extractAllReferencedVariableNames(@Nonnull Scope globalScope) {
		return ImmutableList.from(globalScope.variables().toArray(new Variable[0])).map(variable -> variable.name).uniqByEquality()
			.union(globalScope.children.flatMap(scope -> extractAllReferencedVariableNames(scope).toList()).uniqByEquality());
	}

}
