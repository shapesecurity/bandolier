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

import com.shapesecurity.shift.es2017.ast.Module;
import com.shapesecurity.shift.es2017.codegen.CodeGen;
import com.shapesecurity.shift.es2017.parser.JsError;
import com.shapesecurity.shift.es2017.parser.Parser;
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
		testTransformer("var __resolver = require('m', module); exports['y'] = __resolver['x']",
			"export {x as y} from 'm'");
		testTransformer("exports['x'] = x", "export {x}");
		testTransformer("exports['y'] = x", "export {x as y}");
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