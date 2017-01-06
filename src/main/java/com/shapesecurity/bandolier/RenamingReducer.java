package com.shapesecurity.bandolier;

import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.reducer.CloneReducer;
import com.shapesecurity.shift.scope.GlobalScope;
import com.shapesecurity.shift.scope.ScopeLookup;
import com.shapesecurity.shift.scope.Variable;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RenamingReducer extends CloneReducer {

	@NotNull
	private final RenameState state;
	private final Map<Variable, String> renames;
	private final ScopeLookup scopeAnalysis;
	private final GlobalScope globalScope;

	public RenamingReducer(@NotNull RenameState state, ScopeLookup scopeAnalysis, GlobalScope globalScope) {
		this.state = state;
		this.scopeAnalysis = scopeAnalysis;
		this.globalScope = globalScope;
		this.renames = new HashMap<>();
	}

	@Override
	@NotNull
	public IdentifierExpression reduceIdentifierExpression(@NotNull IdentifierExpression node) {
		Variable v = this.scopeAnalysis.findVariableReferencedBy(node);
		if (this.scopeAnalysis.isGlobal(v) || v.declarations.isEmpty()) {
			return node;
		}
		return rename(IdentifierExpression::new, v);
	}

	@Override
	@NotNull
	public BindingIdentifier reduceBindingIdentifier(@NotNull BindingIdentifier node) {
		Variable v = this.scopeAnalysis.findVariableDeclaredBy(node).toNullable();
		if (v == null) {
			v = this.scopeAnalysis.findVariableReferencedBy(node).toNullable();
		}
		assert v != null;
		if (this.scopeAnalysis.isGlobal(v)) {
			return node;
		}
		return rename(BindingIdentifier::new, v);
	}

	private <T> T rename(Function<String, T> ctor, Variable v) {
		if (this.renames.containsKey(v)) {
			return ctor.apply(this.renames.get(v));
		} else {
			String rename;
			do {
				rename = v.name + this.state.next();
			} while (this.globalScope.lookupVariable(rename).isJust());
			this.renames.put(v, rename);
			return ctor.apply(rename);
		}
	}
}
