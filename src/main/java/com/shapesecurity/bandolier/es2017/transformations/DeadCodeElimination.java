package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2017.ast.*;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.scope.Reference;
import com.shapesecurity.shift.es2017.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2017.scope.ScopeLookup;
import com.shapesecurity.shift.es2017.scope.Variable;

import javax.annotation.Nonnull;

public final class DeadCodeElimination {

	@Nonnull
	private final ScopeLookup lookup;

	private DeadCodeElimination(@Nonnull ScopeLookup lookup) {
		this.lookup = lookup;
	}

	private void checkName(@Nonnull String name) {
		if (name.equals("arguments") || name.equals("eval")) {
			throw new RuntimeException("Cannot declare as arguments or eval");
		}
	}

	private Pair<ImmutableSet<Pair<String, String>>, ImmutableList<ImportDeclarationExportDeclarationStatement>> fixDeclarations(@Nonnull ImportDeclarationExportDeclarationStatement item) {
		ImmutableSet<Pair<String, String>> removedImportSet = ImmutableSet.emptyUsingEquality();
		if (item instanceof Import) {
			Import importItem = (Import) item;
			Maybe<BindingIdentifier> defaultBinding = importItem.defaultBinding;
			if (importItem.defaultBinding.isJust()) {
				Variable defaultVariable = lookup.findVariableDeclaredBy(importItem.defaultBinding.fromJust()).fromJust();
				if (defaultVariable.references.length == 0) {
					checkName(defaultBinding.fromJust().name);
					removedImportSet = removedImportSet.put(Pair.of(importItem.moduleSpecifier, "default"));
					defaultBinding = Maybe.empty();
				}
			}
			ImmutableSet<Pair<String, String>>[] removedExportSetHack = new ImmutableSet[] {removedImportSet};
			ImmutableList<ImportSpecifier> specifiers = importItem.namedImports.filter(specifier -> {
				checkName(specifier.binding.name);
				boolean valid = lookup.findVariableDeclaredBy(specifier.binding).fromJust().references.length > 0;
				if (!valid) {
					removedExportSetHack[0] = removedExportSetHack[0].put(Pair.of(importItem.moduleSpecifier, specifier.name.orJust(specifier.binding.name)));
				}
				return valid;
			});
			removedImportSet = removedExportSetHack[0];
			return Pair.of(removedImportSet, ImmutableList.of(new Import(defaultBinding, specifiers, ((Import) item).moduleSpecifier)));
		} else if (item instanceof ImportNamespace) {
			ImportNamespace importItem = (ImportNamespace) item;
			Maybe<BindingIdentifier> defaultBinding = importItem.defaultBinding;
			if (importItem.defaultBinding.isJust()) {
				Variable defaultVariable = lookup.findVariableDeclaredBy(importItem.defaultBinding.fromJust()).fromJust();
				if (defaultVariable.references.length == 0) {
					checkName(defaultBinding.fromJust().name);
					removedImportSet = removedImportSet.put(Pair.of(importItem.moduleSpecifier, "default"));
					defaultBinding = Maybe.empty();
				}
			}
			checkName(importItem.namespaceBinding.name);
			Variable variable = lookup.findVariableDeclaredBy(importItem.namespaceBinding).fromJust();
			if (variable.references.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.of(new Import(defaultBinding, ImmutableList.empty(), importItem.moduleSpecifier)));
			}
			return Pair.of(removedImportSet, ImmutableList.of(new ImportNamespace(defaultBinding, importItem.namespaceBinding, importItem.moduleSpecifier)));
		} else if (item instanceof FunctionDeclaration) {
			FunctionDeclaration declaration = (FunctionDeclaration) item;
			checkName(declaration.name.name);
			Variable variable = lookup.findVariableDeclaredBy(declaration.name).fromJust();
			if (variable.references.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
		} else if (item instanceof ClassDeclaration) {
			ClassDeclaration declaration = (ClassDeclaration) item;
			checkName(declaration.name.name);
			Variable variable = lookup.findVariableDeclaredBy(declaration.name).fromJust();
			if (variable.references.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
		} else if (item instanceof VariableDeclarationStatement) {
			//todo: array/object bindings?
			ImmutableList<VariableDeclarator> declarators = ((VariableDeclarationStatement) item).declaration.declarators.filter(declarator -> {
				if (declarator.init.isNothing() || declarator.init.fromJust() instanceof FunctionExpression|| declarator.init.fromJust() instanceof ClassExpression || declarator.init.fromJust() instanceof ArrowExpression) {
					return true;
				}
				if (declarator.binding instanceof BindingIdentifier) {
					checkName(((BindingIdentifier) declarator.binding).name);
					Variable variable = lookup.findVariableDeclaredBy((BindingIdentifier) declarator.binding).fromJust();
					ImmutableSet<Reference> references = variable.references.filter(reference -> reference.node != declarator.binding).uniqByIdentity();
					return references.length() > 0;
				}
				return true;
			});
			if (declarators.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
			return Pair.of(removedImportSet, ImmutableList.of(new VariableDeclarationStatement(new VariableDeclaration(((VariableDeclarationStatement) item).declaration.kind, declarators))));
		}
		return Pair.of(removedImportSet, ImmutableList.of(item));
	}

	public static Pair<ImmutableSet<Pair<String, String>>, Module> removeAllUnusedDeclarations(@Nonnull Module module) {
		DeadCodeElimination deadCodeElimination = new DeadCodeElimination(new ScopeLookup(ScopeAnalyzer.analyze(module)));
		ImmutableList<Directive> directives = module.directives;
		ImmutableList<ImportDeclarationExportDeclarationStatement> statements = module.items;
		if (module.items.length == 1) { // IIFE
			ImportDeclarationExportDeclarationStatement item = module.items.maybeHead().fromJust();
			if (item instanceof CallExpression && ((CallExpression) item).arguments.length == 0 && ((CallExpression) item).callee instanceof FunctionExpression) {
				directives = module.directives.uniqByEquality().putAll(((FunctionExpression) ((CallExpression) item).callee).body.directives).toList();
				statements = ((FunctionExpression) ((CallExpression) item).callee).body.statements.map(statement -> statement);
			}
		}

		ImmutableSet<Pair<String, String>>[] removedImportSet = new ImmutableSet[] {ImmutableSet.emptyUsingEquality()};

		Module finalModule = new Module(directives, statements.flatMap(item -> {
			Pair<ImmutableSet<Pair<String, String>>, ImmutableList<ImportDeclarationExportDeclarationStatement>> pair = deadCodeElimination.fixDeclarations(item);
			removedImportSet[0] = removedImportSet[0].union(pair.left);
			return pair.right;
		}));
		return Pair.of(removedImportSet[0], finalModule);
	}

}
