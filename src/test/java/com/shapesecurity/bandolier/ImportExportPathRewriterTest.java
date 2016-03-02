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
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.codegen.CodeGen;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

import junit.framework.TestCase;

import java.nio.file.Paths;

public class ImportExportPathRewriterTest extends TestCase {
	public void testRewriteModule() throws Exception {
		testRewrite("", "", "");
		testRewrite("import 'dir/lib1.js'", "import 'dir/lib1.js'", "root");
		testRewrite("import '/dir/lib1.js'", "import '/dir/lib1.js'", "root");
		testRewrite("import 'root/lib1.js'", "import './lib1.js'", "root");
		testRewrite("import 'root/lib1.js'", "import '../lib1.js'", "root/dir");
		testRewrite("import 'lib1.js'", "import '../../lib1.js'", "root/dir");
		testRewrite("import 'root/dir/lib1.js'", "import './lib1.js'", "root/dir");
		testRewrite("import 'root/lib1.js'; import 'root/sub/lib2.js'",
			"import '../lib1.js'; import '../sub/lib2.js'", "root/dir");
		testRewrite("import 'root/dir/lib1.js'; a = 2;", "import './lib1.js'; a = 2;", "root/dir");

		testRewrite("import d from 'root/lib1.js'", "import d from '../lib1.js'", "root/dir");
		testRewrite("import * as l from 'root/lib1.js'", "import * as l from '../lib1.js'", "root/dir");
		testRewrite("import * as l from 'root/lib1.js'", "import * as l from '../lib1.js'", "root/dir");
		testRewrite("import {x} from 'root/lib1.js'", "import {x} from '../lib1.js'", "root/dir");
		testRewrite("import d, * as l from 'root/lib1.js'", "import d, * as l from '../lib1.js'", "root/dir");
		testRewrite("import d, {x} from 'root/lib1.js'", "import d, {x} from '../lib1.js'", "root/dir");

		testRewrite("export * from 'root/lib1.js'", "export * from '../lib1.js'", "root/dir");
		testRewrite("export {x} from 'root/lib1.js'", "export {x} from '../lib1.js'", "root/dir");
	}

	private void testRewrite(String rewrittenSource, String originalSource, String path) throws JsError {
		ImportResolvingRewriter rewriter = new ImportResolvingRewriter(new FileSystemResolver());
		Module original = Parser.parseModule(originalSource);
		Module rewritten = rewriter.rewrite(original, Paths.get(path));

		Module parsed = Parser.parseModule(rewrittenSource);
		if (!rewritten.equals(parsed)) {
			System.out.println(CodeGen.codeGen(rewritten));
			System.out.println(CodeGen.codeGen(parsed));
		}
		assertEquals(rewritten, parsed);
	}
}