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
package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2017.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.es2017.loader.IResolver;
import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.loader.FileSystemResolver;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.parser.JsError;
import com.shapesecurity.shift.es2017.parser.Parser;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import junit.framework.TestCase;

import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.shapesecurity.bandolier.es2017.TestUtils.testResult;
import static com.shapesecurity.bandolier.es2017.TestUtils.testResultPierced;

public class BundlerTest extends TestCase {
	private static TestLoader loader;
	private static IResolver resolver = new FileSystemResolver();

	static {
		Map<String, String> modules = new HashMap<>();

		modules.put("/root/lib1/js0.js", "var test = 0");

		modules.put("/root/lib1/js1.js", "import {b} from '/root/lib1/js2.js'; export var result = 42 + b");
		modules.put("/root/lib1/js2.js", "export var b = 100");
		modules.put("/root/lib1/js3.js", "import {b} from './js2.js'; export var result = 42 + b");

		modules.put("/root/lib1/js4.js", "import {b} from '../js5.js'; export var result = 42 + b");
		modules.put("/root/js5.js", "export var b = 100");

		modules.put("/root/lib1/js6.js", "import {b} from './js7.js'; export var result = 32 + b");
		modules.put("/root/lib1/js7.js", "import {c} from './js8.js'; export var b = 10 + c");
		modules.put("/root/lib1/js8.js", "export var c = 100");

		modules.put("/root/lib1/js9.js", "import {b} from './js10.js'; export var result = 22 + b");
		modules.put("/root/lib1/js10.js", "import {c} from './js11.js'; export var b = 10 + c");
		modules.put("/root/lib1/js11.js", "import {d} from './js12.js'; export var c = 10 + d");
		modules.put("/root/lib1/js12.js", "export var d = 100");

		modules.put("/root/lib1/even.js", "import {odd} from './odd.js'; export function even(n) { return n == 0 || odd(n-1) }");
		modules.put("/root/lib1/odd.js", "import {even} from './even.js'; export function odd(n) { return n !== 0 || even(n-1) }");
		modules.put("/root/is_even.js", "import {even} from './lib1/even.js'; export var result = even(10);");

		modules.put("/root/lib1/js13.js", "import {b} from '../lib2/js14.js'; import {c} from '../lib2/js15.js'; " +
				"export var result = b + c");
		modules.put("/root/lib2/js14.js", "export var b = 42");
		modules.put("/root/lib2/js15.js", "export var c = 100");

		modules.put("/root/importExport.js", "import {v} from '/root/export.js'; export var result = v + 42");
		modules.put("/root/importExportAllFrom.js", "import {v} from '/root/exportAllFrom.js'; export var result = v + 42");
		modules.put("/root/importExportFrom.js", "import {v} from '/root/exportFrom.js'; export var result = v + 42");
		modules.put("/root/importExportVar.js", "import {v} from '/root/exportVar.js'; export var result = v + 42");
		modules.put("/root/importExportFunction.js", "import {fn} from '/root/exportFunction.js'; export var result = fn(42.1)");
		modules.put("/root/importExportAnon.js", "import fn from '/root/exportAnon.js'; export var result = fn(42.1)");
		modules.put("/root/importExportDefaultFunction.js", "import fn from '/root/exportDefaultFunction.js'; export var result = fn(42.1)");
		modules.put("/root/importExportDefault.js", "import v from '/root/exportDefault.js'; export var result = v + 42");

		modules.put("/root/export.js", "var v = 100; export {v}");
		modules.put("/root/exportAllFrom.js", "export * from '/root/export.js'");
		modules.put("/root/exportFrom.js", "export {v} from '/root/export.js'");
		modules.put("/root/exportVar.js", "export var v = 100");
		modules.put("/root/exportFunction.js", "export function fn(x){return 99.9 + x}");
		modules.put("/root/exportAnon.js", "export default function(x){return 99.9 + x}");
		modules.put("/root/exportDefaultFunction.js", "export default function fn(x){ return 99.9 + x }");
		// modules.put("/root/exportDefaultClass.js", "export default class C{}"); Not dealing with ES6 features for now...
		modules.put("/root/exportDefault.js", "export default 100");
		modules.put("/root/exportDefaultAndName.js", "export default 100; var v = 42; export { v };");

		modules.put("/root/importAll.js", "import * as mod from '/root/export.js'; export var result = mod.v + 42;");
		modules.put("/root/importDefaultAndName.js", "import d, { v } from '/root/exportDefaultAndName.js'; export var result = d + v;");

		modules.put("/root/loadJson.js", "import json from './json.json'; export var result = json.value;");
		modules.put("/root/loadJson.esm", "export { result } from './loadJson.js';");
		modules.put("/root/json.json", "{ \"value\": 1, \"otherValue\": 2 }");

		modules.put("/root/thisIsUndefined.js", "export var result = this;");
		modules.put("/root/circular1.js", "import circ2 from '/root/circular1_dep.js'; export var result = circ2 + 5;");
		modules.put("/root/circular1_dep.js", "export default 7; import {result as circ1} from '/root/circular1.js';");
		modules.put("/root/circular2.js", "export default 7; import {result as circ1} from '/root/circular2_dep.js'; export var result = 7 + circ1;");
		modules.put("/root/circular2_dep.js", "import circ2 from '/root/circular2.js'; export var result = circ2 + 5;");
		modules.put("/root/renaming.js", "import {x, setX} from '/root/renaming_dep.js'; var tempX = x; setX(10);export var result = tempX + x;");
		modules.put("/root/renaming_dep.js", "export var x = 5; export function setX(y) { x = y; }");
		modules.put("/root/normalizing/js1.js", "import './js2.js'; import '../normalizing/js2.js';");
		modules.put("/root/normalizing/js2.js", "");
		loader = new TestLoader(modules);
	}

	public void testReduceImport() throws Exception {
		testDependencyCollector("");
		testDependencyCollector("import '../dep1.js'", "../dep1.js");
		testDependencyCollector("import '../dep1.js'; import 'dep2.js'", "../dep1.js", "dep2.js");
	}

	public void testReduceImportNamespace() throws Exception {
		testDependencyCollector("import * as d1 from '../dep1.js'", "../dep1.js");
		testDependencyCollector("import * as d1 from '../dep1.js'; import * as d2 from 'dep2.js'", "../dep1.js", "dep2.js");
	}

	public void testReduceExportFrom() throws Exception {
		testDependencyCollector("export {v} from '../dep1.js'", "../dep1.js");
		testDependencyCollector("export {v1} from '../dep1.js'; export {v2} from 'dep2.js'", "../dep1.js", "dep2.js");
	}

	public void testReduceExportAllFrom() throws Exception {
		testDependencyCollector("export * from '../dep1.js'", "../dep1.js");
		testDependencyCollector("export * from '../dep1.js'; export * from 'dep2.js'", "../dep1.js", "dep2.js");
	}

	public void testLoadJsonDependency() throws Exception {
		testDependencyCollector("import json from '/json.json'", "/json.json");
	}

	private static void testDependencyCollector(String code, String... dependencies) throws JsError {
		Module module = Parser.parseModule(code);
		ImmutableList<String> deps = ModuleHelper.getModuleDependencies(module);
		ImmutableList<String> expected = ImmutableList.from(dependencies);

		if (!deps.equals(expected)) {
			System.out.println(Arrays.toString(deps.toArray(new String[deps.length])));
			System.out.println(Arrays.toString(expected.toArray(new String[expected.length])));
		}
		assertEquals(deps, expected);
	}

	public void testDeterminism() throws Exception {
		Map<String, String> modules = new HashMap<>();
		modules.put("/js1.js", "import {b} from './js2.js'; import {c} from './js3.js'; export var result = 'a' + b + c");
		modules.put("/js2.js", "import {d} from './js4.js'; export var b = 'b' + d");
		modules.put("/js3.js", "import {d} from './js4.js'; export var c = 'c' + d");
		modules.put("/js4.js", "export var d = 'd'");
		TestLoader localLoader = new TestLoader(modules);

		String actualPierced = TestUtils.toString(TestUtils.bundlePierced(BundlerOptions.SPEC_OPTIONS, "/js1.js", resolver, localLoader));
		String actualPiercedDangerously = TestUtils.toString(TestUtils.bundlePierced(BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS), "/js1.js", resolver, localLoader));
		String actualStandard = TestUtils.toString(TestUtils.bundleStandard(BundlerOptions.SPEC_OPTIONS, "/js1.js", resolver, localLoader));

		String expectedPierced = "(function(t){\n" +
				"\"use strict\";\n" +
				"var d=\"d\";\n" +
				"var c=\"c\"+d;\n" +
				"var b=\"b\"+d;\n" +
				"var result=\"a\"+b+c;\n" +
				"var o={\n" +
				"__proto__:null,result:result};\n" +
				"if(t.Symbol)t.Object.defineProperty(o,t.Symbol.toStringTag,{\n" +
				"value:\"Module\"});\n" +
				"o=t.Object.freeze(o);\n" +
				"return o;\n" +
				"}(this));\n";

		String expectedPiercedDangerously = "(function(t){\n" +
				"\"use strict\";\n" +
				"var d=\"d\";\n" +
				"var c=\"c\"+d;\n" +
				"var b=\"b\"+d;\n" +
				"var result=\"a\"+b+c;\n" +
				"var o={\n" +
				"__proto__:null,result:result};\n" +
				"return o;\n" +
				"}(this));\n";

		String expectedStandard = "(function(global){\n" +
				"\"use strict\";\n" +
				"function require(file,parentModule){\n" +
				"if({\n" +
				"}.hasOwnProperty.call(require.cache,file))return require.cache[file];\n" +
				"var resolved=require.resolve(file);\n" +
				"if(!resolved)throw new Error(\"Failed to resolve module \"+file);\n" +
				"var module$={\n" +
				"id:file,require:require,filename:file,exports:{\n" +
				"},loaded:false,parent:parentModule,children:[]};\n" +
				"if(parentModule)parentModule.children.push(module$);\n" +
				"var dirname=file.slice(0,file.lastIndexOf(\"/\")+1);\n" +
				"require.cache[file]=module$.exports;\n" +
				"resolved.call(void 0,module$,module$.exports,dirname,file);\n" +
				"module$.loaded=true;\n" +
				"return require.cache[file]=module$.exports;\n" +
				"}require.modules={\n" +
				"};\n" +
				"require.cache={\n" +
				"};\n" +
				"require.resolve=function(file){\n" +
				"return{\n" +
				"}.hasOwnProperty.call(require.modules,file)?require.modules[file]:void 0;\n" +
				"};\n" +
				"require.define=function(file,fn){\n" +
				"require.modules[file]=fn;\n" +
				"};\n" +
				"require.define(\"1\",function(module,exports,__dirname,__filename){\n" +
				"var __resolver=require(\"2\",module);\n" +
				"var b=__resolver[\"b\"];\n" +
				"var __resolver=require(\"3\",module);\n" +
				"var c=__resolver[\"c\"];\n" +
				"var result=\"a\"+b+c;\n" +
				"exports[\"result\"]=result;\n" +
				"});\n" +
				"require.define(\"2\",function(module,exports,__dirname,__filename){\n" +
				"var __resolver=require(\"4\",module);\n" +
				"var d=__resolver[\"d\"];\n" +
				"var b=\"b\"+d;\n" +
				"exports[\"b\"]=b;\n" +
				"});\n" +
				"require.define(\"3\",function(module,exports,__dirname,__filename){\n" +
				"var __resolver=require(\"4\",module);\n" +
				"var d=__resolver[\"d\"];\n" +
				"var c=\"c\"+d;\n" +
				"exports[\"c\"]=c;\n" +
				"});\n" +
				"require.define(\"4\",function(module,exports,__dirname,__filename){\n" +
				"var d=\"d\";\n" +
				"exports[\"d\"]=d;\n" +
				"});\n" +
				"return require(\"1\");\n" +
				"}.call(this,this));\n";

		assertEquals(expectedPierced, actualPierced);
		assertEquals(expectedPiercedDangerously, actualPiercedDangerously);
		assertEquals(expectedStandard, actualStandard);
	}

	@Test
	public void testBundle() throws Exception {
		//testResult("/root/lib1/js0.js", null, resolver, loader); // the bundler is innocent!
		testResult("/root/lib1/js1.js", 142.0, resolver, loader); // simple import
		testResult("/root/lib1/js3.js", 142.0, resolver, loader); // simple import / current dir
		testResult("/root/lib1/js4.js", 142.0, resolver, loader); // simple import / parent dir
		testResult("/root/lib1/js6.js", 142.0, resolver, loader); // import chaining
		testResult("/root/lib1/js9.js", 142.0, resolver, loader); // import chaining
		testResult("/root/lib1/js13.js", 142.0, resolver, loader); // import function
		testResult("/root/is_even.js", true, resolver, loader); // cyclic import

		// All the possible ways of importing and exporting:
		testResult("/root/importExport.js", 142.0, resolver, loader); // multiple imports
		testResult("/root/importExportAllFrom.js", 142.0, resolver, loader);
		testResult("/root/importExportFrom.js", 142.0, resolver, loader);
		testResult("/root/importExportVar.js", 142.0, resolver, loader);
		testResult("/root/importExportFunction.js", 142.0, resolver, loader);
		testResult("/root/importExportAnon.js", 142.0, resolver, loader);
		testResult("/root/importExportDefaultFunction.js", 142.0, resolver, loader);
		testResult("/root/importExportDefault.js", 142.0, resolver, loader);
		testResult("/root/importAll.js", 142.0, resolver, loader);
		testResult("/root/importDefaultAndName.js", 142.0, resolver, loader);

		testResult("/root/loadJson.js", 1.0, resolver, loader);
		testResult("/root/loadJson.esm", 1.0, resolver, loader);

		testResult("/root/thisIsUndefined.js", null, resolver, loader);
		testResultPierced("/root/circular1.js", 12.0, resolver, loader);
		testResultPierced("/root/circular2.js", Double.NaN, resolver, loader);
		testResultPierced("/root/renaming.js", 15.0, resolver, loader);
	}

	@Test
	public void testBundleModule() throws Exception {
		Path path = Paths.get("/root/lib1/js1.js");
		String source = loader.loadResource(path);
		Script bundled = Bundler.bundleModule(Parser.parseModule(source), path, resolver, loader, new StandardModuleBundler());

		String newProgramText = TestUtils.toString(bundled);
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

		Object result;
		try {
			result = engine.eval(newProgramText);
			result = ((ScriptObjectMirror)result).get("result");
			// resolving weird nashorn inconsistency
			if (result instanceof Integer) {
				result = ((Integer) result).doubleValue();
			}
		} catch (ScriptException e) {
			System.out.println(newProgramText);
			throw e;
		}
		System.out.println(result);
	}

	@Test
	public void testLoadDependencies() throws Exception {
		Path path = Paths.get("/root/normalizing/js1.js");
		String source = loader.loadResource(path);

		Map<String, Module> dependencies = Bundler.loadDependencies(Parser.parseModule(source), path, resolver, loader);
		ImmutableList<String> dependentPaths = ImmutableList.from(dependencies.keySet().stream().sorted(String::compareTo).collect(Collectors.toList()));
		assertEquals(ImmutableList.of("/root/normalizing/js1.js", "/root/normalizing/js2.js"), dependentPaths);
	}

}