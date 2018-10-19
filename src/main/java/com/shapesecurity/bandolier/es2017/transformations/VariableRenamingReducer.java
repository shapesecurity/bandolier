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

// renames all identifiers given some map
public class VariableRenamingReducer extends ReconstructingReducer {

	@Nonnull
	private final HashTable<String, String> mapping;
	@Nonnull
	private final Maybe<ScopeLookup> lookup;
	@Nonnull
	private final Maybe<GlobalScope> globalScope;

	public VariableRenamingReducer(@Nonnull HashTable<String, String> mapping, @Nonnull Maybe<ScopeLookup> lookup, @Nonnull Maybe<GlobalScope> globalScope) {
		this.mapping = mapping;
		this.lookup = lookup;
		this.globalScope = globalScope;
	}

	@Nonnull
	@Override
	public AssignmentTargetIdentifier reduceAssignmentTargetIdentifier(@Nonnull AssignmentTargetIdentifier node) {
		if (lookup.isJust()) {
			Scope scope = lookup.fromJust().findScopeFor(node).orJust(globalScope.fromJust());
			if (scope.isGlobal() && scope.through.get(node.name).map(list -> (ImmutableList<Reference>) list).orJustLazy(ImmutableList::empty).find(reference -> reference.node == node).isJust()) {
				return new AssignmentTargetIdentifier(node.name);
			}
		}
		Maybe<String> newName = mapping.get(node.name);
		return new AssignmentTargetIdentifier(newName.orJust(node.name));
	}

	@Nonnull
	@Override
	public IdentifierExpression reduceIdentifierExpression(@Nonnull IdentifierExpression node) {
		if (lookup.isJust()) {
			Scope scope = lookup.fromJust().findScopeFor(node).orJust(globalScope.fromJust());
			if (scope.isGlobal() && scope.through.get(node.name).map(list -> (ImmutableList<Reference>) list).orJustLazy(ImmutableList::empty).find(reference -> reference.node == node).isJust()) {
				return new IdentifierExpression(node.name);
			}
		}
		Maybe<String> newName = mapping.get(node.name);
		return new IdentifierExpression(newName.orJust(node.name));
	}

	@Nonnull
	@Override
	public BindingIdentifier reduceBindingIdentifier(@Nonnull BindingIdentifier node) {
		Maybe<String> newName = mapping.get(node.name);
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
		return new ExportFromSpecifier(node.name, node.exportedName.map(exportedName -> mapping.get(exportedName).orJust(node.exportedName.fromJust())));
	}

	@Nonnull
	@Override
	public ExportLocalSpecifier reduceExportLocalSpecifier(
			@Nonnull ExportLocalSpecifier node,
			@Nonnull Node name) {
		return new ExportLocalSpecifier((IdentifierExpression) name, node.exportedName.map(exportedName -> mapping.get(exportedName).orJust(node.exportedName.fromJust())));
	}

	@Nonnull
	@Override
	public ImportDeclarationExportDeclarationStatement reduceExportAllFrom(@Nonnull ExportAllFrom node) {
		return new ExportAllFrom(node.moduleSpecifier);
	}

}
