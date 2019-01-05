package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.bundlers.IModuleBundler;
import com.shapesecurity.bandolier.es2017.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2017.loader.FileLoader;
import com.shapesecurity.bandolier.es2017.loader.IResourceLoader;
import com.shapesecurity.bandolier.es2017.loader.ModuleLoaderException;
import com.shapesecurity.bandolier.es2017.loader.NodeResolver;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.ImmutableSet;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.codegen.CodeGen;
import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.EarlyErrorChecker;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Test262 {

	private static final Yaml yamlParser = new Yaml();

	private static final ImmutableSet<String> xfailParse = ImmutableList.of(

			// ignored proposal
			"privatename-valid-no-earlyerr.js"
	).uniqByEquality();

	private static final ImmutableSet<String> xfailParseThrowingDangerous = ImmutableList.of(

			// we fail here instead of during execution.
			"instn-iee-bndng-var.js",
			"instn-named-bndng-fun.js",
			"instn-named-bndng-trlng-comma.js",
			"instn-star-binding.js",
			"instn-named-bndng-var.js",
			"instn-iee-bndng-fun.js",
			"instn-named-bndng-gen.js",
			"instn-iee-bndng-gen.js"
			).uniqByEquality().union(xfailParse);

	private static final HashSet<String> xfailParseFeatures = new HashSet<>(Arrays.asList(
			// ignored proposal
			"export-star-as-namespace-from-module"
	));

	private static final HashSet<String> xpassParseDespiteFeatures = new HashSet<>(Arrays.asList(
			// supposed to fail for a different reason
			"early-dup-export-as-star-as.js",
			"parse-err-semi-name-space-export.js",
			"early-dup-export-star-as-dflt.js",
			"parse-err-semi-export-star.js"
	));

	private static final HashSet<String> xfailEarlyErrors = new HashSet<>(Arrays.asList(

			// unimplemented module early errors
			"early-lex-and-var.js"
	));

	private static final ImmutableSet<String> xfailExecute = ImmutableList.of(

			// no generators in Nashorn
			"instn-local-bndng-gen.js",
			"eval-export-dflt-gen-named-semi.js",
			"instn-named-bndng-gen.js",
			"instn-named-bndng-dflt-gen-anon.js",
			"instn-iee-bndng-gen.js",
			"instn-named-bndng-dflt-gen-named.js",
			"eval-export-dflt-expr-gen-named.js",
			"eval-export-dflt-gen-anon-semi.js",
			"instn-local-bndng-export-gen.js",
			"eval-export-gen-semi.js",
			"eval-export-dflt-expr-gen-anon.js",

			// no classes in Nashorn
			"eval-export-dflt-cls-named.js",
			"eval-export-dflt-cls-anon-semi.js",
			"eval-gtbndng-local-bndng-cls.js",
			"eval-export-dflt-expr-cls-named.js",
			"eval-export-dflt-cls-named-semi.js",
			"instn-named-bndng-dflt-cls.js",
			"instn-named-bndng-cls.js",
			"instn-local-bndng-cls.js",
			"eval-export-dflt-expr-cls-name-meth.js",
			"instn-local-bndng-export-cls.js",
			"eval-export-dflt-cls-name-meth.js",
			"instn-iee-bndng-cls.js",
			"eval-export-dflt-expr-cls-anon.js",
			"eval-export-cls-semi.js",
			"eval-export-dflt-cls-anon.js",

			// no let in Nashorn
			"instn-iee-bndng-let.js",
			"instn-local-bndng-export-let.js",
			"own-property-keys-binding-types.js",
			"namespace/internals/get-str-found-uninit.js",
			"namespace/internals/delete-exported-uninit.js",
			"namespace/internals/has-property-str-found-uninit.js",
			"namespace/internals/get-str-initialize.js",
			"namespace/internals/get-own-property-str-found-uninit.js",
			"instn-uniq-env-rec.js",
			"instn-local-bndng-let.js",
			"eval-gtbndng-local-bndng-let.js",
			"instn-named-bndng-let.js",

			// no const in Nashorn
			"instn-local-bndng-const.js",
			"eval-gtbndng-local-bndng-const.js",
			"instn-local-bndng-export-const.js",
			"namespace/internals/set.js",
			"namespace/internals/define-own-property.js",
			"instn-iee-bndng-const.js",
			"instn-named-bndng-const.js",

			// lack of Symbol, Reflect API (they work when implemented, aka not in nashorn)
			"namespace/internals/has-property-str-found-init.js",
			"namespace/internals/get-own-property-sym.js",
			"namespace/internals/delete-non-exported.js",
			"namespace/internals/prevent-extensions.js",
			"namespace/internals/get-sym-not-found.js",
			"namespace/internals/has-property-str-not-found.js",
			"namespace/internals/get-sym-found.js",
			"namespace/internals/has-property-sym-not-found.js",
			"namespace/internals/has-property-sym-found.js",
			"namespace/Symbol.toStringTag.js",
			"namespace/Symbol.iterator.js",
			"namespace/internals/own-property-keys-binding-types.js",
			"namespace/internals/own-property-keys-sort.js",
			"namespace/internals/delete-exported-init.js",

			// Nashorn bug in object expressions not setting function expression names.
			"eval-export-dflt-expr-fn-anon.js",
			"instn-named-bndng-dflt-fun-named.js",
			"instn-named-bndng-dflt-fun-anon.js",

			// nothing we can do object.hasownproperty/etc
			"namespace/internals/object-hasOwnProperty-binding-uninit.js",
			"namespace/internals/object-propertyIsEnumerable-binding-uninit.js",

			// nothing we can do: "in"/enumerable properties
			"namespace/internals/enumerate-binding-uninit.js",

			// nothing we can do: getOwnPropertyDescriptor
			"namespace/internals/get-own-property-str-found-init.js",

			// nothing we can do: Object.keys
			"namespace/internals/object-keys-binding-uninit.js",

			// TDZ reference before export case
			"instn-named-bndng-dflt-star.js",
			"instn-named-bndng-dflt-named.js",
			"instn-named-bndng-dflt-expr.js"

	).uniqByEquality();


	// in union with xFailExecute
	private static final ImmutableSet<String> xfailExecuteBalanced = ImmutableList.of(

			// this is what we give up for DangerLevel.BALANCED
			"instn-star-id-name.js",
			"instn-star-iee-cycle.js",
			"instn-star-equality.js",
			"namespace/internals/get-str-found-init.js",
			"namespace/internals/get-prototype-of.js",
			"namespace/internals/set-prototype-of-null.js",
			"namespace/internals/get-str-update.js",
			"namespace/internals/get-own-property-str-not-found.js",
			"namespace/internals/get-str-not-found.js",
			"namespace/internals/is-extensible.js",
			"instn-star-binding.js",
			"instn-iee-star-cycle.js"
	).uniqByEquality().union(xfailExecute);

	// in union with xFailExecute and xfailExecuteBalanced
	private static final ImmutableSet<String> xfailExecuteDangerous = ImmutableList.of(

			// this is what we give up for DangerLevel.DANGEROUS in addition to xfailExecuteBalanced
			"instn-iee-bndng-var.js",
			"instn-named-bndng-fun.js",
			"instn-named-bndng-trlng-comma.js",
			"instn-star-binding.js",
			"instn-named-bndng-var.js",
			"instn-iee-bndng-fun.js"
	).uniqByEquality().union(xfailExecuteBalanced);

	private static final String testsDir = "src/test/resources/test262/test/language/module-code/";

	private static final String harnessDir = "src/test/resources/test262/harness/";

	private enum Test262Negative {
		PARSERESOLUTION, EARLY, RUNTIME, NONE
	}

	private static final class Test262Info {
		@Nonnull
		public final String name;
		public final Test262Negative negative;
		public final boolean noStrict;
		public final boolean onlyStrict;
		public final boolean async;
		public final boolean module;
		@Nonnull
		public final ImmutableList<String> includes;
		@Nonnull
		public final ImmutableList<String> features;

		public Test262Info(@Nonnull String name, @Nonnull Test262Negative negative, boolean noStrict, boolean onlyStrict, boolean async, boolean module, @Nonnull ImmutableList<String> includes, @Nonnull ImmutableList<String> features) {
			this.name = name;
			this.negative = negative;
			this.noStrict = noStrict;
			this.onlyStrict = onlyStrict;
			this.async = async;
			this.module = module;
			this.includes = includes;
			this.features = features;
		}
	}

	@Nullable
	private static Test262Info extractTest262Info(@Nonnull String path, @Nonnull String source) {
		// extract comment block
		int test262CommentBegin = source.indexOf("/*---");
		if (test262CommentBegin < 0) {
			return null;
		}
		test262CommentBegin += 5;
		int test262CommentEnd = source.indexOf("---*/", test262CommentBegin);
		if (test262CommentEnd < 0) {
			return null;
		}
		String yaml = source.substring(test262CommentBegin, test262CommentEnd);
		Object rawParsedYaml = yamlParser.load(yaml);
		if (!(rawParsedYaml instanceof Map)) {
			return null;
		}
		Map<String, Object> parsedYaml = (Map<String, Object>) rawParsedYaml;
		// extract flags and negative
		Object rawNegative = parsedYaml.get("negative");
		Test262Negative negativeEnum = Test262Negative.NONE;
		if (rawNegative != null) {
			if (!(rawNegative instanceof Map)) {
				return null;
			}
			Map<String, Object> negative = (Map<String, Object>) rawNegative;
			String phase = (String) negative.get("phase");
			if (phase == null) {
				return null;
			}
			switch (phase) {
				case "resolution":
				case "parse":
					negativeEnum = Test262Negative.PARSERESOLUTION;
					break;
				case "early":
					negativeEnum = Test262Negative.EARLY;
					break;
				case "runtime":
					negativeEnum = Test262Negative.RUNTIME;
					break;
				default:
					throw new RuntimeException("Invalid negative phase: " + phase);
			}
		}
		Object rawFlags = parsedYaml.get("flags");
		boolean noStrict = false;
		boolean onlyStrict = false;
		boolean async = false;
		boolean module = false;
		if (rawFlags != null) {
			ArrayList<String> flags = (ArrayList<String>) rawFlags;
			for (String flag : flags) {
				switch (flag) {
					case "noStrict":
						noStrict = true;
						break;
					case "onlyStrict":
						onlyStrict = true;
						break;
					case "async":
						async = true;
						break;
					case "module":
						module = true;
						break;
				}
			}
		}
		Object rawIncludes = parsedYaml.get("includes");
		ImmutableList<String> includes = ImmutableList.empty();
		if (rawIncludes != null) {
			includes = ImmutableList.from((ArrayList<String>) rawIncludes);
		}
		Object rawFeatures = parsedYaml.get("features");
		ImmutableList<String> features = ImmutableList.empty();
		if (rawFeatures != null) {
			features = ImmutableList.from((ArrayList<String>) rawFeatures);
		}
		return new Test262Info(path, negativeEnum, noStrict, onlyStrict, async, module, includes, features);
	}

	private static final class Test262Exception extends RuntimeException {

		@Nonnull
		public final String name;

		public Test262Exception(@Nonnull String name, @Nonnull String message) {
			super(message);
			this.name = name;
		}

		public Test262Exception(@Nonnull String name, @Nonnull String message, @Nonnull Throwable caused) {
			super(message, caused);
			this.name = name;
		}
	}

	@Nonnull
	private final IResourceLoader loader = new FileLoader();

	private void runTest262Test(@Nonnull String harness, @Nonnull String source, @Nonnull Path path, @Nonnull Test262Info info, @Nonnull IModuleBundler bundler, @Nonnull String category, @Nonnull BundlerOptions options, @Nonnull ImmutableSet<String> xfailParse, @Nonnull ImmutableSet<String> xfailExecute) {
		boolean xfailedParse = xfailParse.contains(info.name) || (info.features.exists(xfailParseFeatures::contains) && !xpassParseDespiteFeatures.contains(info.name));
		try {
			Pair<Script, ImmutableList<EarlyError>> pair = Bundler.bundleStringWithEarlyErrors(options, source, path.toAbsolutePath(), new NodeResolver(loader), loader, bundler);
			boolean invalidParse = false;
			if ((info.negative == Test262Negative.PARSERESOLUTION) != xfailedParse) {
				invalidParse = true;
			}
			boolean xfailedEarly = xfailEarlyErrors.contains(info.name);
			ImmutableList<EarlyError> earlyErrors = EarlyErrorChecker.validate(pair.left).append(pair.right);
			boolean passEarlyError = earlyErrors.length == 0;
			if (invalidParse) {
				if (passEarlyError) {
					throw new Test262Exception(info.name, "Parsed and should not have: " + path.toString());
				}
				return;
			}
			if (passEarlyError && (info.negative == Test262Negative.EARLY) != xfailedEarly) {
				throw new Test262Exception(info.name, "Passed early errors and should not have: " + path.toString());
			} else if (!passEarlyError && ((info.negative == Test262Negative.EARLY) == xfailedEarly)) {
				throw new Test262Exception(info.name, "Failed early errors and should not have: " + path.toString(),
						new RuntimeException(earlyErrors.foldLeft((acc, error) -> error.message + "\n" + acc, "")));
			}
			boolean xfailedExecute = xfailExecute.contains(info.name);
			ScriptEngine nashorn = new ScriptEngineManager().getEngineByName("nashorn");
			try {
				nashorn.eval(harness);
				nashorn.eval(CodeGen.codeGen(pair.left));
				if ((info.negative == Test262Negative.RUNTIME) != xfailedExecute) {
					throw new Test262Exception(info.name, "Executed and should not have<" + category + ">: " + path.toString());
				}
			} catch (ScriptException e) {
				if (info.negative == Test262Negative.RUNTIME == xfailedExecute && info.negative != Test262Negative.RUNTIME) {
					throw new Test262Exception(info.name, "Did not execute and should have<" + category + ">: " + path.toString() + "\n" + CodeGen.codeGen(pair.left), e);
				}
			}
		} catch (ModuleLoaderException e) {
			if ((info.negative == Test262Negative.PARSERESOLUTION) == xfailedParse && info.negative != Test262Negative.EARLY && info.negative != Test262Negative.RUNTIME) { // we classify some early and runtime errors as parse errors
				throw new Test262Exception(info.name, "Did not parse and should have: " + path.toString(), e);
			}
		}
	}

	private void runTest(@Nonnull Path root, @Nonnull Path path) throws IOException {
		if (Files.isDirectory(path) || !path.toString().endsWith(".js") || path.toString().endsWith("_FIXTURE.js")) {
			return;
		}
		String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		Test262Info info = extractTest262Info(root.relativize(path).toString(), source);
		if (info == null) { // parse failure or not module
			throw new Test262Exception(path.toString(), "Failed to parse frontmatter");
		} else if (!info.module) {
			return; // we are only interested in modules
		}
		StringBuilder includes = new StringBuilder();
		for (String include : info.includes.cons("assert.js").cons("sta.js")) {
			includes.append(new String(Files.readAllBytes(Paths.get(harnessDir, include)), StandardCharsets.UTF_8)).append("\n");
		}
		runTest262Test(includes.toString(), source, path, info, new PiercedModuleBundler(), "SAFE", BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.SAFE), xfailParse, xfailExecute);
		runTest262Test(includes.toString(), source, path, info, new PiercedModuleBundler(), "BALANCED", BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.BALANCED), xfailParse, xfailExecuteBalanced);
		runTest262Test(includes.toString(), source, path, info, new PiercedModuleBundler(), "DANGEROUS", BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS), xfailParse, xfailExecuteDangerous);
		runTest262Test(includes.toString(), source, path, info, new PiercedModuleBundler(), "THROWING_DANGEROUS", BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS).withThrowOnImportAssignment(true), xfailParseThrowingDangerous, xfailExecuteDangerous);
		// runTest262Test(includes.toString() + source, path, info, new StandardModuleBundler());
	}

	@Test
	public void testTest262() throws Exception {
		LinkedList<Test262Exception> exceptions = new LinkedList<>();
		Path root = Paths.get(testsDir);
		Files.walk(root).forEach(path -> {
			try {
				runTest(root, path);
			} catch (IOException e) {
				Assert.fail(e.toString());
			} catch (Test262Exception e) {
				exceptions.add(e);
			}
		});
		if (exceptions.size() > 0) {
			for (Test262Exception exception : exceptions) {
				exception.printStackTrace();
			}
			System.out.println(exceptions.size() + " test262 tests failed:");
			for (Test262Exception exception : exceptions) {
				System.out.println("	" + exception.name + ": " + exception.getMessage());
			}
			Assert.fail();
		}
	}
}
