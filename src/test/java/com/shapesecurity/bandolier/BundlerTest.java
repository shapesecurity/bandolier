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

import com.shapesecurity.bandolier.loader.FileSystemResolver;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2016.ast.Module;
import com.shapesecurity.shift.es2016.parser.JsError;
import com.shapesecurity.shift.es2016.parser.Parser;

import junit.framework.TestCase;

import org.junit.Test;

import static com.shapesecurity.bandolier.TestUtils.testResult;

public class BundlerTest extends TestCase {
	private static TestLoader loader;
	private static IResolver resolver = new FileSystemResolver();

	static {
		HashTable<String, String> modules = HashTable.emptyUsingEquality();

		modules = modules.put("/root/lib1/js0.js", "var test = 0")

				.put("/root/lib1/js1.js", "import {b} from '/root/lib1/js2.js'; export var result = 42 + b")
				.put("/root/lib1/js2.js", "export var b = 100")
				.put("/root/lib1/js3.js", "import {b} from './js2.js'; export var result = 42 + b")
				.put("/root/lib1/js4.js", "import {b} from '../js5.js'; export var result = 42 + b")

				.put("/root/js5.js", "export var b = 100")

				.put("/root/lib1/js6.js", "import {b} from './js7.js'; export var result = 32 + b")
				.put("/root/lib1/js7.js", "import {c} from './js8.js'; export var b = 10 + c")
				.put("/root/lib1/js8.js", "export var c = 100")

				.put("/root/lib1/js9.js", "import {b} from './js10.js'; export var result = 22 + b")
				.put("/root/lib1/js10.js", "import {c} from './js11.js'; export var b = 10 + c")
				.put("/root/lib1/js11.js", "import {d} from './js12.js'; export var c = 10 + d")
				.put("/root/lib1/js12.js", "export var d = 100")

				.put("/root/lib1/even.js", "import {odd} from './odd.js'; export function even(n) { return n == 0 || odd(n-1) }")
				.put("/root/lib1/odd.js", "import {even} from './even.js'; export function odd(n) { return n !== 0 || even(n-1) }")
				.put("/root/is_even.js", "import {even} from './lib1/even.js'; export var result = even(10);")

				.put("/root/lib1/js13.js", "import {b} from '../lib2/js14.js'; import {c} from '../lib2/js15.js'; " +
				"export var result = b + c")
				.put("/root/lib2/js14.js", "export var b = 42")
				.put("/root/lib2/js15.js", "export var c = 100")

				.put("/root/importExport.js", "import {v} from '/root/export.js'; export var result = v + 42")
				.put("/root/importExportAllFrom.js", "import {v} from '/root/exportAllFrom.js'; export var result = v + 42")
				.put("/root/importExportFrom.js", "import {v} from '/root/exportFrom.js'; export var result = v + 42")
				.put("/root/importExportVar.js", "import {v} from '/root/exportVar.js'; export var result = v + 42")
				.put("/root/importExportFunction.js", "import {fn} from '/root/exportFunction.js'; export var result = fn(42.1)")
				.put("/root/importExportAnon.js", "import fn from '/root/exportAnon.js'; export var result = fn(42.1)")
				.put("/root/importExportDefaultFunction.js", "import fn from '/root/exportDefaultFunction.js'; export var result = fn(42.1)")
				.put("/root/importExportDefault.js", "import v from '/root/exportDefault.js'; export var result = v + 42")

				.put("/root/export.js", "var v = 100; export {v}")
				.put("/root/exportAllFrom.js", "export * from '/root/export.js'")
				.put("/root/exportFrom.js", "export {v} from '/root/export.js'")
				.put("/root/exportVar.js", "export var v = 100")
				.put("/root/exportFunction.js", "export function fn(x){return 99.9 + x}")
				.put("/root/exportAnon.js", "export default function(x){return 99.9 + x}")
				.put("/root/exportDefaultFunction.js", "export default function fn(x){ return 99.9 + x }")
		// 		.put("/root/exportDefaultClass.js", "export default class C{}"); Not dealing with ES6 features for now...
				.put("/root/exportDefault.js", "export default 100")
				.put("/root/exportDefaultAndName.js", "export default 100; var v = 42; export { v };")

				.put("/root/importAll.js", "import * as mod from '/root/export.js'; export var result = mod.v + 42;")
				.put("/root/importAll.js", "import * as mod from '/root/export.js'; export var result = mod.v + 42;")
				.put("/root/importDefaultAndName.js", "import d, { v } from '/root/exportDefaultAndName.js'; export var result = d + v;")

				.put("/root/loadJson.js", "import json from './json.json'; export var result = json.value;")
				.put("/root/loadJson.esm", "export { result } from './loadJson.js';")
				.put("/root/json.json", "{ \"value\": 1, \"otherValue\": 2 }")

				.put("/root/thisIsUndefined.js", "export var result = this;");
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

	private static String listToString(ImmutableList<String> list) {
		return list.foldLeft((acc, str) -> {
			if (acc.length() == 0) {
				return str;
			} else {
				return acc + ", " + str;
			}
		}, "");
	}

	private static void testDependencyCollector(String code, String... dependencies) throws JsError {
		Module module = Parser.parseModule(code);
		ImmutableList<String> deps = new BandolierModule("main.js", module).getDependencies();
		ImmutableList<String> expected = ImmutableList.from(dependencies);

		if (!deps.equals(expected)) {
			System.out.println(listToString(deps));
			System.out.println(listToString(expected));
		}
		assertEquals(deps, expected);
	}

	public void testDeterminism() throws Exception {
		HashTable<String, String> modules = HashTable.emptyUsingEquality();
		modules = modules.put("/js1.js", "import {b} from './js2.js'; import {c} from './js3.js'; export var result = 'a' + b + c")
			.put("/js2.js", "import {d} from './js4.js'; export var b = 'b' + d")
			.put("/js3.js", "import {d} from './js4.js'; export var c = 'c' + d")
			.put("/js4.js", "export var d = 'd'");
		TestLoader localLoader = new TestLoader(modules);

		String actual = TestUtils.toString(TestUtils.bundleStandard("/js1.js", resolver, localLoader));

		String expected = "(function(global){\n" +
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
			"}.call(this,this).result);\n";

		assertEquals(expected, actual);
	}

	@Test
	public void testBundle() throws Exception {
		testResult("/root/lib1/js0.js", null, resolver, loader); // the bundler is innocent!
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
	}



}