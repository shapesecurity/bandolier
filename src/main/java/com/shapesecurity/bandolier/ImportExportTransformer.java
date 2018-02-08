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
package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2016.ast.AssignmentExpression;
import com.shapesecurity.shift.es2016.ast.BindingIdentifier;
import com.shapesecurity.shift.es2016.ast.CallExpression;
import com.shapesecurity.shift.es2016.ast.ClassDeclaration;
import com.shapesecurity.shift.es2016.ast.ComputedMemberAssignmentTarget;
import com.shapesecurity.shift.es2016.ast.ComputedMemberExpression;
import com.shapesecurity.shift.es2016.ast.Export;
import com.shapesecurity.shift.es2016.ast.ExportAllFrom;
import com.shapesecurity.shift.es2016.ast.ExportDeclaration;
import com.shapesecurity.shift.es2016.ast.ExportDefault;
import com.shapesecurity.shift.es2016.ast.ExportFrom;
import com.shapesecurity.shift.es2016.ast.ExportFromSpecifier;
import com.shapesecurity.shift.es2016.ast.ExportLocalSpecifier;
import com.shapesecurity.shift.es2016.ast.ExportLocals;
import com.shapesecurity.shift.es2016.ast.Expression;
import com.shapesecurity.shift.es2016.ast.ExpressionStatement;
import com.shapesecurity.shift.es2016.ast.ForInStatement;
import com.shapesecurity.shift.es2016.ast.FunctionDeclaration;
import com.shapesecurity.shift.es2016.ast.FunctionDeclarationClassDeclarationExpression;
import com.shapesecurity.shift.es2016.ast.FunctionDeclarationClassDeclarationVariableDeclaration;
import com.shapesecurity.shift.es2016.ast.FunctionExpression;
import com.shapesecurity.shift.es2016.ast.IdentifierExpression;
import com.shapesecurity.shift.es2016.ast.Import;
import com.shapesecurity.shift.es2016.ast.ImportDeclaration;
import com.shapesecurity.shift.es2016.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.es2016.ast.ImportNamespace;
import com.shapesecurity.shift.es2016.ast.ImportSpecifier;
import com.shapesecurity.shift.es2016.ast.LiteralStringExpression;
import com.shapesecurity.shift.es2016.ast.Module;
import com.shapesecurity.shift.es2016.ast.Script;
import com.shapesecurity.shift.es2016.ast.SpreadElementExpression;
import com.shapesecurity.shift.es2016.ast.Statement;
import com.shapesecurity.shift.es2016.ast.VariableDeclaration;
import com.shapesecurity.shift.es2016.ast.VariableDeclarationKind;
import com.shapesecurity.shift.es2016.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.es2016.ast.VariableDeclarator;

/**
 * ImportExportTransformer applies the transformations necessary for reducing a {@link Module} to a
 * {@link Script}.
 */
public class ImportExportTransformer {
	static public Module transformModule(Module module) {
		ImmutableList<Statement> statementItems =
			module.items.bind(ImportExportTransformer::transformImportDeclarationExportDeclarationStatement);

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
			return ImmutableList.of((Statement) statement); // do not transform other statements
		}
	}

	static private ImmutableList<Statement> transformImportDeclaration(ImportDeclaration declaration) {
		if (declaration instanceof Import) {
			return transformImport((Import) declaration);
		} else if (declaration instanceof ImportNamespace) {
			return transformImportNamespace((ImportNamespace) declaration);
		} else {
			return ImmutableList.empty(); //This should never happen!
		}
	}

	static private ImmutableList<Statement> transformImport(Import statement) {
		String resolver = "__resolver";

		Statement requireStatement =
			statement.namedImports.isEmpty() && statement.defaultBinding.isNothing() ?
				makeRequireStatement(statement.moduleSpecifier) :
				makeRequireStatement(resolver, statement.moduleSpecifier);

		ImmutableList<Statement> variableDeclarationStatements =
			statement.namedImports.map(x -> (Statement) makeNamedImportStatement(resolver, x));

		if (statement.defaultBinding.isJust()) {
			variableDeclarationStatements = variableDeclarationStatements.cons(
				makeDefaultBindingStatement(resolver, statement.defaultBinding.fromJust()));
		}

		return variableDeclarationStatements.cons(requireStatement);
	}

	static private ImmutableList<Statement> transformImportNamespace(ImportNamespace statement) {
		String resolver = "__resolver";

		Statement requireStatement = makeRequireStatement(resolver, statement.moduleSpecifier);

		ImmutableList<Statement> variableDeclarationStatements =
			ImmutableList.of(makeNameSpaceBindingStatement(resolver, statement.namespaceBinding));

		if (statement.defaultBinding.isJust()) {
			variableDeclarationStatements = variableDeclarationStatements.cons(
				makeDefaultBindingStatement(resolver, statement.defaultBinding.fromJust()));
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
		} else if (declaration instanceof ExportLocals) {
			return transformExportLocals((ExportLocals) declaration);
		} else {
			return ImmutableList.empty(); //This should never happen
		}
	}


	static private ImmutableList<Statement> transformExport(Export statement) {
		FunctionDeclarationClassDeclarationVariableDeclaration declaration = statement.declaration;
		if (declaration instanceof FunctionDeclaration) {
			return transformExportFunctionDeclaration((FunctionDeclaration) declaration);
		} else if (declaration instanceof ClassDeclaration) {
			return transformExportClassDeclaration((ClassDeclaration) declaration);
		} else if (declaration instanceof VariableDeclaration) {
			return transformExportVariableDeclaration((VariableDeclaration) declaration);
		} else {
			return ImmutableList.empty(); //This should never happen
		}
	}

	static private ImmutableList<Statement> transformExportFunctionDeclaration(FunctionDeclaration declaration) {
		return ImmutableList.of(declaration,
			makeNamedExportStatement(new ExportLocalSpecifier(new IdentifierExpression(declaration.name.name), Maybe.empty())));
	}

	static private ImmutableList<Statement> transformExportClassDeclaration(ClassDeclaration declaration) {
		return ImmutableList.of(declaration,
			makeNamedExportStatement(new ExportLocalSpecifier(new IdentifierExpression(declaration.name.name), Maybe.empty())));
	}

	static private ImmutableList<Statement> transformExportVariableDeclaration(VariableDeclaration declaration) {
		ImmutableList<Statement> exportStatements =
			declaration.declarators.map(x -> {
				BindingIdentifier temp = (BindingIdentifier) x.binding;
				return makeNamedExportStatement(new ExportLocalSpecifier(new IdentifierExpression(temp.name), Maybe.empty()));
			});
		return exportStatements.cons(new VariableDeclarationStatement(declaration));
	}

	static private ImmutableList<Statement> transformExportAllFrom(ExportAllFrom statement) {
		String resolver = "__resolver";

		Statement requireStatement = makeRequireStatement(resolver, statement.moduleSpecifier);
		Statement enumerateExports = makeEnumerateExports(resolver);

		return ImmutableList.of(requireStatement, enumerateExports);
	}

	static private ImmutableList<Statement> transformExportDefault(ExportDefault statement) {
		FunctionDeclarationClassDeclarationExpression body = statement.body;
		if (body instanceof FunctionDeclaration) {
			return transformExportDefaultFunctionDeclaration((FunctionDeclaration) body);
		} else if (body instanceof ClassDeclaration) {
			return transformExportDefaultClassDeclaration((ClassDeclaration) body);
		} else if (body instanceof Expression) {
			return transformExportDefaultExpression((Expression) body);
		} else {
			return ImmutableList.empty(); //This should never happen
		}
	}

	static private ImmutableList<Statement> transformExportDefaultFunctionDeclaration(FunctionDeclaration declaration) {
		String functionName = declaration.name.name;
		if (functionName.equals("*default*")) {
			IdentifierExpression exportsIden = new IdentifierExpression("exports");
			ComputedMemberAssignmentTarget compMem = new ComputedMemberAssignmentTarget(exportsIden, new LiteralStringExpression("default"));

			AssignmentExpression assignmentExpression =
				new AssignmentExpression(compMem, new FunctionExpression(false, Maybe.empty(),
					declaration.params, declaration.body));
			return ImmutableList.of(new ExpressionStatement(assignmentExpression));
		} else {
			return ImmutableList.of(declaration,
									  makeExportDefaultStatement(new ExportLocalSpecifier(new IdentifierExpression(declaration.name.name), Maybe.empty())));
		}
	}

	static private ImmutableList<Statement> transformExportDefaultClassDeclaration(ClassDeclaration declaration) {
		return ImmutableList.of(declaration,
			makeExportDefaultStatement(new ExportLocalSpecifier(new IdentifierExpression(declaration.name.name), Maybe.empty())));
	}

	static private ImmutableList<Statement> transformExportDefaultExpression(Expression expression) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberAssignmentTarget staticMem = new ComputedMemberAssignmentTarget(exportsIden, new LiteralStringExpression("default"));

		AssignmentExpression assignmentExpression =
			new AssignmentExpression(staticMem, expression);
		return ImmutableList.of(new ExpressionStatement(assignmentExpression));
	}

	static private ImmutableList<Statement> transformExportLocals(ExportLocals declaration) {
		return declaration.namedExports.map(x -> (Statement) makeNamedExportStatement(x));
	}

	static private ImmutableList<Statement> transformExportFrom(ExportFrom statement) {
		String resolver = "__resolver";

		String moduleSpecifier = statement.moduleSpecifier;
		ImmutableList<Statement> statements = statement.namedExports.map(x -> (Statement) makeNamedExportStatement(resolver, x));

		return statements.cons(makeRequireStatement(resolver, moduleSpecifier));
	}

	// e.g., exports.x = x;
	static private ExpressionStatement makeNamedExportStatement(ExportLocalSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberAssignmentTarget staticMem = new ComputedMemberAssignmentTarget(exportsIden, new LiteralStringExpression(specifier.name.name));

		IdentifierExpression exportVar = makeExportVar(specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., exports.default = x;
	static private ExpressionStatement makeExportDefaultStatement(ExportLocalSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberAssignmentTarget staticMem = new ComputedMemberAssignmentTarget(exportsIden, new LiteralStringExpression("default"));

		IdentifierExpression exportVar = makeExportVar(specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., exports.x = __resolver.x
	static private ExpressionStatement makeNamedExportStatement(String resolver, ExportFromSpecifier specifier) {
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberAssignmentTarget staticMem = new ComputedMemberAssignmentTarget(exportsIden, new LiteralStringExpression(specifier.name));

		ComputedMemberExpression exportVar = makeExportVar(resolver, specifier);

		AssignmentExpression assignmentExpression = new AssignmentExpression(staticMem, exportVar);

		return new ExpressionStatement(assignmentExpression);
	}

	// e.g., __resolver.x
	static private ComputedMemberExpression makeExportVar(String resolver, ExportFromSpecifier specifier) {
		String property = specifier.exportedName.isJust() ? specifier.exportedName.fromJust() : specifier.name;

		return new ComputedMemberExpression(new IdentifierExpression(resolver), new LiteralStringExpression(property));
	}

	// e.g., x
	static private IdentifierExpression makeExportVar(ExportLocalSpecifier specifier) {
		return specifier.exportedName.isJust() ?
			new IdentifierExpression(specifier.exportedName.fromJust()) :
			new IdentifierExpression(specifier.name.name);
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
			ImmutableList.of(new VariableDeclarator(resolverIden, Maybe.of(callExp)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., require('lib')
	static private CallExpression makeRequireCallExpression(String moduleSpecifier) {
		IdentifierExpression require = new IdentifierExpression("require"); // function require

		LiteralStringExpression module = new LiteralStringExpression(moduleSpecifier); // e.g. 'lib.js'
		IdentifierExpression secondParam = new IdentifierExpression("module"); // the second parameter to require

		ImmutableList<SpreadElementExpression> requireArguments =
			ImmutableList.of(module, secondParam); // parameters to pass to require

		return new CallExpression(require, requireArguments);
	}

	// e.g., d = _resolver.default;
	static private VariableDeclarationStatement makeDefaultBindingStatement(String resolver, BindingIdentifier binding) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);
		ComputedMemberExpression staticMem = new ComputedMemberExpression(resolverIden, new LiteralStringExpression("default"));

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.of(new VariableDeclarator(binding, Maybe.of(staticMem)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}


	// e.g., m = __resolver;
	static private VariableDeclarationStatement makeNameSpaceBindingStatement(String resolver, BindingIdentifier binding) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.of(new VariableDeclarator(binding, Maybe.of(resolverIden)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., x = __resolver.y or x = __resolver.x
	static private VariableDeclarationStatement makeNamedImportStatement(String resolver, ImportSpecifier specifier) {
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);
		ComputedMemberExpression staticMem = specifier.name.isJust() ?
			new ComputedMemberExpression(resolverIden, new LiteralStringExpression(specifier.name.fromJust())) :
			new ComputedMemberExpression(resolverIden, new LiteralStringExpression(specifier.binding.name));

		ImmutableList<VariableDeclarator> declarators =
			ImmutableList.of(new VariableDeclarator(specifier.binding, Maybe.of(staticMem)));

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// e.g., for(var i in __resolver) exports[k] = __resolver[k];
	static private ForInStatement makeEnumerateExports(String resolver) {
		BindingIdentifier itemIden = new BindingIdentifier("i");
		VariableDeclarator itemDeclarator = new VariableDeclarator(itemIden, Maybe.empty());
		VariableDeclaration itemDeclaration =
			new VariableDeclaration(VariableDeclarationKind.Var, ImmutableList.of(itemDeclarator));
		IdentifierExpression resolverIden = new IdentifierExpression(resolver);

		IdentifierExpression itemIdenExp = new IdentifierExpression("i");
		IdentifierExpression exportsIden = new IdentifierExpression("exports");
		ComputedMemberAssignmentTarget exportsItem = new ComputedMemberAssignmentTarget(exportsIden, itemIdenExp);

		ComputedMemberExpression resolverItem = new ComputedMemberExpression(resolverIden, itemIdenExp);

		AssignmentExpression assignment = new AssignmentExpression(exportsItem, resolverItem);


		return new ForInStatement(itemDeclaration, resolverIden, new ExpressionStatement(assignment));
	}
}
