package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.loader.FileSystemResolver;
import com.shapesecurity.bandolier.es2017.loader.IResolver;
import com.shapesecurity.bandolier.es2017.loader.ModuleLoaderException;
import com.shapesecurity.bandolier.es2017.transformations.DeadCodeElimination;
import com.shapesecurity.shift.es2017.codegen.CodeGen;
import com.shapesecurity.shift.es2017.parser.Parser;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.shapesecurity.bandolier.es2017.TestUtils.bundlePierced;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DeadCodeEliminationTest {
	private static TestLoader loader;
	private static IResolver resolver = new FileSystemResolver();

	static {
		Map<String, String> modules = new HashMap<>();

		modules.put("/js0.js", "export var test = 0");
		modules.put("/js1.js", "import { test } from './js0.js';");

		loader = new TestLoader(modules);
	}

	static String bundle(@Nonnull BundlerOptions options, @Nonnull String filePath) throws ModuleLoaderException {
		return CodeGen.codeGen(bundlePierced(options, filePath, resolver, loader), true);
	}

	static void testBundled(@Nonnull BundlerOptions options, @Nonnull String filePath, @Nonnull String expected) {
		try {
			assertEquals(expected, bundle(options, filePath));
		} catch (ModuleLoaderException e) {
			e.printStackTrace();
			fail();
		}
	}

	static void testDCE(@Nonnull String source, @Nonnull String expected) {
		try {
			assertEquals(expected, CodeGen.codeGen(DeadCodeElimination.removeAllUnusedDeclarations(Parser.parseScript(source)), true));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	@Test
	public void testImports() {
		testBundled(BundlerOptions.DEFAULT_OPTIONS, "/js1.js", "(function(e){\n" +
				"\"use strict\";\n" +
				"var t={\n" +
				"__proto__:null};\n" +
				"if(e.Symbol)e.Object.defineProperty(t,e.Symbol.toStringTag,{\n" +
				"value:\"Module\"});\n" +
				"t=e.Object.freeze(t);\n" +
				"return t;\n" +
				"}(this));\n");
	}

	@Test
	public void testBasicDeclarations() {
		testDCE("var test", "");
		testDCE("var test = 0", "");
		testDCE("var test = 'test'", "");
		testDCE("var test = 5.2332", "");
		testDCE("var test = /test/", "");
		testDCE("var test;test;", "var test;\ntest;\n");
		testDCE("var test = 0;test;", "var test=0;\ntest;\n");
		testDCE("var test = 'test';test;", "var test=\"test\";\ntest;\n");
		testDCE("var test = 5.2332;test;", "var test=5.2332;\ntest;\n");
		testDCE("var test = /test/;test;", "var test=/test/;\ntest;\n");

		testDCE("let test", "");
		testDCE("let test = 0", "");
		testDCE("let test = 'test'", "");
		testDCE("let test = 5.2332", "");
		testDCE("let test = /test/", "");
		testDCE("let test;test;", "let test;\ntest;\n");
		testDCE("let test = 0;test;", "let test=0;\ntest;\n");
		testDCE("let test = 'test';test;", "let test=\"test\";\ntest;\n");
		testDCE("let test = 5.2332;test;", "let test=5.2332;\ntest;\n");
		testDCE("let test = /test/;test;", "let test=/test/;\ntest;\n");

		testDCE("const test", "");
		testDCE("const test = 0", "");
		testDCE("const test = 'test'", "");
		testDCE("const test = 5.2332", "");
		testDCE("const test = /test/", "");
		testDCE("const test;test;", "const test;\ntest;\n");
		testDCE("const test = 0;test;", "const test=0;\ntest;\n");
		testDCE("const test = 'test';test;", "const test=\"test\";\ntest;\n");
		testDCE("const test = 5.2332;test;", "const test=5.2332;\ntest;\n");
		testDCE("const test = /test/;test;", "const test=/test/;\ntest;\n");
	}

	@Test
	public void testDeclarations() {
		testDCE("var test = function() {console.log('test');}", "");
		testDCE("var test = function() {console.log('test');};test()", "var test=function(){\n" +
				"console.log(\"test\");\n" +
				"};\n" +
				"test();\n");
		testDCE("var test = () => console.log('test');", "");
		testDCE("var test = () => console.log('test');test()", "var test=()=>console.log(\"test\");\n" +
				"test();\n");
		testDCE("function test() {console.log('test');}", "");
		testDCE("function test() {console.log('test');};test()", "function test(){\n" +
				"console.log(\"test\");\n" +
				"};\n" +
				"test();\n");
		testDCE("class test {test(){console.log('test');} test2(){this.test()}}", "");
		testDCE("class test {constructor(){console.log('test');} test2(){this.test2()}}; new test();", "class test{constructor(){\n" +
				"console.log(\"test\");\n" +
				"}test2(){\n" +
				"this.test2();\n" +
				"}};\n" +
				"new test;\n");
	}

	@Test
	public void testSideEffects() {
		testDCE("var test = console.log('test');", "var test=console.log(\"test\");\n");
		testDCE("var test = 3 + 3;", "var test=3+3;\n");
	}

	@Test
	public void testIIFE() {
		testDCE("(function(){var test = 0;})();", "(function(){\n" +
				"}());\n");
		testDCE("(function(x){var test = 0;})();", "(function(x){\n" +
				"var test=0;\n" +
				"}());\n");
		testDCE("(function(){var test = 0;})(x);", "(function(){\n" +
				"var test=0;\n" +
				"}(x));\n");
		testDCE("(function(...x){var test = 0;})();", "(function(...x){\n" +
				"var test=0;\n" +
				"}());\n");
	}


}
