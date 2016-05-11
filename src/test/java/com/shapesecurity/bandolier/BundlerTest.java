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
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.StaticMemberExpression;
import com.shapesecurity.shift.codegen.CodeGen;
import com.shapesecurity.bandolier.loader.IResolver;

import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import junit.framework.TestCase;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class BundlerTest extends TestCase {
	private TestLoader loader = new TestLoader();
	private IResolver resolver = new FileSystemResolver();

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

	private static void testDependencyCollector(String code, String... dependencies) throws JsError {
		Module module = Parser.parseModule(code);
		ImmutableList<String> deps = Bundler.collectDirectDependencies(module);
		ImmutableList<String> expected = ImmutableList.from(dependencies);

		if (!deps.equals(expected)) {
			System.out.println(Arrays.toString(deps.toArray(new String[deps.length])));
			System.out.println(Arrays.toString(expected.toArray(new String[expected.length])));
		}
		assertEquals(deps, expected);
	}

	@Test
	public void testBundle() throws Exception {
		testResult("/root/lib1/js0.js", null); // the bundler is innocent!
		testResult("/root/lib1/js1.js", 142.0); // simple import
		testResult("/root/lib1/js3.js", 142.0); // simple import / current dir
		testResult("/root/lib1/js4.js", 142.0); // simple import / parent dir
		testResult("/root/lib1/js6.js", 142.0); // import chaining
		testResult("/root/lib1/js9.js", 142.0); // import chaining
		testResult("/root/lib1/js13.js", 142.0); // import function
		testResult("/root/is_even.js", true); // cyclic import

		// All the possible ways of importing and exporting:
		testResult("/root/importExport.js", 142.0); // multiple imports
		testResult("/root/importExportAllFrom.js", 142.0);
		testResult("/root/importExportFrom.js", 142.0);
		testResult("/root/importExportVar.js", 142.0);
		testResult("/root/importExportFunction.js", 142.0);
		testResult("/root/importExportAnon.js", 142.0);
		testResult("/root/importExportDefaultFunction.js", 142.0);
		testResult("/root/importExportDefault.js", 142.0);
		testResult("/root/importAll.js", 142.0);
		testResult("/root/importDefaultAndName.js", 142.0);

		testResult("/root/thisIsUndefined.js", null);
	}

	private Object runInNashorn(String filePath) throws Exception {
		Script script = Bundler.bundle(Paths.get(filePath), resolver, loader);

		ExpressionStatement statement = (ExpressionStatement) script.getStatements().maybeHead().just();
		CallExpression callExpression = (CallExpression) statement.getExpression();
		StaticMemberExpression memberExpression = new StaticMemberExpression("result", callExpression);

		script = new Script(script.getDirectives(), ImmutableList.from(new ExpressionStatement(memberExpression)));
		String newProgramText = CodeGen.codeGen(script, true);

		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

		try {
			return engine.eval(newProgramText);
		} catch (ScriptException e) {
			System.out.println(newProgramText);
			throw e;
		}
	}

	private void testResult(String filePath, Object expected) throws Exception {
		Object result = runInNashorn(filePath);
		if (result instanceof Double) {
			assertEquals((Double) expected, (Double) result, 0.0);
		} else if (result instanceof Integer) {
			assertEquals(((Double) expected).intValue(), (int) result);
		} else {
			assertEquals(expected, result);
		}
	}

	private class TestLoader implements IResourceLoader {
		private Map<String, String> modules = new HashMap<>();

		public TestLoader() {
			modules.put("/root/lib1/js0.js", "var test = 0");

			modules.put("/root/lib1/js1.js", "import {b} from '/root/lib1/js2.js'; export var result = 42 + b");
			modules.put("/root/lib1/js2.js", "export var b = 100");
			modules.put("/root/lib1/js3.js", "import {b} from './js2.js'; export var result = 42 + b");

			modules.put("/root/lib1/js4.js", "import {b} from '../js5.js'; export var result = 42 + b");
			modules.put("/root/js5.js", "export var b = 100");

			modules.put("/root/lib1/js6.js", "import {b} from './js7.js'; export var result = 32 + b");
			modules.put("/root/lib1/js7.js", "import {c} from './js8.js'; export var b = 10 + c");
			modules.put("/root/lib1/js8.js", "export var c = 100");

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
			modules.put("/root/importAll.js", "import * as mod from '/root/export.js'; export var result = mod.v + 42;");
			modules.put("/root/importDefaultAndName.js", "import d, { v } from '/root/exportDefaultAndName.js'; export var result = d + v;");

			modules.put("/root/thisIsUndefined.js", "export var result = this;");
		}

		@NotNull
		@Override
		public Boolean exists(@NotNull Path path) {
			return modules.containsKey(path.toString());
		}

		@NotNull
		@Override
		public String loadResource(@NotNull Path path) throws IOException {
			String code = modules.get(path.toString());
			if (code == null) throw new AssertionError(path);
			return code;
		}
	}
}