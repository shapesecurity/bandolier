/*
 * Copyright 2016 Shape Security, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shapesecurity.es6bundler;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ClassDeclaration;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.Export;
import com.shapesecurity.shift.ast.ExportAllFrom;
import com.shapesecurity.shift.ast.ExportDeclaration;
import com.shapesecurity.shift.ast.ExportDefault;
import com.shapesecurity.shift.ast.ExportFrom;
import com.shapesecurity.shift.ast.ExportSpecifier;
import com.shapesecurity.shift.ast.Expression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.ForInStatement;
import com.shapesecurity.shift.ast.FunctionDeclaration;
import com.shapesecurity.shift.ast.FunctionDeclarationClassDeclarationExpression;
import com.shapesecurity.shift.ast.FunctionDeclarationClassDeclarationVariableDeclaration;
import com.shapesecurity.shift.ast.FunctionExpression;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.Import;
import com.shapesecurity.shift.ast.ImportDeclaration;
import com.shapesecurity.shift.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.ast.ImportNamespace;
import com.shapesecurity.shift.ast.ImportSpecifier;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.SpreadElementExpression;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.ast.VariableDeclaration;
import com.shapesecurity.shift.ast.VariableDeclarationKind;
import com.shapesecurity.shift.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.ast.VariableDeclarator;

/**
 * ImportExportTransformer applies the transformations necessary for reducing a {@link Module} to a
 * {@link Script}.
 */
public class ImportExportTransformer {
	static public Module transformModule(Module module) {
		ImmutableList<Statement> statementItems =
			module.getItems().bind(ImportExportTransformer::transformImportDeclarationExportDeclarationStatement);

		ImmutableList<ImportDeclarationExportDeclarationStatement> items =
			statementItems.map(x -> (ImportDeclarationExportDeclarationStatement) x);

		return new Module(module.directives, items);
	}

	static private ImmutableList<Statement> transformImportDeclarationExportDeclarationStatement(
		ImportDeclarationExportDeclarationStatement statement) {
		if (statement instanceof ImportDeclaration) {
			return transformImportDeclaration((ImportDeclaration) statement);
		} else if (statement instanceof ExportDeclaration) {
			return transformExportDeclaration((ExportDeclaration) statement);
		} else {
			return ImmutableList.from((Statement) statement); // do not transform other statements
		}
	}

	static private ImmutableList<Statement> transformImportDeclaration(ImportDeclaration declaration) {
		if (declaration instanceof Import) {
			return transformImport((Import) declaration);
		} else if (declaration instanceof ImportNamespace) {
			return transformImportNamespace((ImportNamespace) declaration);
		} else {
			return ImmutableList.nil(); //This should never happen!
		}
	}

	static private ImmutableList<Statement> transformImport(Import statement) {
		String resolver = "__resolver";

		Statement requireStatement =
			statement.getNamedImports().isEmpty() && statement.getDefaultBinding().isNothing() ?
				makeRequireStatement(statement.getModuleSpecifier()) :
				makeRequireStatement(resolver, statement.getModuleSpecifier());

		ImmutableList<Statement> variableDeclarationStatements =
			statement.getNamedImports().map(x -> (Statement) makeNamedImportStatement(resolver, x));

		if (statement.getDefaultBinding().isJust()) {
			variableDeclarationStatements = variableDeclarationStatements.cons(
				makeDefaultBindingStatement(resolver, statement.getDefaultBinding().just()));
		}

		return variableDeclarationStatements.cons(requireStatement);
	}

	static private ImmutableList<Statement> transformImportNamespace(ImportNamespace statement) {
		String resolver = "__resolver";

		Statement requireStatement = makeRequireStatement(resolver, statement.getModuleSpecifier());

		ImmutableList<Statement> variableDeclarationStatements =
			ImmutableList.from(makeNameSpaceBindingStatement(resolver, statement.getNamespaceBinding()));

		if (statement.getDefaultBinding().isJust()) {
			variableDeclarationStatements = variableDeclarationStatements.cons(
				makeDefaultBindingStatement(resolver, statement.getDefaultBinding().just()));
		}

		return variableDeclarationStatements.cons(requireStatement);
	}


	static private ImmutableList<Statement> transformExportDeclaration(ExportDeclaration declaration) {
		if (declaration instanceof Export) {
			return transformExport((Export) declaration);
		} else if (declaration instanceof ExportAllFrom) {
			return transformExportAllFrom((ExportAllFrom) declaration);
		} else if (declaration instanceof ExportDefault) {
			return transformExportDefault((ExportDefault) declaration);
		} else if (declaration instanceof ExportFrom) {
			return transformExportFrom((ExportFrom) declaration);
		} else {
			return ImmutableList.nil(); //This should never happen
		}
	}

	static private ImmutableList<Statement> transformExport(Export statement) {
		FunctionDeclarationClassDeclarationVariableDeclaration declaration = statement.getDeclaration();
		if (declaration instanceof FunctionDeclaration) {
			return transformExportFunctionDeclaration((FunctionDeclaration) declaration);
		} else if (declaration instanceof ClassDeclaration) {
			return transformExportClassDeclaration((ClassDeclaration) declaration);
		} else if (declaration instanceof VariableDeclaration) {
			return transformExportVariableDeclaration((VariableDeclaration) declaration);
		} else {
			return ImmutableList.nil(); //This should never happen
		}
	}

	static private ImmutableList<Statement> transformExportFunctionDeclaration(FunctionDeclaration declaration) {
		return ImmutableList.from(declaration,
			makeNamedExportStatement(new ExportSpecifier(Maybe.nothing(), declaration.getName().getName())));
	}

	static private ImmutableList<Statement> transformExportClassDeclaration(ClassDeclaration declaration) {
		return ImmutableList.from(declaration,
			makeNamedExportStatement(new ExportSpecifier(Maybe.nothing(), declaration.getName().getName())));
	}

	static private ImmutableList<Statement> transformExportVariableDeclaration(VariableDeclaration declaration) {
		ImmutableList<Statement> exportStatements =
			declaration.declarators.map(x -> {
				BindingIdentifier temp = (BindingIdentifier) x.getBinding();
				return makeNamedExportStatement(new ExportSpecifier(Maybe.nothing(), temp.getName()));
			});
		return exportStatements.cons(new VariableDeclarationStatement(declaration));
	}

	static private ImmutableList<Statement> transformExportAllFrom(ExportAllFrom statement) {
		String resolver = "__resolver";

		Statement requireStatement = makeRequireStatement(resolver, statement.getModuleSpecifier());
		Statement enumerateExports = makeEnumerateExports(resolver);

		return ImmutableList.from(requireStatement, enumerateExports);
	}

	static private ImmutableList<Statement> transformExportDefault(ExportDefault statement) {
		FunctionDeclarationClassDeclarationExpression body = statement.getBody();
		if (body instanceof FunctionDeclaration) {
			return transformExportDefaultFunctionDeclaration((FunctionDeclaration) body);
		} else if (body instanceof ClassDeclaration) {
			return transformExportDefaultClassDeclaration((ClassDeclaration) body);
		} else if (body instanceof Expression) {
			return transformExportDefaultExpression((Expression) body);
		} else {
			return ImmutableList.nil(); //This should never happen
		}
	}

	static private ImmutableList<Statement> transformExportDefaultFunctionDeclaration(FunctionDeclaration declaration) {
		String functionName = declaration.getName().getName();
		if (functionName.equals("*default*")) {
			IdentifierExpression exportsIden = new IdentifierExpression("exports");
			ComputedMemberExpression compMem = new ComputedMemberExpression(new LiteralStringExpression("default"), exportsIden);

			AssignmentExpression assignmentExpression =
				new AssignmentExpression(compMem, new FunctionExpression(Maybe.nothing(), false,
					declaration.getParams(), declaration.getBody()));
			return ImmutableList.from(new ExpressionStatement(assignmentExpression));
		} else {
			return ImmutableList.from(declaration,
									  makeExportDefaultStatement(new ExportSpecifier(Maybe.nothing(), declaration.getName().getName())));
		}
	}

	static private ImmutableList<Statement> transformExportDefaultClassDeclaration(ClassDeclaration declaration) {
		return ImmutableList.from(declaration,
			makeExportDefaultStatement(new ExportSpecifier(Maybe.nothing(), declaration.getName().getName())));
	}

	static private ImmutableList<Statement> transformExportDefaultExpression(Expression expression) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberExpression staticMem = new ComputedMemberExpression(new LiteralStringExpression("default"), exportsIden);

		AssignmentExpression assignmentExpression =
			new AssignmentExpression(staticMem, expression);
		return ImmutableList.from(new ExpressionStatement(assignmentExpression));
	}

	static private ImmutableList<Statement> transformExportFrom(ExportFrom statement) {
		String resolver = "__resolver";

		Maybe<String> moduleSpecifier = statement.getModuleSpecifier();
		ImmutableList<Statement> statements = moduleSpecifier.isJust() ?
			statement.getNamedExports().map(x -> (Statement) makeNamedExportStatement(resolver, x)) :
			statement.getNamedExports().map(x -> (Statement) makeNamedExportStatement(x));

		return moduleSpecifier.isJust() ?
			statements.cons(makeRequireStatement(resolver, moduleSpecifier.just())) :
			statements;
	}

	// e.g., exports.x = x;
	static private ExpressionStatement makeNamedExportStatement(ExportSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberExpression staticMem = new ComputedMemberExpression(new LiteralStringExpression(specifier.getExportedName()), exportsIden);

		IdentifierExpression exportVar = makeExportVar(specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., exports.default = x;
	static private ExpressionStatement makeExportDefaultStatement(ExportSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberExpression staticMem = new ComputedMemberExpression(new LiteralStringExpression("default"), exportsIden);

		IdentifierExpression exportVar = makeExportVar(specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., exports.x = __resolver.x
	static private ExpressionStatement makeNamedExportStatement(String resolver, ExportSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberExpression staticMem = new ComputedMemberExpression(new LiteralStringExpression(specifier.getExportedName()), exportsIden);

		ComputedMemberExpression exportVar = makeExportVar(resolver, specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., __resolver.x
	static private ComputedMemberExpression makeExportVar(String resolver, ExportSpecifier specifier) {
		String property = specifier.getName().isJust() ? specifier.getName().just() : specifier.getExportedName();

		return new ComputedMemberExpression(new LiteralStringExpression(property), new IdentifierExpression(resolver));
	}

	// e.g., x
	static private IdentifierExpression makeExportVar(ExportSpecifier specifier) {
		return specifier.getName().isJust() ?
			new IdentifierExpression(specifier.getName().just()) :
			new IdentifierExpression(specifier.getExportedName());
	}

	// e.g., require('lib');
	static private ExpressionStatement makeRequireStatement(String moduleSpecifier) {
		return new ExpressionStatement(makeRequireCallExpression(moduleSpecifier));
	}

	// e.g., var __resolver = require('lib');
	static private VariableDeclarationStatement makeRequireStatement(String resolver, String moduleSpecifier) {
		BindingIdentifier resolverIden = new BindingIdentifier(resolver); // e.g. _resolver = require(...);
		CallExpression callExp = makeRequireCallExpression(moduleSpecifier);

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.from(new VariableDeclarator(resolverIden, Maybe.just(callExp)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., require('lib')
	static private CallExpression makeRequireCallExpression(String moduleSpecifier) {
		IdentifierExpression require = new IdentifierExpression("require"); // function require

		LiteralStringExpression module = new LiteralStringExpression(moduleSpecifier); // e.g. 'lib.js'
		IdentifierExpression secondParam = new IdentifierExpression("module"); // the second parameter to require

		ImmutableList<SpreadElementExpression> requireArguments =
			ImmutableList.from(module, secondParam); // parameters to pass to require

		return new CallExpression(require, requireArguments);
	}

	// e.g., d = _resolver.default;
	static private VariableDeclarationStatement makeDefaultBindingStatement(String resolver, BindingIdentifier binding) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);
		ComputedMemberExpression staticMem = new ComputedMemberExpression(new LiteralStringExpression("default"), resolverIden);

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.from(new VariableDeclarator(binding, Maybe.just(staticMem)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}


	// e.g., m = __resolver;
	static private VariableDeclarationStatement makeNameSpaceBindingStatement(String resolver, BindingIdentifier binding) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.from(new VariableDeclarator(binding, Maybe.just(resolverIden)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., x = __resolver.y or x = __resolver.x
	static private VariableDeclarationStatement makeNamedImportStatement(String resolver, ImportSpecifier specifier) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);
		ComputedMemberExpression staticMem = specifier.getName().isJust() ?
			new ComputedMemberExpression(new LiteralStringExpression(specifier.getName().just()), resolverIden) :
			new ComputedMemberExpression(new LiteralStringExpression(specifier.getBinding().getName()), resolverIden);

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.from(new VariableDeclarator(specifier.getBinding(), Maybe.just(staticMem)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., for(var i in __resolver) exports[k] = __resolver[k];
	static private ForInStatement makeEnumerateExports(String resolver) {
		BindingIdentifier itemIden = new BindingIdentifier("i");
		VariableDeclarator itemDeclarator = new VariableDeclarator(itemIden, Maybe.nothing());
		VariableDeclaration itemDeclaration =
			new VariableDeclaration(VariableDeclarationKind.Var, ImmutableList.from(itemDeclarator));
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);

		IdentifierExpression itemIdenExp = new IdentifierExpression("i");
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberExpression exportsItem = new ComputedMemberExpression(itemIdenExp, exportsIden);

		ComputedMemberExpression resolverItem = new ComputedMemberExpression(itemIdenExp, resolverIden);

		AssignmentExpression assignment = new AssignmentExpression(exportsItem, resolverItem);


		return new ForInStatement(itemDeclaration, resolverIden, new ExpressionStatement(assignment));
	}
}
