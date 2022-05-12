package com.shapesecurity.bandolier.es2018;

import com.shapesecurity.bandolier.es2018.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2018.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2018.loader.FileLoader;
import com.shapesecurity.bandolier.es2018.loader.IResourceLoader;
import com.shapesecurity.bandolier.es2018.loader.NodeResolver;
import com.shapesecurity.shift.es2018.ast.Script;
import com.shapesecurity.shift.es2018.codegen.CodeGen;

import java.nio.file.Paths;

public class Main {

	private static final void usage() {
		System.out.println("Arguments: [OPTIONS] [target module]\n" +
			"Options:\n" +
			"	-n --node		Node import resolution strategy\n" +
			"	-s --spec		ECMAScript import resolution strategy\n" +
			"	-h --help		Show this help menu");
	}

	public static void main(String[] args) throws Exception {
		BundlerOptions options = BundlerOptions.DEFAULT_OPTIONS;
		String filename = null;
		for (String arg : args) {
			if (arg.equals("-n") || arg.equals("--node")) {
				options = BundlerOptions.NODE_OPTIONS;
			} else if (arg.equals("-s") || arg.equals("--spec")) {
				options = BundlerOptions.SPEC_OPTIONS;
			} else if (arg.equals("-h") || arg.equals("--help")) {
				usage();
				return;
			} else if (filename == null) {
				filename = arg;
			} else {
				System.err.println("Invalid argument: " + arg);
				usage();
				return;
			}
		}
		if (filename == null) {
			System.err.println("No target module specified.");
			usage();
			return;
		}
		IResourceLoader loader = new FileLoader();
		Script bundle = Bundler.bundle(options, Paths.get(filename).toAbsolutePath(),
									   new NodeResolver(loader),
									   loader, new PiercedModuleBundler());
		System.out.println(CodeGen.codeGen(bundle));
	}
}
