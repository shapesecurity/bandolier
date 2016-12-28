package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.ast.operators.BinaryOperator;
import com.shapesecurity.shift.ast.operators.UnaryOperator;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardModuleBundler extends ModuleBundler {

	private final Map<String, Module> rewrittenModules = new HashMap<>();
	private final Map<String, String> pathMapping = new HashMap<>();

	public StandardModuleBundler(Map<String, Module> modules) {
		super(modules);

		// rather than bundle with absolute paths (a potential information leak) create a mapping
		// of absolute paths to a unique name
		Integer moduleCount = 0;
		for (String absPath : this.modules.keySet()) {
			this.pathMapping.put(absPath, (++moduleCount).toString());
		}
		ImportMappingRewriter importMappingRewriter = new ImportMappingRewriter(this.pathMapping);
		this.modules.forEach((absPath, m) -> this.rewrittenModules.put(this.pathMapping.get(absPath), importMappingRewriter.rewrite(m)));
	}

	@NotNull
	@Override
	public Script bundleEntrypoint(String entry) {
		ExpressionStatement bundled = anonymousFunctionCall(this.pathMapping.get(entry));
		return new Script(ImmutableList.empty(), ImmutableList.of(bundled));
	}

	//(function(global){ ... }.call(this, this));
	private ExpressionStatement anonymousFunctionCall(String rootPath) {
		StaticMemberExpression anonymousCall =
				new StaticMemberExpression("call", anonymousFunctionExpression(rootPath));
		ImmutableList<SpreadElementExpression> params = ImmutableList.of(new ThisExpression(), new ThisExpression());
		CallExpression callExpression = new CallExpression(anonymousCall, params);

		return new ExpressionStatement(callExpression);
	}

	// function(global) {...}
	private FunctionExpression anonymousFunctionExpression(String rootPath) {
		BindingIdentifier globalIden = new BindingIdentifier("global");
		FormalParameters params = new FormalParameters(ImmutableList.of(globalIden), Maybe.empty());

		LinkedList<Statement> requireStatements =
				this.rewrittenModules.entrySet().stream().map(x -> {
					Node reduced = ImportExportTransformer.transformModule(x.getValue());
					return requireDefineStatement(x.getKey(), (Module) reduced);
				}).collect(Collectors.toCollection(LinkedList::new));
		ImmutableList<Statement> statements = ImmutableList.from(requireStatements);
		statements = statements.append(ImmutableList.of(requireCall(rootPath)));
		statements = statements.cons(requireDefineDefinition());
		statements = statements.cons(requireResolveDefinition());
		statements = statements.cons(initializeRequireCache());
		statements = statements.cons(initializeRequireModules());
		statements = statements.cons(requireFunctionDeclaration());

		FunctionBody body = new FunctionBody(ImmutableList.of(new Directive("use strict")), statements);

		return new FunctionExpression(Maybe.empty(), false, params, body);
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

		return new FunctionDeclaration(requireIden, false, params, body);
	}

	//if({}.hasOwnProperty.call(require.cache,file)) return require.cache[file];
	private IfStatement checkCacheIf() {
		ObjectExpression objectExpression = new ObjectExpression(ImmutableList.empty());
		StaticMemberExpression objHasOwnProp = new StaticMemberExpression("hasOwnProperty", objectExpression);
		StaticMemberExpression objHasOwnPropCall = new StaticMemberExpression("call", objHasOwnProp);

		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.of(requireCache, fileIden);
		CallExpression callExpression = new CallExpression(objHasOwnPropCall, callParams);

		ReturnStatement returnStatement = new ReturnStatement(Maybe.of(requireCacheFile));

		return new IfStatement(callExpression, returnStatement, Maybe.empty());
	}

	//var resolved=require.resolve(file);
	private VariableDeclarationStatement resolvedDeclaration() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireResolve = new StaticMemberExpression("resolve", requireIden);
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
		BinaryExpression errorExpression = new BinaryExpression(BinaryOperator.Plus, errorMsg, fileIden);

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

		DataProperty idProp = new DataProperty(new IdentifierExpression("file"), new StaticPropertyName("id"));
		DataProperty requireProp = new DataProperty(new IdentifierExpression("require"), new StaticPropertyName("require"));
		DataProperty fileNameProp = new DataProperty(new IdentifierExpression("file"), new StaticPropertyName("filename"));
		DataProperty exportsProp = new DataProperty(new ObjectExpression(ImmutableList.empty()), new StaticPropertyName("exports"));
		DataProperty loadedProperty = new DataProperty(new LiteralBooleanExpression(false), new StaticPropertyName("loaded"));
		DataProperty parentProp = new DataProperty(new IdentifierExpression("parentModule"), new StaticPropertyName("parent"));
		DataProperty childrenProp = new DataProperty(new ArrayExpression(ImmutableList.empty()), new StaticPropertyName("children"));
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
		StaticMemberExpression parentModuleChildren = new StaticMemberExpression("children", parentModuleIden);
		StaticMemberExpression parentModuleChildrenPush = new StaticMemberExpression("push", parentModuleChildren);

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.of(moduleIden);
		CallExpression callExpression = new CallExpression(parentModuleChildrenPush, callParams);
		ExpressionStatement callStatement = new ExpressionStatement(callExpression);

		return new IfStatement(parentModuleIden, callStatement, Maybe.empty());
	}

	// var dirname=file.slice(0,file.lastIndexOf("/")+1);
	private VariableDeclarationStatement dirnameDeclaration() {
		IdentifierExpression fileIden = new IdentifierExpression("file");
		StaticMemberExpression fileLastIndOf = new StaticMemberExpression("lastIndexOf", fileIden);
		LiteralStringExpression slashStr = new LiteralStringExpression("/");
		ImmutableList<SpreadElementExpression> lastIndOfParams = ImmutableList.of(slashStr);
		CallExpression lastIndOfCall = new CallExpression(fileLastIndOf, lastIndOfParams);

		BinaryExpression sliceSecondParam =
				new BinaryExpression(BinaryOperator.Plus, lastIndOfCall, new LiteralNumericExpression(1.0));
		ImmutableList<SpreadElementExpression> sliceParams =
				ImmutableList.of(new LiteralNumericExpression(0.0), sliceSecondParam);
		StaticMemberExpression fileSlice = new StaticMemberExpression("slice", fileIden);
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
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ExpressionStatement(assignment);
	}

	// top-level this bindings in a module are undefined
	// https://tc39.github.io/ecma262/#sec-module-environment-records-getthisbinding
	// resolved.call(null ,module$,module$.exports,dirname,file);
	private ExpressionStatement resolvedCall() {
		IdentifierExpression resolvedIden = new IdentifierExpression("resolved");
		StaticMemberExpression resolvedCall = new StaticMemberExpression("call", resolvedIden);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);
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
		StaticMemberExpression moduleLoaded = new StaticMemberExpression("loaded", moduleIden);
		AssignmentExpression assignment = new AssignmentExpression(moduleLoaded, new LiteralBooleanExpression(true));

		return new ExpressionStatement(assignment);
	}

	// return require.cache[file]=module$.exports;
	private ReturnStatement returnRequire() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ReturnStatement(Maybe.of(assignment));
	}

	// require.modules={};
	private ExpressionStatement initializeRequireModules() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		AssignmentExpression assignment =
				new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.empty()));

		return new ExpressionStatement(assignment);
	}

	// require.cache={};
	private ExpressionStatement initializeRequireCache() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireModules = new StaticMemberExpression("cache", requireIden);
		AssignmentExpression assignment =
				new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.empty()));

		return new ExpressionStatement(assignment);
	}

	// require.resolve=function(file){
	//    return{}.hasOwnProperty.call(require.modules,file)?require.modules[file]:void 0;
	// }
	private ExpressionStatement requireResolveDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireResolve = new StaticMemberExpression("resolve", requireIden);

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		FormalParameters anonFunctionParam = new FormalParameters(ImmutableList.of(fileBindingIden), Maybe.empty());

		StaticMemberExpression hasOwnProp =
				new StaticMemberExpression("hasOwnProperty", new ObjectExpression(ImmutableList.empty()));
		StaticMemberExpression hasOwnPropCall = new StaticMemberExpression("call", hasOwnProp);

		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ImmutableList<SpreadElementExpression> hasOwnPropCallParams = ImmutableList.of(requireModules, fileIden);
		CallExpression hasOwnPropCallCall = new CallExpression(hasOwnPropCall, hasOwnPropCallParams);
		ComputedMemberExpression requireModulesFile = new ComputedMemberExpression(fileIden, requireModules);

		ConditionalExpression conditionalExpression =
				new ConditionalExpression(hasOwnPropCallCall, requireModulesFile,
						new UnaryExpression(UnaryOperator.Void, new LiteralNumericExpression(0.0)));

		ReturnStatement returnStatement = new ReturnStatement(Maybe.of(conditionalExpression));
		FunctionBody anonFunctionBody = new FunctionBody(ImmutableList.empty(), ImmutableList.of(returnStatement));

		FunctionExpression anonFunction =
				new FunctionExpression(Maybe.empty(), false, anonFunctionParam, anonFunctionBody);

		AssignmentExpression assignment = new AssignmentExpression(requireResolve, anonFunction);
		return new ExpressionStatement(assignment);
	}

	// require.define=function(file,fn){
	//    require.modules[file]=fn;
	// };
	private ExpressionStatement requireDefineDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireDefine = new StaticMemberExpression("define", requireIden);

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		BindingIdentifier fnBindingIden = new BindingIdentifier("fn");
		FormalParameters anonFunctionParam =
				new FormalParameters(ImmutableList.of(fileBindingIden, fnBindingIden), Maybe.empty());

		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireModulesFile = new ComputedMemberExpression(fileIden, requireModules);
		IdentifierExpression fnIden = new IdentifierExpression("fn");

		AssignmentExpression innerAssignment = new AssignmentExpression(requireModulesFile, fnIden);
		ExpressionStatement innerStatement = new ExpressionStatement(innerAssignment);
		FunctionBody anonFunctionBody =
				new FunctionBody(ImmutableList.empty(), ImmutableList.of(innerStatement));

		FunctionExpression anonFunction =
				new FunctionExpression(Maybe.empty(), false, anonFunctionParam, anonFunctionBody);

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
	private ExpressionStatement requireDefineStatement(String moduleName, Module module) {
		BindingBindingWithDefault moduleParam = new BindingIdentifier("module");
		BindingBindingWithDefault exportsParam = new BindingIdentifier("exports");
		BindingBindingWithDefault dirnameParam = new BindingIdentifier("__dirname");
		BindingBindingWithDefault filenameParam = new BindingIdentifier("__filename");

		ImmutableList<BindingBindingWithDefault> paramsList =
				ImmutableList.of(moduleParam, exportsParam, dirnameParam, filenameParam);

		FormalParameters params = new FormalParameters(paramsList, Maybe.empty());

		ImmutableList<Directive> directives = module.getDirectives();
		ImmutableList<Statement> items = module.getItems().map(x -> (Statement) x);

		FunctionBody body = new FunctionBody(directives, items);

		FunctionExpression function = new FunctionExpression(Maybe.empty(), false, params, body);

		LiteralStringExpression moduleExpression = new LiteralStringExpression(moduleName);

		String defineObject = "define";
		IdentifierExpression requireIdentifier = new IdentifierExpression("require");
		StaticMemberExpression callee = new StaticMemberExpression(defineObject, requireIdentifier);

		ImmutableList<SpreadElementExpression> calleeParams = ImmutableList.of(moduleExpression, function);

		CallExpression callExpression = new CallExpression(callee, calleeParams);

		return new ExpressionStatement(callExpression);
	}
}
