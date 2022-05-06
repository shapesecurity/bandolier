package com.shapesecurity.bandolier.es2017.transformations;

import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2018.ast.ArrowExpression;
import com.shapesecurity.shift.es2018.ast.BindingIdentifier;
import com.shapesecurity.shift.es2018.ast.CallExpression;
import com.shapesecurity.shift.es2018.ast.ClassDeclaration;
import com.shapesecurity.shift.es2018.ast.ClassExpression;
import com.shapesecurity.shift.es2018.ast.Directive;
import com.shapesecurity.shift.es2018.ast.ExpressionStatement;
import com.shapesecurity.shift.es2018.ast.FormalParameters;
import com.shapesecurity.shift.es2018.ast.FunctionBody;
import com.shapesecurity.shift.es2018.ast.FunctionDeclaration;
import com.shapesecurity.shift.es2018.ast.FunctionExpression;
import com.shapesecurity.shift.es2018.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.es2018.ast.LiteralBooleanExpression;
import com.shapesecurity.shift.es2018.ast.LiteralInfinityExpression;
import com.shapesecurity.shift.es2018.ast.LiteralNullExpression;
import com.shapesecurity.shift.es2018.ast.LiteralNumericExpression;
import com.shapesecurity.shift.es2018.ast.LiteralRegExpExpression;
import com.shapesecurity.shift.es2018.ast.LiteralStringExpression;
import com.shapesecurity.shift.es2018.ast.Script;
import com.shapesecurity.shift.es2018.ast.Statement;
import com.shapesecurity.shift.es2018.ast.VariableDeclaration;
import com.shapesecurity.shift.es2018.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.es2018.ast.VariableDeclarator;
import com.shapesecurity.shift.es2018.scope.Reference;
import com.shapesecurity.shift.es2018.scope.ScopeAnalyzer;
import com.shapesecurity.shift.es2018.scope.ScopeLookup;
import com.shapesecurity.shift.es2018.scope.Variable;

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

	private Pair<ImmutableSet<Pair<String, String>>, ImmutableList<Statement>> fixDeclarations(@Nonnull Statement item) {
		ImmutableSet<Pair<String, String>> removedImportSet = ImmutableSet.emptyUsingEquality();
		if (item instanceof FunctionDeclaration) {
			FunctionDeclaration declaration = (FunctionDeclaration) item;
			checkName(declaration.name.name);
			Variable variable = lookup.findVariableDeclaredBy(declaration.name).fromJust();
			if (variable.references.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
		} else if (item instanceof ClassDeclaration) {
			ClassDeclaration declaration = (ClassDeclaration) item;
			checkName(declaration.name.name);
			Variable variable = lookup.findVariablesForClassDecl(declaration).right;
			if (variable.references.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
		} else if (item instanceof VariableDeclarationStatement) {
			ImmutableList<VariableDeclarator> unremovableDeclarators = ((VariableDeclarationStatement) item).declaration.declarators.filter(declarator -> {
				if (declarator.binding instanceof BindingIdentifier) {
					if (declarator.init.isNothing() ||
							declarator.init.fromJust() instanceof FunctionExpression ||
							declarator.init.fromJust() instanceof ClassExpression ||
							declarator.init.fromJust() instanceof ArrowExpression ||
							declarator.init.fromJust() instanceof LiteralStringExpression ||
							declarator.init.fromJust() instanceof LiteralNumericExpression ||
							declarator.init.fromJust() instanceof LiteralNullExpression ||
							declarator.init.fromJust() instanceof LiteralBooleanExpression ||
							declarator.init.fromJust() instanceof LiteralInfinityExpression ||
							declarator.init.fromJust() instanceof LiteralRegExpExpression) {
						checkName(((BindingIdentifier) declarator.binding).name);
						Variable variable = lookup.findVariableDeclaredBy((BindingIdentifier) declarator.binding).fromJust();
						ImmutableSet<Reference> references = variable.references.filter(reference -> reference.node != declarator.binding).uniqByIdentity();
						return references.length() > 0;
					}
				}
				// TODO: array/object bindings?
				return true;
			});
			if (unremovableDeclarators.length == 0) {
				return Pair.of(removedImportSet, ImmutableList.empty());
			}
			return Pair.of(removedImportSet, ImmutableList.of(new VariableDeclarationStatement(new VariableDeclaration(((VariableDeclarationStatement) item).declaration.kind, unremovableDeclarators))));
		}
		return Pair.of(removedImportSet, ImmutableList.of(item));
	}

	public static Script removeAllUnusedDeclarations(@Nonnull Script script) {
		DeadCodeElimination deadCodeElimination = new DeadCodeElimination(new ScopeLookup(ScopeAnalyzer.analyze(script)));
		ImmutableList<Directive> directives = script.directives;
		ImmutableList<Statement> statements = script.statements;
		boolean iife = false;
		if (statements.length == 1) { // IIFE
			ImportDeclarationExportDeclarationStatement item = statements.maybeHead().fromJust();
			if (item instanceof  ExpressionStatement && ((ExpressionStatement) item).expression instanceof CallExpression) {
				CallExpression callExpression = (CallExpression) ((ExpressionStatement) item).expression;
				if (callExpression.arguments.length == 0 && callExpression.callee instanceof FunctionExpression) {
					FunctionExpression functionExpression = (FunctionExpression) callExpression.callee;
					if (functionExpression.params.items.length == 0 && functionExpression.params.rest.isNothing()) {
						directives = directives.uniqByEquality().putAll(functionExpression.body.directives).toList();
						statements = functionExpression.body.statements.map(statement -> statement);
						iife = true;
					}
				}
			}
		}
		ImmutableList<Statement> finalStatements = statements.flatMap(item -> {
			Pair<ImmutableSet<Pair<String, String>>, ImmutableList<Statement>> pair = deadCodeElimination.fixDeclarations(item);
			return pair.right;
		});
		Script finalScript = new Script(directives, iife ?
				ImmutableList.of(new ExpressionStatement(new CallExpression(new FunctionExpression(false, false, Maybe.empty(), new FormalParameters(ImmutableList.empty(), Maybe.empty()), new FunctionBody(directives, finalStatements)), ImmutableList.empty())))
				: finalStatements);
		return finalScript;
	}

}
