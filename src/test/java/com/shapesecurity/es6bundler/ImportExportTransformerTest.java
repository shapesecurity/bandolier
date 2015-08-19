package com.shapesecurity.es6bundler;

import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.codegen.CodeGen;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

import junit.framework.TestCase;

public class ImportExportTransformerTest extends TestCase {

	public void testTransformModule() throws Exception {
		testTransformer("", "");
		testTransformer("require('m', module)", "import 'm'");
		testTransformer("var __resolver = require('m', module); var d = __resolver['default'];", "import d from 'm'");
		testTransformer("var __resolver = require('m', module); var m = __resolver;", "import * as m from 'm'");
		testTransformer("var __resolver = require('m', module); var x = __resolver['x'];", "import {x} from 'm'");
		testTransformer("var __resolver = require('m', module); var d = __resolver['default']; var m = __resolver;",
			"import d, * as m from 'm'");
		testTransformer("var __resolver = require('m', module); var d = __resolver['default']; var x = __resolver['x'];",
			"import d, {x} from 'm'");
		testTransformer("var __resolver = require('m', module); for (var i in __resolver) exports[i] = __resolver[i]",
			"export * from 'm'");
		testTransformer("var __resolver = require('m', module); exports['x'] = __resolver['x']",
			"export {x} from 'm'");
		testTransformer("exports['x'] = x", "export {x}");
		testTransformer("var x = 0; exports['x'] = x", "export var x = 0");
		testTransformer("function f(){} exports['f'] = f", "export function f(){}");
		testTransformer("class C{} exports['C'] = C", "export class C{}");
		testTransformer("exports['default'] = function(){}", "export default function(){}");
		testTransformer("function f(){} exports['default'] = f", "export default function f(){}");
		testTransformer("class C{} exports['default'] = C", "export default class C{}");
		testTransformer("exports['default'] = 0", "export default 0");
		testTransformer("exports['default'] = 41 + 1", "export default 41 + 1");
		testTransformer("exports['default'] = {}", "export default {}");
	}

	private void testTransformer(String expected, String code) throws JsError {
		Module module = Parser.parseModule(code);
		Module transformed = ImportExportTransformer.transformModule(module);
		Module expectedModule = Parser.parseModule(expected);

		if (!transformed.equals(expectedModule)) {
			System.out.println(CodeGen.codeGen(transformed));
			System.out.println(expected);
		}

		assertEquals(expectedModule, transformed);
	}
}