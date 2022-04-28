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
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.codegen.CodeGen;
import com.shapesecurity.shift.es2017.parser.EarlyError;
import com.shapesecurity.shift.es2017.parser.EarlyErrorChecker;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class Test262 {
	private static final Yaml YAML_PARSER = new Yaml();

	private static final IResourceLoader LOADER = new FileLoader();

	private static final Set<String> XFAIL_AT_PARSE = Set.of(
			// ignored proposal
			"privatename-valid-no-earlyerr.js"
	);

	private static final Set<String> XFAIL_AT_PARSE_WITH_THROWING_DANGEROUS = Stream.concat(Set.of(
			// we fail here instead of during execution.
			"instn-iee-bndng-var.js",
			"instn-named-bndng-fun.js",
			"instn-named-bndng-trlng-comma.js",
			"instn-star-binding.js",
			"instn-named-bndng-var.js",
			"instn-iee-bndng-fun.js",
			"instn-named-bndng-gen.js",
			"instn-iee-bndng-gen.js"
	).stream(), XFAIL_AT_PARSE.stream()).collect(Collectors.toSet());

	private static final Set<String> XFAIL_PARSE_FEATURES = Set.of(
			// ignored proposal
			"export-star-as-namespace-from-module"
	);

	private static final Set<String> XPASS_PARSE_DESPITE_FEATURES = Set.of(
			// supposed to fail for a different reason
			"early-dup-export-as-star-as.js",
			"parse-err-semi-name-space-export.js",
			"early-dup-export-star-as-dflt.js",
			"parse-err-semi-export-star.js"
	);

	private static final Set<String> XFAIL_EARLY_ERRORS = Set.of(
			// unimplemented module early errors
			"early-lex-and-var.js"
	);

	private static final Set<String> XFAIL_EXECUTE = Set.of(
			// function name inference breaks
			"eval-export-dflt-expr-fn-anon.js",
			"eval-export-dflt-cls-named.js",
			"eval-export-dflt-cls-anon.js",
			"eval-export-dflt-expr-cls-anon.js",
			"eval-export-dflt-expr-gen-anon.js",
			"instn-named-bndng-dflt-fun-named.js",
			"instn-named-bndng-dflt-fun-anon.js",
			"instn-named-bndng-dflt-gen-named.js",
			"instn-named-bndng-dflt-gen-anon.js",

			// nothing we can do about property descriptors on the namespace object
			"namespace/internals/define-own-property.js",
			"namespace/internals/get-own-property-str-found-uninit.js",
			"namespace/internals/object-hasOwnProperty-binding-uninit.js",
			"namespace/internals/object-propertyIsEnumerable-binding-uninit.js",
			"namespace/internals/set.js",

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
	);

	// in union with xFailExecute
	private static final Set<String> XFAIL_EXECUTE_BALANCED = Stream.concat(XFAIL_EXECUTE.stream(), Set.of(
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
			"namespace/internals/delete-exported-init.js",
			"namespace/internals/delete-exported-uninit.js",
			"namespace/internals/delete-non-exported.js",
			"namespace/internals/get-own-property-sym.js",
			"namespace/internals/get-str-found-uninit.js",
			"namespace/internals/get-str-initialize.js",
			"namespace/internals/get-sym-found.js",
			"namespace/internals/get-sym-not-found.js",
			"namespace/internals/has-property-str-found-uninit.js",
			"namespace/internals/has-property-str-not-found.js",
			"namespace/internals/has-property-str-found-init.js",
			"namespace/internals/has-property-sym-found.js",
			"namespace/internals/has-property-sym-not-found.js",
			"namespace/internals/own-property-keys-binding-types.js",
			"namespace/internals/own-property-keys-sort.js",
			"namespace/internals/prevent-extensions.js",
			"namespace/Symbol.iterator.js",
			"namespace/Symbol.toStringTag.js",
			"instn-star-binding.js",
			"instn-iee-star-cycle.js"
	).stream()).collect(Collectors.toSet());

	private static final Set<String> XFAIL_EXECUTE_DANGEROUS = Stream.concat(XFAIL_EXECUTE_BALANCED.stream(), Set.of(
			// this is what we give up for DangerLevel.DANGEROUS in addition to xfailExecuteBalanced
			"instn-iee-bndng-var.js",
			"instn-named-bndng-fun.js",
			"instn-named-bndng-trlng-comma.js",
			"instn-star-binding.js",
			"instn-named-bndng-var.js",
			"instn-iee-bndng-fun.js",
			"instn-iee-bndng-gen.js",
			"instn-named-bndng-gen.js"
	).stream()).collect(Collectors.toSet());

	private static final String TESTS_DIR = "src/test/resources/test262/test/language/module-code/";

	private static final String HARNESS_DIR = "src/test/resources/test262/harness/";

	final Path root;
	final Path path;
	final Test262Info info;
	final String includes;

	public Test262(Path root, Path path, Test262Info info, String name) {
		// name is ignored, we just need it so JUnit can give tests reasonable names
		this.root = root;
		this.path = path;
		this.info = info;
		StringBuilder includes = new StringBuilder();
		for (String include : info.includes.cons("assert.js").cons("sta.js")) {
			try {
				includes.append(Files.readString(Paths.get(HARNESS_DIR, include))).append("\n");
			} catch (IOException e) {
				throw new RuntimeException("failed to read include " + include, e);
			}
		}
		this.includes = includes.toString();
	}

	@Parameterized.Parameters(name = "{2}")
	public static Iterable<Object[]> params() {
		Path root = Paths.get(TESTS_DIR);
		List<Object[]> params = new ArrayList<>();
		try {
			Files.walk(root).forEach(path -> {
				if (Files.isDirectory(path) || !path.toString().endsWith(".js") || path.toString().endsWith("_FIXTURE.js")) {
					return;
				}
				Path shortName = root.relativize(path);
				String source;
				try {
					source = Files.readString(path);
				} catch (IOException e) {
					throw new RuntimeException("failed to load tests", e);
				}
				Test262Info info = extractTest262Info(shortName.toString(), source);
				if (info == null) { // parse failure or not module
					throw new RuntimeException("Failed to parse frontmatter for " + path);
				} else if (!info.module) {
					return; // we are only interested in modules
				}
				params.add(new Object[] { root, path, info, shortName.toString() });
			});
		} catch (IOException e) {
			throw new RuntimeException("failed to load tests", e);
		}
		return params;
	}

	@Test
	public void runSafe() {
		var test = buildTest262Test(info.source, this.path, info, new PiercedModuleBundler(), BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.SAFE), XFAIL_AT_PARSE);
		if (test.isNothing()) {
			return;
		}
		runTest262Test(includes, test.fromJust(), this.path, info, "SAFE", XFAIL_EXECUTE);
	}

	@Test
	public void runBalanced() {
		var test = buildTest262Test(info.source, this.path, info, new PiercedModuleBundler(), BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.BALANCED), XFAIL_AT_PARSE);
		if (test.isNothing()) {
			return;
		}
		runTest262Test(includes, test.fromJust(), this.path, info, "BALANCED", XFAIL_EXECUTE_BALANCED);
	}

	@Test
	public void runDangerous() {
		var test = buildTest262Test(info.source, this.path, info, new PiercedModuleBundler(), BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS), XFAIL_AT_PARSE);
		if (test.isNothing()) {
			return;
		}
		runTest262Test(includes, test.fromJust(), this.path, info, "DANGEROUS", XFAIL_EXECUTE_DANGEROUS);
	}

	@Test
	public void runThrowingDangerous() {
		var test = buildTest262Test(info.source, this.path, info, new PiercedModuleBundler(), BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS).withThrowOnImportAssignment(true), XFAIL_AT_PARSE_WITH_THROWING_DANGEROUS);
		if (test.isNothing()) {
			return;
		}
		runTest262Test(includes, test.fromJust(), this.path, info, "THROWING_DANGEROUS", XFAIL_EXECUTE_DANGEROUS);
	}

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
		public final String source;

		public Test262Info(@Nonnull String name, @Nonnull Test262Negative negative, boolean noStrict, boolean onlyStrict, boolean async, boolean module, @Nonnull ImmutableList<String> includes, @Nonnull ImmutableList<String> features, @Nonnull String source) {
			this.name = name;
			this.negative = negative;
			this.noStrict = noStrict;
			this.onlyStrict = onlyStrict;
			this.async = async;
			this.module = module;
			this.includes = includes;
			this.features = features;
			this.source = source;
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
		Object rawParsedYaml = YAML_PARSER.load(yaml);
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
		return new Test262Info(path, negativeEnum, noStrict, onlyStrict, async, module, includes, features, source);
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

	private Maybe<Script> buildTest262Test(@Nonnull String source, @Nonnull Path path, @Nonnull Test262Info info, @Nonnull IModuleBundler bundler, @Nonnull BundlerOptions options, @Nonnull Set<String> xfailParse) {
		boolean xfailedParse = xfailParse.contains(info.name) || (info.features.exists(XFAIL_PARSE_FEATURES::contains) && !XPASS_PARSE_DESPITE_FEATURES.contains(info.name));
		try {
			Pair<Script, ImmutableList<EarlyError>> pair = Bundler.bundleStringWithEarlyErrors(options, source, path.toAbsolutePath(), new NodeResolver(LOADER), LOADER, bundler);
			boolean invalidParse = false;
			if ((info.negative == Test262Negative.PARSERESOLUTION) != xfailedParse) {
				invalidParse = true;
			}
			boolean xfailedEarly = XFAIL_EARLY_ERRORS.contains(info.name);
			ImmutableList<EarlyError> earlyErrors = EarlyErrorChecker.validate(pair.left).append(pair.right);
			boolean passEarlyError = earlyErrors.length == 0;
			if (invalidParse) {
				if (passEarlyError) {
					throw new Test262Exception(info.name, "Bundled and should not have: " + path.toString());
				}
				return Maybe.empty();
			}
			if (passEarlyError && (info.negative == Test262Negative.EARLY) != xfailedEarly) {
				throw new Test262Exception(info.name, "Passed early errors and should not have: " + path.toString());
			} else if (!passEarlyError && ((info.negative == Test262Negative.EARLY) == xfailedEarly)) {
				throw new Test262Exception(info.name, "Failed early errors and should not have: " + path.toString(),
					new RuntimeException(earlyErrors.foldLeft((acc, error) -> error.message + "\n" + acc, "")));
			}
			return Maybe.of(pair.left);
		} catch (ModuleLoaderException e) {
			if ((info.negative == Test262Negative.PARSERESOLUTION) == xfailedParse && info.negative != Test262Negative.EARLY && info.negative != Test262Negative.RUNTIME) { // we classify some early and runtime errors as parse errors
				throw new Test262Exception(info.name, "Did not bundle and should have: " + path.toString(), e);
			}
		}
		return Maybe.empty();
	}

	private void runTest262Test(@Nonnull String harness, @Nonnull Script script, @Nonnull Path path, @Nonnull Test262Info info, @Nonnull String category, @Nonnull Set<String> xfailExecute) {
		boolean xfailedExecute = xfailExecute.contains(info.name);

		PolyglotException exception = null;
		try (Context context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()) {
			context.eval("js", harness);
			context.eval("js", CodeGen.codeGen(script));
		} catch (PolyglotException e) {
			exception = e;
		}
		if (exception == null) {
			if ((info.negative == Test262Negative.RUNTIME) != xfailedExecute) {
				throw new Test262Exception(info.name, "Executed and should not have<" + category + ">: " + path);
			}
		} else {
			if ((info.negative == Test262Negative.RUNTIME) == xfailedExecute && info.negative != Test262Negative.RUNTIME) {
				throw new Test262Exception(info.name, "Did not execute and should have<" + category + ">: " + path + "\n" + CodeGen.codeGen(script), exception);
			}
		}
	}

}
