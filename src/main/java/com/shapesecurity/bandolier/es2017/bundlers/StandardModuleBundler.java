package com.shapesecurity.bandolier.es2017.bundlers;

import com.shapesecurity.bandolier.es2017.ImportExportTransformer;
import com.shapesecurity.bandolier.es2017.ImportMappingRewriter;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2017.ast.*;

import com.shapesecurity.bandolier.es2017.ModuleWrapper;
import com.shapesecurity.shift.es2017.ast.operators.BinaryOperator;
import com.shapesecurity.shift.es2017.ast.operators.UnaryOperator;

import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.EarlyErrorChecker;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardModuleBundler implements IModuleBundler {

	private final Map<String, String> pathMapping = new HashMap<>();

	// This function is only guaranteed to be deterministic if the provided `modules` map has deterministic ordering
	@NotNull
	@Override
	public Script bundleEntrypoint(BundlerOptions options, String entry, Map<String, ModuleWrapper> modules) {
		// rather than bundle with absolute paths (a potential information leak) create a mapping
		// of absolute paths to a unique name
		Integer moduleCount = 0;
		for (String absPath : modules.keySet()) {
			this.pathMapping.put(absPath, (++moduleCount).toString());
		}

		ImportMappingRewriter importMappingRewriter = new ImportMappingRewriter(this.pathMapping);
		LinkedHashMap<String, ModuleWrapper> rewrittenModules = new LinkedHashMap<>();
		modules.forEach((absPath, m) -> rewrittenModules.put(this.pathMapping.get(absPath), importMappingRewriter.rewrite(m)));
		ExpressionStatement bundled = anonymousFunctionCall(this.pathMapping.get(entry), rewrittenModules);
		return new Script(ImmutableList.empty(), ImmutableList.of(bundled));
	}

	@Override
	public @NotNull Pair<Script, ImmutableList<EarlyError>> bundleEntrypointWithEarlyErrors(BundlerOptions options, String entry, Map<String, ModuleWrapper> modules) {
		return Pair.of(bundleEntrypoint(options, entry, modules), ImmutableList.from(modules.values().stream().map(EarlyErrorChecker::validate).collect(Collectors.toList())).foldLeft(ImmutableList::append, ImmutableList.empty()));
	}

	//(function(global){ ... }.call(this, this));
	private ExpressionStatement anonymousFunctionCall(String rootPath, LinkedHashMap<String, ModuleWrapper> rewrittenModules) {
		StaticMemberExpression anonymousCall =
				new StaticMemberExpression(anonymousFunctionExpression(rootPath, rewrittenModules), "call");
		ImmutableList<SpreadElementExpression> params = ImmutableList.of(new ThisExpression(), new ThisExpression());
		CallExpression callExpression = new CallExpression(anonymousCall, params);

		return new ExpressionStatement(callExpression);
	}

	// function(global) {...}
	private FunctionExpression anonymousFunctionExpression(String rootPath, LinkedHashMap<String, ModuleWrapper> rewrittenModules) {
		BindingIdentifier globalIden = new BindingIdentifier("global");
		FormalParameters params = new FormalParameters(ImmutableList.of(globalIden), Maybe.empty());

		LinkedList<Statement> requireStatements =
				rewrittenModules.entrySet().stream().map(x -> {
					Node reduced = ImportExportTransformer.transformModule(x.getValue());
					return requireDefineStatement(x.getKey(), (ModuleWrapper) reduced);
				}).collect(Collectors.toCollection(LinkedList::new));
		ImmutableList<Statement> statements = ImmutableList.from(requireStatements);
		statements = statements.append(ImmutableList.of(requireCall(rootPath)));
		statements = statements.cons(requireDefineDefinition());
		statements = statements.cons(requireResolveDefinition());
		statements = statements.cons(initializeRequireCache());
		statements = statements.cons(initializeRequireModules());
		statements = statements.cons(requireFunctionDeclaration());

		FunctionBody body = new FunctionBody(ImmutableList.of(new Directive("use strict")), statements);

		return new FunctionExpression(false, false, Maybe.empty(), params, body);
	}

	//function require(file,parentModule){ ... }
	private FunctionDeclaration requireFunctionDeclaration() {
		BindingIdentifier requireIden = new BindingIdentifier("require");
		BindingIdentifier fileParamIden = new BindingIdentifier("file");
		BindingIdentifier parentModuleIden = new BindingIdentifier("parentModule");

		FormalParameters params = new FormalParameters(ImmutableList.of(fileParamIden, parentModuleIden), Maybe.empty());

		ImmutableList<Statement> statements = ImmutableList.empty();
		statements = statements.cons(returnRequire());
		statements = statements.cons(moduleLoaded());
		statements = statements.cons(resolvedCall());
		statements = statements.cons(cacheExports());
		statements = statements.cons(dirnameDeclaration());
		statements = statements.cons(checkParentModuleIf());
		statements = statements.cons(moduleObjectDeclaration());
		statements = statements.cons(checkResolvedIf());
		statements = statements.cons(resolvedDeclaration());
		statements = statements.cons(checkCacheIf());

		FunctionBody body = new FunctionBody(ImmutableList.empty(), statements);

		return new FunctionDeclaration(false, false, requireIden, params, body);
	}

	//if({}.hasOwnProperty.call(require.cache,file)) return require.cache[file];
	private IfStatement checkCacheIf() {
		ObjectExpression objectExpression = new ObjectExpression(ImmutableList.empty());
		StaticMemberExpression objHasOwnProp = new StaticMemberExpression(objectExpression, "hasOwnProperty");
		StaticMemberExpression objHasOwnPropCall = new StaticMemberExpression(objHasOwnProp, "call");

		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression(requireIden, "cache");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(requireCache, fileIden);

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.of(requireCache, fileIden);
		CallExpression callExpression = new CallExpression(objHasOwnPropCall, callParams);

		ReturnStatement returnStatement = new ReturnStatement(Maybe.of(requireCacheFile));

		return new IfStatement(callExpression, returnStatement, Maybe.empty());
	}

	//var resolved=require.resolve(file);
	private VariableDeclarationStatement resolvedDeclaration() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireResolve = new StaticMemberExpression(requireIden, "resolve");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ImmutableList<SpreadElementExpression> callParams = ImmutableList.of(fileIden);
		CallExpression callExpression = new CallExpression(requireResolve, callParams);

		BindingIdentifier resolvedIden = new BindingIdentifier("resolved");
		VariableDeclarator resolvedDecl = new VariableDeclarator(resolvedIden, Maybe.of(callExpression));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.of(resolvedDecl);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	//if(!resolved)throw new Error("Failed to resolve module "+file);
	private IfStatement checkResolvedIf() {
		LiteralStringExpression errorMsg = new LiteralStringExpression("Failed to resolve module ");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		BinaryExpression errorExpression = new BinaryExpression(errorMsg, BinaryOperator.Plus, fileIden);

		IdentifierExpression errorIden = new IdentifierExpression("Error");
		ImmutableList<SpreadElementExpression> errorParams = ImmutableList.of(errorExpression);
		NewExpression newExpression = new NewExpression(errorIden, errorParams);
		ThrowStatement throwStatement = new ThrowStatement(newExpression);

		IdentifierExpression resolvedIden = new IdentifierExpression("resolved");
		UnaryExpression testExpression = new UnaryExpression(UnaryOperator.LogicalNot, resolvedIden);

		return new IfStatement(testExpression, throwStatement, Maybe.empty());
	}

	//var module$={
	//  id:file,
	//  require:require,
	//  filename:file,exports:{},
	//  loaded:false,
	//  parent:parentModule,
	//  children:[]
	// };
	private VariableDeclarationStatement moduleObjectDeclaration() {
		BindingIdentifier moduleIden = new BindingIdentifier("module$");

		DataProperty idProp = new DataProperty(new StaticPropertyName("id"), new IdentifierExpression("file"));
		DataProperty requireProp = new DataProperty(new StaticPropertyName("require"), new IdentifierExpression("require"));
		DataProperty fileNameProp = new DataProperty(new StaticPropertyName("filename"), new IdentifierExpression("file"));
		DataProperty exportsProp = new DataProperty(new StaticPropertyName("exports"), new ObjectExpression(ImmutableList.empty()));
		DataProperty loadedProperty = new DataProperty(new StaticPropertyName("loaded"), new LiteralBooleanExpression(false));
		DataProperty parentProp = new DataProperty(new StaticPropertyName("parent"), new IdentifierExpression("parentModule"));
		DataProperty childrenProp = new DataProperty(new StaticPropertyName("children"), new ArrayExpression(ImmutableList.empty()));
		ImmutableList<ObjectProperty> properties =
				ImmutableList.of(idProp, requireProp, fileNameProp, exportsProp, loadedProperty, parentProp, childrenProp);
		ObjectExpression object = new ObjectExpression(properties);

		VariableDeclarator declarator = new VariableDeclarator(moduleIden, Maybe.of(object));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.of(declarator);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);
		return new VariableDeclarationStatement(declaration);
	}

	// if(parentModule) parentModule.children.push(module$);
	private IfStatement checkParentModuleIf() {
		IdentifierExpression parentModuleIden = new IdentifierExpression("parentModule");
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression parentModuleChildren = new StaticMemberExpression(parentModuleIden, "children");
		StaticMemberExpression parentModuleChildrenPush = new StaticMemberExpression(parentModuleChildren, "push");

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.of(moduleIden);
		CallExpression callExpression = new CallExpression(parentModuleChildrenPush, callParams);
		ExpressionStatement callStatement = new ExpressionStatement(callExpression);

		return new IfStatement(parentModuleIden, callStatement, Maybe.empty());
	}

	// var dirname=file.slice(0,file.lastIndexOf("/")+1);
	private VariableDeclarationStatement dirnameDeclaration() {
		IdentifierExpression fileIden = new IdentifierExpression("file");
		StaticMemberExpression fileLastIndOf = new StaticMemberExpression(fileIden, "lastIndexOf");
		LiteralStringExpression slashStr = new LiteralStringExpression("/");
		ImmutableList<SpreadElementExpression> lastIndOfParams = ImmutableList.of(slashStr);
		CallExpression lastIndOfCall = new CallExpression(fileLastIndOf, lastIndOfParams);

		BinaryExpression sliceSecondParam =
				new BinaryExpression(lastIndOfCall, BinaryOperator.Plus, new LiteralNumericExpression(1.0));
		ImmutableList<SpreadElementExpression> sliceParams =
				ImmutableList.of(new LiteralNumericExpression(0.0), sliceSecondParam);
		StaticMemberExpression fileSlice = new StaticMemberExpression(fileIden, "slice");
		CallExpression fileSliceCall = new CallExpression(fileSlice, sliceParams);

		BindingIdentifier dirnameIden = new BindingIdentifier("dirname");
		VariableDeclarator declarator = new VariableDeclarator(dirnameIden, Maybe.of(fileSliceCall));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.of(declarator);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// require.cache[file]=module$.exports;
	private ExpressionStatement cacheExports() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression(requireIden, "cache");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberAssignmentTarget requireCacheFile = new ComputedMemberAssignmentTarget(requireCache, fileIden);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression(moduleIden, "exports");

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ExpressionStatement(assignment);
	}

	// top-level this bindings in a module are undefined
	// https://tc39.github.io/ecma262/#sec-module-environment-records-getthisbinding
	// resolved.call(null ,module$,module$.exports,dirname,file);
	private ExpressionStatement resolvedCall() {
		IdentifierExpression resolvedIden = new IdentifierExpression("resolved");
		StaticMemberExpression resolvedCall = new StaticMemberExpression(resolvedIden, "call");
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression(moduleIden, "exports");
		IdentifierExpression dirnameIden = new IdentifierExpression("dirname");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		Expression undef = new UnaryExpression(UnaryOperator.Void, new LiteralNumericExpression(0.0));

		ImmutableList<SpreadElementExpression> callParams =
				ImmutableList.of(undef, moduleIden, moduleExports, dirnameIden, fileIden);
		CallExpression callExpression = new CallExpression(resolvedCall, callParams);
		return new ExpressionStatement(callExpression);
	}

	// module$.loaded=true;
	private ExpressionStatement moduleLoaded() {
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberAssignmentTarget moduleLoaded = new StaticMemberAssignmentTarget(moduleIden, "loaded");
		AssignmentExpression assignment = new AssignmentExpression(moduleLoaded, new LiteralBooleanExpression(true));

		return new ExpressionStatement(assignment);
	}

	// return require.cache[file]=module$.exports;
	private ReturnStatement returnRequire() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression(requireIden, "cache");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberAssignmentTarget requireCacheFile = new ComputedMemberAssignmentTarget(requireCache, fileIden);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression(moduleIden, "exports");

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ReturnStatement(Maybe.of(assignment));
	}

	// require.modules={};
	private ExpressionStatement initializeRequireModules() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberAssignmentTarget requireModules = new StaticMemberAssignmentTarget(requireIden, "modules");
		AssignmentExpression assignment =
				new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.empty()));

		return new ExpressionStatement(assignment);
	}

	// require.cache={};
	private ExpressionStatement initializeRequireCache() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberAssignmentTarget requireModules = new StaticMemberAssignmentTarget(requireIden, "cache");
		AssignmentExpression assignment =
				new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.empty()));

		return new ExpressionStatement(assignment);
	}

	// require.resolve=function(file){
	//    return{}.hasOwnProperty.call(require.modules,file)?require.modules[file]:void 0;
	// }
	private ExpressionStatement requireResolveDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberAssignmentTarget requireResolve = new StaticMemberAssignmentTarget(requireIden, "resolve");

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		FormalParameters anonFunctionParam = new FormalParameters(ImmutableList.of(fileBindingIden), Maybe.empty());

		StaticMemberExpression hasOwnProp =
				new StaticMemberExpression(new ObjectExpression(ImmutableList.empty()), "hasOwnProperty");
		StaticMemberExpression hasOwnPropCall = new StaticMemberExpression(hasOwnProp, "call");

		StaticMemberExpression requireModules = new StaticMemberExpression(requireIden, "modules");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ImmutableList<SpreadElementExpression> hasOwnPropCallParams = ImmutableList.of(requireModules, fileIden);
		CallExpression hasOwnPropCallCall = new CallExpression(hasOwnPropCall, hasOwnPropCallParams);
		ComputedMemberExpression requireModulesFile = new ComputedMemberExpression(requireModules, fileIden);

		ConditionalExpression conditionalExpression =
				new ConditionalExpression(hasOwnPropCallCall, requireModulesFile,
						new UnaryExpression(UnaryOperator.Void, new LiteralNumericExpression(0.0)));

		ReturnStatement returnStatement = new ReturnStatement(Maybe.of(conditionalExpression));
		FunctionBody anonFunctionBody = new FunctionBody(ImmutableList.empty(), ImmutableList.of(returnStatement));

		FunctionExpression anonFunction =
				new FunctionExpression(false, false, Maybe.empty(), anonFunctionParam, anonFunctionBody);

		AssignmentExpression assignment = new AssignmentExpression(requireResolve, anonFunction);
		return new ExpressionStatement(assignment);
	}

	// require.define=function(file,fn){
	//    require.modules[file]=fn;
	// };
	private ExpressionStatement requireDefineDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberAssignmentTarget requireDefine = new StaticMemberAssignmentTarget(requireIden, "define");

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		BindingIdentifier fnBindingIden = new BindingIdentifier("fn");
		FormalParameters anonFunctionParam =
				new FormalParameters(ImmutableList.of(fileBindingIden, fnBindingIden), Maybe.empty());

		StaticMemberExpression requireModules = new StaticMemberExpression(requireIden, "modules");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		AssignmentTarget requireModulesFile = new ComputedMemberAssignmentTarget(requireModules, fileIden);
		IdentifierExpression fnIden = new IdentifierExpression("fn");

		AssignmentExpression innerAssignment = new AssignmentExpression(requireModulesFile, fnIden);
		ExpressionStatement innerStatement = new ExpressionStatement(innerAssignment);
		FunctionBody anonFunctionBody =
				new FunctionBody(ImmutableList.empty(), ImmutableList.of(innerStatement));

		FunctionExpression anonFunction =
				new FunctionExpression(false, false, Maybe.empty(), anonFunctionParam, anonFunctionBody);

		AssignmentExpression assignment = new AssignmentExpression(requireDefine, anonFunction);
		return new ExpressionStatement(assignment);
	}

	// return require("/path/to/module.js");
	private ReturnStatement requireCall(String filePath) {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		ImmutableList<SpreadElementExpression> requireParams = ImmutableList.of(new LiteralStringExpression(filePath));
		CallExpression callExpression = new CallExpression(requireIden, requireParams);
		return new ReturnStatement(Maybe.of(callExpression));
	}

	// require.define("/path/to/module.js",function(module,exports,__dirname,__filename){
	//    ...
	// });
	private ExpressionStatement requireDefineStatement(String moduleName, ModuleWrapper module) {
		BindingIdentifier moduleParam = new BindingIdentifier("module");
		BindingIdentifier exportsParam = new BindingIdentifier("exports");
		BindingIdentifier dirnameParam = new BindingIdentifier("__dirname");
		BindingIdentifier filenameParam = new BindingIdentifier("__filename");

		ImmutableList<Parameter> paramsList =
				ImmutableList.of(moduleParam, exportsParam, dirnameParam, filenameParam);

		FormalParameters params = new FormalParameters(paramsList, Maybe.empty());

		ImmutableList<Directive> directives = module.directives;
		ImmutableList<Statement> items = module.items.map(x -> (Statement) x);

		FunctionBody body = new FunctionBody(directives, items);

		FunctionExpression function = new FunctionExpression(false, false, Maybe.empty(), params, body);

		LiteralStringExpression moduleExpression = new LiteralStringExpression(moduleName);

		String defineObject = "define";
		IdentifierExpression requireIdentifier = new IdentifierExpression("require");
		StaticMemberExpression callee = new StaticMemberExpression(requireIdentifier, defineObject);

		ImmutableList<SpreadElementExpression> calleeParams = ImmutableList.of(moduleExpression, function);

		CallExpression callExpression = new CallExpression(callee, calleeParams);

		return new ExpressionStatement(callExpression);
	}
}
