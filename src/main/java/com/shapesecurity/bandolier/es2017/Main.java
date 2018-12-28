package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2017.loader.FileLoader;
import com.shapesecurity.bandolier.es2017.loader.IResourceLoader;
import com.shapesecurity.bandolier.es2017.loader.NodeResolver;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.codegen.CodeGen;

import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) throws Exception {
		if (args.length > 0) {
			IResourceLoader loader = new FileLoader();
			Script bundle = Bundler.bundle(BundlerOptions.NODE_OPTIONS, Paths.get(args[0]).toAbsolutePath(),
										   new NodeResolver(loader),
										   loader, new PiercedModuleBundler());
			System.out.println(CodeGen.codeGen(bundle));
		} else {
			System.err.println("Must provide a filename");
		}
	}
}
