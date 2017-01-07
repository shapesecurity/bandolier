package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.loader.FileLoader;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.NodeResolver;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.codegen.CodeGen;

import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) throws Exception {
		if (args.length > 0) {
			IResourceLoader loader = new FileLoader();
			Script bundle = Bundler.bundle(Paths.get(args[0]).toAbsolutePath(),
										   new NodeResolver(loader),
										   loader, new StandardModuleBundler());
			System.out.print(CodeGen.codeGen(bundle));
		} else {
			System.err.println("Must provide a filename");
		}
	}
}
