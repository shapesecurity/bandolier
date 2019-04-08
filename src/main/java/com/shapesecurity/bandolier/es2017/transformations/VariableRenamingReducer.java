package com.shapesecurity.bandolier.es2017.transformations;

import javax.annotation.Nonnull;

import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2017.ast.*;
import com.shapesecurity.shift.es2017.reducer.ReconstructingReducer;
import com.shapesecurity.shift.es2017.scope.GlobalScope;
import com.shapesecurity.shift.es2017.scope.Reference;
import com.shapesecurity.shift.es2017.scope.Scope;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

// renames all identifiers given some map
public class VariableRenamingReducer extends ReconstructingReducer {

	@Nonnull
	private final HashTable<Variable, String> mapping;
	@Nonnull
	private final Maybe<Variable> defaultVariable;
	@Nonnull
	private final ScopeLookup lookup;

	public VariableRenamingReducer(@Nonnull HashTable<Variable, String> mapping, @Nonnull Maybe<Variable> defaultVariable, @Nonnull ScopeLookup lookup) {
		this.mapping = mapping;
		this.defaultVariable = defaultVariable;
		this.lookup = lookup;
	}

	@Nonnull
	@Override
	public AssignmentTargetIdentifier reduceAssignmentTargetIdentifier(@Nonnull AssignmentTargetIdentifier node) {
		Maybe<String> newName = mapping.get(lookup.findVariableReferencedBy(node));
		return new AssignmentTargetIdentifier(newName.orJust(node.name));
	}

	@Nonnull
	@Override
	public IdentifierExpression reduceIdentifierExpression(@Nonnull IdentifierExpression node) {
		Maybe<String> newName = mapping.get(lookup.findVariableReferencedBy(node));
		return new IdentifierExpression(newName.orJust(node.name));
	}

	@Nonnull
	@Override
	public BindingIdentifier reduceBindingIdentifier(@Nonnull BindingIdentifier node) {
		Maybe<String> newName = defaultVariable.isJust() && node.name.equals("*default*") ?
			Maybe.of(defaultVariable.fromJust().name) :
			lookup.findVariableDeclaredBy(node).flatMap(mapping::get);
		return new BindingIdentifier(newName.orJust(node.name));
	}

	@Nonnull
	@Override
	public ImportDeclarationExportDeclarationStatement reduceImport(
			@Nonnull Import node,
			@Nonnull Maybe<Node> defaultBinding,
			@Nonnull ImmutableList<Node> namedImports) {
		return new Import(defaultBinding.map(x -> (BindingIdentifier) x), namedImports.map(x -> (ImportSpecifier) x).map(importSpecifier -> new ImportSpecifier(importSpecifier.name, importSpecifier.binding)), node.moduleSpecifier);
	}

	@Nonnull
	@Override
	public ImportSpecifier reduceImportSpecifier(
			@Nonnull ImportSpecifier node,
			@Nonnull Node binding) {
		return new ImportSpecifier(node.name, (BindingIdentifier) binding);
	}

	@Nonnull
	@Override
	public ExportFromSpecifier reduceExportFromSpecifier(@Nonnull ExportFromSpecifier node) {
		return new ExportFromSpecifier(node.name, node.exportedName);
	}

	@Nonnull
	@Override
	public ExportLocalSpecifier reduceExportLocalSpecifier(
			@Nonnull ExportLocalSpecifier node,
			@Nonnull Node name) {
		return new ExportLocalSpecifier((IdentifierExpression) name, node.exportedName);
	}

	@Nonnull
	@Override
	public ImportDeclarationExportDeclarationStatement reduceExportAllFrom(@Nonnull ExportAllFrom node) {
		return new ExportAllFrom(node.moduleSpecifier);
	}

}
