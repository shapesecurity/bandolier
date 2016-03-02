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
import com.shapesecurity.shift.ast.ArrayExpression;
import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.BinaryExpression;
import com.shapesecurity.shift.ast.BindingBindingWithDefault;
import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.ConditionalExpression;
import com.shapesecurity.shift.ast.DataProperty;
import com.shapesecurity.shift.ast.Directive;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.FormalParameters;
import com.shapesecurity.shift.ast.FunctionBody;
import com.shapesecurity.shift.ast.FunctionDeclaration;
import com.shapesecurity.shift.ast.FunctionExpression;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.IfStatement;
import com.shapesecurity.shift.ast.LiteralBooleanExpression;
import com.shapesecurity.shift.ast.LiteralNumericExpression;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.NewExpression;
import com.shapesecurity.shift.ast.Node;
import com.shapesecurity.shift.ast.ObjectExpression;
import com.shapesecurity.shift.ast.ObjectProperty;
import com.shapesecurity.shift.ast.ReturnStatement;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.SpreadElementExpression;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.ast.StaticMemberExpression;
import com.shapesecurity.shift.ast.StaticPropertyName;
import com.shapesecurity.shift.ast.ThisExpression;
import com.shapesecurity.shift.ast.ThrowStatement;
import com.shapesecurity.shift.ast.UnaryExpression;
import com.shapesecurity.shift.ast.VariableDeclaration;
import com.shapesecurity.shift.ast.VariableDeclarationKind;
import com.shapesecurity.shift.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.ast.VariableDeclarator;
import com.shapesecurity.shift.ast.operators.BinaryOperator;
import com.shapesecurity.shift.ast.operators.UnaryOperator;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.visitor.Director;
import com.shapesecurity.bandolier.loader.FileLoader;
import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class Bundler {

	/**
	 * Bundles the module at the specified path using the default resolver and loaders
	 * @param filePath path to the module
	 * @return a bundled script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull Script bundle(@NotNull Path filePath) throws ModuleLoaderException {
		return bundle(filePath, new FileSystemResolver(), new FileLoader());
	}

	/**
	 * Bundles the module specified by the given path and its dependencies and returns the resulting
	 * Script.
	 *
	 * @param filePath is the path to the input entry point module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException when the module fails to load
	 */
	public static @NotNull Script bundle(@NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			return bundleString(loader.loadResource(filePath), filePath, resolver, loader);
		} catch (IOException e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}

	/**
	 * Bundles the module provided as a string and along with its dependencies and returns the resulting
	 * Script.
	 * @param mod the string of the module
	 * @param filePath path to the module
	 * @param resolver how to resolve paths
	 * @param loader how to load modules
	 * @return the resulting script
	 * @throws ModuleLoaderException
	 */
	public static @NotNull Script bundleString(@NotNull String mod, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader) throws ModuleLoaderException {
		try {
			Module module = Parser.parseModule(mod);
			Map<String, Module> modules = loadDependencies(module, filePath, resolver, loader);

			// rather than bundle with absolute paths (a potential information leak) create a mapping
			// of absolute paths to a unique name
			Map<String, String> importPathGensymMap = new HashMap<>();
			Integer moduleCount = 0;
			for (String absPath : modules.keySet()) {
				importPathGensymMap.put(absPath, (++moduleCount).toString());
			}
			ImportMappingRewriter importMappingRewriter = new ImportMappingRewriter(importPathGensymMap);

			Map<String, Module> importMappedModules = new HashMap<>();
			modules.forEach((absPath, m) -> {
				importMappedModules.put(importPathGensymMap.get(absPath), importMappingRewriter.rewrite(m));
			});

			ExpressionStatement bundled = bundleModules(importPathGensymMap.get(filePath.toString()), importMappedModules);
			return new Script(ImmutableList.nil(), ImmutableList.from(bundled));
		} catch (JsError e) {
			throw new ModuleLoaderException(filePath.toString(), e);
		}
	}



	/**
	 * Recursively loads all the modules referenced by the input module.
	 *
	 * @param filePath is the path to the input module.
	 * @param resolver how to resolve the path
	 * @param loader   how to load
	 * @return is a map from module names (path to modules) to the loaded modules.
	 * @throws ModuleLoaderException when the module fails to load
	 */
	private static @NotNull Map<String, Module> loadDependencies(@NotNull Module module, @NotNull Path filePath, @NotNull IResolver resolver, @NotNull IResourceLoader loader)
		throws ModuleLoaderException {

		Map<String, Module> loadedModules = new HashMap<>();
		LinkedList<String> toLoad = new LinkedList<>();
		ImportResolvingRewriter rewriter = new ImportResolvingRewriter(resolver);
		Module rewritten = rewriter.rewrite(module, filePath.getParent());
		loadedModules.put(filePath.toString(), rewritten);
		toLoad.add(filePath.toString());

		while (!toLoad.isEmpty()) {
			String root = toLoad.remove();
			String[] dependencies = getModuleDirectDependencies(loadedModules.get(root));
			for (String dependency : dependencies) {
				if (!loadedModules.containsKey(dependency)) {
					try {
						module = Parser.parseModule(loader.loadResource(Paths.get(dependency)));
					} catch (IOException | JsError e) {
						throw new ModuleLoaderException(dependency, e);
					}
					rewritten = rewriter.rewrite(module, Paths.get(dependency).getParent());
					loadedModules.put(dependency, rewritten);
					toLoad.add(dependency);
				}
			}
		}

		return loadedModules;
	}

	/**
	 * Returns a list of modules that are directly imported by the input module.
	 *
	 * @param module is the input module.
	 * @return a list of imported modules.
	 */
	private static String[] getModuleDirectDependencies(Module module) {
		ImmutableList<String> imports = Director.reduceModule(new ModuleDependencyCollector(), module);
		return imports.toArray(new String[0]);
	}

	@NotNull
	private static ExpressionStatement bundleModules(@NotNull String filePath, @NotNull Map<String, Module> modules) {
		return anonymousFunctionCall(filePath, modules);
	}

	/* The following functions create the wrapping (mostly static) code in the output script. */

	//(function(global){ ... }.call(this, this));
	private static ExpressionStatement anonymousFunctionCall(String rootPath, Map<String, Module> modules) {
		StaticMemberExpression anonymousCall =
			new StaticMemberExpression("call", anonymousFunctionExpression(rootPath, modules));
		ImmutableList<SpreadElementExpression> params = ImmutableList.from(new ThisExpression(), new ThisExpression());
		CallExpression callExpression = new CallExpression(anonymousCall, params);

		return new ExpressionStatement(callExpression);
	}

	// function(global) {...}
	private static FunctionExpression anonymousFunctionExpression(String rootPath, Map<String, Module> modules) {
		BindingIdentifier globalIden = new BindingIdentifier("global");
		FormalParameters params = new FormalParameters(ImmutableList.from(globalIden), Maybe.nothing());

		LinkedList<Statement> requireStatements =
			modules.entrySet().stream().map(x -> {
				Node reduced = ImportExportTransformer.transformModule(x.getValue());
				return requireDefineStatement(x.getKey(), (Module) reduced);
			}).collect(Collectors.toCollection(LinkedList::new));
		ImmutableList<Statement> statements = ImmutableList.from(requireStatements);
		statements = statements.append(ImmutableList.from(requireCall(rootPath)));
		statements = statements.cons(requireDefineDefinition());
		statements = statements.cons(requireResolveDefinition());
		statements = statements.cons(initializeRequireCache());
		statements = statements.cons(initializeRequireModules());
		statements = statements.cons(requireFunctionDeclaration());

		FunctionBody body = new FunctionBody(ImmutableList.from(new Directive("use strict")), statements);

		return new FunctionExpression(Maybe.nothing(), false, params, body);
	}

	//function require(file,parentModule){ ... }
	private static FunctionDeclaration requireFunctionDeclaration() {
		BindingIdentifier requireIden = new BindingIdentifier("require");
		BindingIdentifier fileParamIden = new BindingIdentifier("file");
		BindingIdentifier parentModuleIden = new BindingIdentifier("parentModule");

		FormalParameters params = new FormalParameters(ImmutableList.from(fileParamIden, parentModuleIden), Maybe.nothing());

		ImmutableList<Statement> statements = ImmutableList.nil();
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

		FunctionBody body = new FunctionBody(ImmutableList.nil(), statements);

		return new FunctionDeclaration(requireIden, false, params, body);
	}

	//if({}.hasOwnProperty.call(require.cache,file)) return require.cache[file];
	private static IfStatement checkCacheIf() {
		ObjectExpression objectExpression = new ObjectExpression(ImmutableList.nil());
		StaticMemberExpression objHasOwnProp = new StaticMemberExpression("hasOwnProperty", objectExpression);
		StaticMemberExpression objHasOwnPropCall = new StaticMemberExpression("call", objHasOwnProp);

		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.from(requireCache, fileIden);
		CallExpression callExpression = new CallExpression(objHasOwnPropCall, callParams);

		ReturnStatement returnStatement = new ReturnStatement(Maybe.just(requireCacheFile));

		return new IfStatement(callExpression, returnStatement, Maybe.nothing());
	}

	//var resolved=require.resolve(file);
	private static VariableDeclarationStatement resolvedDeclaration() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireResolve = new StaticMemberExpression("resolve", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ImmutableList<SpreadElementExpression> callParams = ImmutableList.from(fileIden);
		CallExpression callExpression = new CallExpression(requireResolve, callParams);

		BindingIdentifier resolvedIden = new BindingIdentifier("resolved");
		VariableDeclarator resolvedDecl = new VariableDeclarator(resolvedIden, Maybe.just(callExpression));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.from(resolvedDecl);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	//if(!resolved)throw new Error("Failed to resolve module "+file);
	private static IfStatement checkResolvedIf() {
		LiteralStringExpression errorMsg = new LiteralStringExpression("Failed to resolve module ");
		IdentifierExpression fileIden = new IdentifierExpression("file");
		BinaryExpression errorExpression = new BinaryExpression(BinaryOperator.Plus, errorMsg, fileIden);

		IdentifierExpression errorIden = new IdentifierExpression("Error");
		ImmutableList<SpreadElementExpression> errorParams = ImmutableList.from(errorExpression);
		NewExpression newExpression = new NewExpression(errorIden, errorParams);
		ThrowStatement throwStatement = new ThrowStatement(newExpression);

		IdentifierExpression resolvedIden = new IdentifierExpression("resolved");
		UnaryExpression testExpression = new UnaryExpression(UnaryOperator.LogicalNot, resolvedIden);

		return new IfStatement(testExpression, throwStatement, Maybe.nothing());
	}

	//var module$={
	//  id:file,
	//  require:require,
	//  filename:file,exports:{},
	//  loaded:false,
	//  parent:parentModule,
	//  children:[]
	// };
	private static VariableDeclarationStatement moduleObjectDeclaration() {
		BindingIdentifier moduleIden = new BindingIdentifier("module$");

		DataProperty idProp = new DataProperty(new IdentifierExpression("file"), new StaticPropertyName("id"));
		DataProperty requireProp = new DataProperty(new IdentifierExpression("require"), new StaticPropertyName("require"));
		DataProperty fileNameProp = new DataProperty(new IdentifierExpression("file"), new StaticPropertyName("filename"));
		DataProperty exportsProp = new DataProperty(new ObjectExpression(ImmutableList.nil()), new StaticPropertyName("exports"));
		DataProperty loadedProperty = new DataProperty(new LiteralBooleanExpression(false), new StaticPropertyName("loaded"));
		DataProperty parentProp = new DataProperty(new IdentifierExpression("parentModule"), new StaticPropertyName("parent"));
		DataProperty childrenProp = new DataProperty(new ArrayExpression(ImmutableList.nil()), new StaticPropertyName("children"));
		ImmutableList<ObjectProperty> properties =
			ImmutableList.from(idProp, requireProp, fileNameProp, exportsProp, loadedProperty, parentProp, childrenProp);
		ObjectExpression object = new ObjectExpression(properties);

		VariableDeclarator declarator = new VariableDeclarator(moduleIden, Maybe.just(object));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.from(declarator);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);
		return new VariableDeclarationStatement(declaration);
	}

	// if(parentModule) parentModule.children.push(module$);
	private static IfStatement checkParentModuleIf() {
		IdentifierExpression parentModuleIden = new IdentifierExpression("parentModule");
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression parentModuleChildren = new StaticMemberExpression("children", parentModuleIden);
		StaticMemberExpression parentModuleChildrenPush = new StaticMemberExpression("push", parentModuleChildren);

		ImmutableList<SpreadElementExpression> callParams = ImmutableList.from(moduleIden);
		CallExpression callExpression = new CallExpression(parentModuleChildrenPush, callParams);
		ExpressionStatement callStatement = new ExpressionStatement(callExpression);

		return new IfStatement(parentModuleIden, callStatement, Maybe.nothing());
	}

	// var dirname=file.slice(0,file.lastIndexOf("/")+1);
	private static VariableDeclarationStatement dirnameDeclaration() {
		IdentifierExpression fileIden = new IdentifierExpression("file");
		StaticMemberExpression fileLastIndOf = new StaticMemberExpression("lastIndexOf", fileIden);
		LiteralStringExpression slashStr = new LiteralStringExpression("/");
		ImmutableList<SpreadElementExpression> lastIndOfParams = ImmutableList.from(slashStr);
		CallExpression lastIndOfCall = new CallExpression(fileLastIndOf, lastIndOfParams);

		BinaryExpression sliceSecondParam =
			new BinaryExpression(BinaryOperator.Plus, lastIndOfCall, new LiteralNumericExpression(1.0));
		ImmutableList<SpreadElementExpression> sliceParams =
			ImmutableList.from(new LiteralNumericExpression(0.0), sliceSecondParam);
		StaticMemberExpression fileSlice = new StaticMemberExpression("slice", fileIden);
		CallExpression fileSliceCall = new CallExpression(fileSlice, sliceParams);

		BindingIdentifier dirnameIden = new BindingIdentifier("dirname");
		VariableDeclarator declarator = new VariableDeclarator(dirnameIden, Maybe.just(fileSliceCall));
		ImmutableList<VariableDeclarator> declarators = ImmutableList.from(declarator);

		VariableDeclaration declaration = new VariableDeclaration(VariableDeclarationKind.Var, declarators);

		return new VariableDeclarationStatement(declaration);
	}

	// require.cache[file]=module$.exports;
	private static ExpressionStatement cacheExports() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ExpressionStatement(assignment);
	}

	// resolved.call(module$.exports,module$,module$.exports,dirname,file);
	private static ExpressionStatement resolvedCall() {
		IdentifierExpression resolvedIden = new IdentifierExpression("resolved");
		StaticMemberExpression resolvedCall = new StaticMemberExpression("call", resolvedIden);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);
		IdentifierExpression dirnameIden = new IdentifierExpression("dirname");
		IdentifierExpression fileIden = new IdentifierExpression("file");

		ImmutableList<SpreadElementExpression> callParams =
			ImmutableList.from(moduleExports, moduleIden, moduleExports, dirnameIden, fileIden);
		CallExpression callExpression = new CallExpression(resolvedCall, callParams);
		return new ExpressionStatement(callExpression);
	}

	// module$.loaded=true;
	private static ExpressionStatement moduleLoaded() {
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleLoaded = new StaticMemberExpression("loaded", moduleIden);
		AssignmentExpression assignment = new AssignmentExpression(moduleLoaded, new LiteralBooleanExpression(true));

		return new ExpressionStatement(assignment);
	}

	// return require.cache[file]=module$.exports;
	private static ReturnStatement returnRequire() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireCache = new StaticMemberExpression("cache", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireCacheFile = new ComputedMemberExpression(fileIden, requireCache);
		IdentifierExpression moduleIden = new IdentifierExpression("module$");
		StaticMemberExpression moduleExports = new StaticMemberExpression("exports", moduleIden);

		AssignmentExpression assignment = new AssignmentExpression(requireCacheFile, moduleExports);
		return new ReturnStatement(Maybe.just(assignment));
	}

	// require.modules={};
	private static ExpressionStatement initializeRequireModules() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		AssignmentExpression assignment =
			new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.nil()));

		return new ExpressionStatement(assignment);
	}

	// require.cache={};
	private static ExpressionStatement initializeRequireCache() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireModules = new StaticMemberExpression("cache", requireIden);
		AssignmentExpression assignment =
			new AssignmentExpression(requireModules, new ObjectExpression(ImmutableList.nil()));

		return new ExpressionStatement(assignment);
	}

	// require.resolve=function(file){
	//    return{}.hasOwnProperty.call(require.modules,file)?require.modules[file]:void 0;
	// }
	private static ExpressionStatement requireResolveDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireResolve = new StaticMemberExpression("resolve", requireIden);

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		FormalParameters anonFunctionParam = new FormalParameters(ImmutableList.from(fileBindingIden), Maybe.nothing());

		StaticMemberExpression hasOwnProp =
			new StaticMemberExpression("hasOwnProperty", new ObjectExpression(ImmutableList.nil()));
		StaticMemberExpression hasOwnPropCall = new StaticMemberExpression("call", hasOwnProp);

		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ImmutableList<SpreadElementExpression> hasOwnPropCallParams = ImmutableList.from(requireModules, fileIden);
		CallExpression hasOwnPropCallCall = new CallExpression(hasOwnPropCall, hasOwnPropCallParams);
		ComputedMemberExpression requireModulesFile = new ComputedMemberExpression(fileIden, requireModules);

		ConditionalExpression conditionalExpression =
			new ConditionalExpression(hasOwnPropCallCall, requireModulesFile,
				new UnaryExpression(UnaryOperator.Void, new LiteralNumericExpression(0.0)));

		ReturnStatement returnStatement = new ReturnStatement(Maybe.just(conditionalExpression));
		FunctionBody anonFunctionBody = new FunctionBody(ImmutableList.nil(), ImmutableList.from(returnStatement));

		FunctionExpression anonFunction =
			new FunctionExpression(Maybe.nothing(), false, anonFunctionParam, anonFunctionBody);

		AssignmentExpression assignment = new AssignmentExpression(requireResolve, anonFunction);
		return new ExpressionStatement(assignment);
	}

	// require.define=function(file,fn){
	//    require.modules[file]=fn;
	// };
	private static ExpressionStatement requireDefineDefinition() {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		StaticMemberExpression requireDefine = new StaticMemberExpression("define", requireIden);

		BindingIdentifier fileBindingIden = new BindingIdentifier("file");
		BindingIdentifier fnBindingIden = new BindingIdentifier("fn");
		FormalParameters anonFunctionParam =
			new FormalParameters(ImmutableList.from(fileBindingIden, fnBindingIden), Maybe.nothing());

		StaticMemberExpression requireModules = new StaticMemberExpression("modules", requireIden);
		IdentifierExpression fileIden = new IdentifierExpression("file");
		ComputedMemberExpression requireModulesFile = new ComputedMemberExpression(fileIden, requireModules);
		IdentifierExpression fnIden = new IdentifierExpression("fn");

		AssignmentExpression innerAssignment = new AssignmentExpression(requireModulesFile, fnIden);
		ExpressionStatement innerStatement = new ExpressionStatement(innerAssignment);
		FunctionBody anonFunctionBody =
			new FunctionBody(ImmutableList.nil(), ImmutableList.from(innerStatement));

		FunctionExpression anonFunction =
			new FunctionExpression(Maybe.nothing(), false, anonFunctionParam, anonFunctionBody);

		AssignmentExpression assignment = new AssignmentExpression(requireDefine, anonFunction);
		return new ExpressionStatement(assignment);
	}

	// return require("/path/to/module.js");
	private static ReturnStatement requireCall(String filePath) {
		IdentifierExpression requireIden = new IdentifierExpression("require");
		ImmutableList<SpreadElementExpression> requireParams = ImmutableList.from(new LiteralStringExpression(filePath));
		CallExpression callExpression = new CallExpression(requireIden, requireParams);
		return new ReturnStatement(Maybe.just(callExpression));
	}

	// require.define("/path/to/module.js",function(module,exports,__dirname,__filename){
	//    ...
	// });
	private static ExpressionStatement requireDefineStatement(String moduleName, Module module) {
		BindingBindingWithDefault moduleParam = new BindingIdentifier("module");
		BindingBindingWithDefault exportsParam = new BindingIdentifier("exports");
		BindingBindingWithDefault dirnameParam = new BindingIdentifier("__dirname");
		BindingBindingWithDefault filenameParam = new BindingIdentifier("__filename");

		ImmutableList<BindingBindingWithDefault> paramsList =
			ImmutableList.list(moduleParam, exportsParam, dirnameParam, filenameParam);

		FormalParameters params = new FormalParameters(paramsList, Maybe.nothing());

		ImmutableList<Directive> directives = module.getDirectives();
		ImmutableList<Statement> items = module.getItems().map(x -> (Statement) x);

		FunctionBody body = new FunctionBody(directives, items);

		FunctionExpression function = new FunctionExpression(Maybe.nothing(), false, params, body);

		LiteralStringExpression moduleExpression = new LiteralStringExpression(moduleName);

		String defineObject = "define";
		IdentifierExpression requireIdentifier = new IdentifierExpression("require");
		StaticMemberExpression callee = new StaticMemberExpression(defineObject, requireIdentifier);

		ImmutableList<SpreadElementExpression> calleeParams = ImmutableList.from(moduleExpression, function);

		CallExpression callExpression = new CallExpression(callee, calleeParams);

		return new ExpressionStatement(callExpression);
	}
}
