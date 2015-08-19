package com.shapesecurity.es6bundler;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.visitor.Director;

import junit.framework.TestCase;

import java.util.Arrays;

public class ModuleDependencyCollectorTest extends TestCase {

	public void testReduceImport() throws Exception {
		testDependencyCollector("", new String[]{});
		testDependencyCollector("import '../dep1.js'", new String[]{"../dep1.js"});
		testDependencyCollector("import '../dep1.js'; import 'dep2.js'", new String[]{"../dep1.js", "dep2.js"});
	}

	public void testReduceImportNamespace() throws Exception {
		testDependencyCollector("import * as d1 from '../dep1.js'", new String[]{"../dep1.js"});
		testDependencyCollector("import * as d1 from '../dep1.js'; import * as d2 from 'dep2.js'", new String[]{"../dep1.js", "dep2.js"});
	}

	public void testReduceExportFrom() throws Exception {
		testDependencyCollector("export {v} from '../dep1.js'", new String[]{"../dep1.js"});
		testDependencyCollector("export {v1} from '../dep1.js'; export {v2} from 'dep2.js'", new String[]{"../dep1.js", "dep2.js"});
	}

	public void testReduceExportAllFrom() throws Exception {
		testDependencyCollector("export * from '../dep1.js'", new String[]{"../dep1.js"});
		testDependencyCollector("export * from '../dep1.js'; export * from 'dep2.js'", new String[]{"../dep1.js", "dep2.js"});
	}

	private void testDependencyCollector(String code, String[] dependencies) throws JsError {
		Module module = Parser.parseModule(code);
		ImmutableList<String> reduced = Director.reduceModule(new ModuleDependencyCollector(), module);
		ImmutableList<String> deps = ImmutableList.from(dependencies);

		if (!reduced.equals(deps)) {
			System.out.println(Arrays.toString(reduced.toArray(new String[reduced.length])));
			System.out.println(Arrays.toString(deps.toArray(new String[deps.length])));
		}
		assertEquals(reduced, deps);
	}
}