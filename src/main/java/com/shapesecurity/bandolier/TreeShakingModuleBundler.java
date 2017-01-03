package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.visitor.Director;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TreeShakingModuleBundler extends ModuleBundler {
	public TreeShakingModuleBundler(Map<String, Module> modules) {
		super(modules);
	}

	@NotNull
	@Override
	public Script bundleEntrypoint(String entry) {
		List<ImmutableList<Statement>> allStatements = new ArrayList<>();
		this.modules.forEach((path, mod) -> {
			Module m = Director.reduceModule(new ExportStrippingReducer(), mod);

			allStatements.add(mod.items.filter(stmt -> stmt instanceof Statement).map(stmt -> (Statement) stmt));
		});

		ImmutableList<Statement> finalBody = ImmutableList.empty();
		for (ImmutableList<Statement> list : allStatements) {
			finalBody = finalBody.append(list);
		}

		return new Script(ImmutableList.empty(), finalBody);
	}

	class ExportStrippingReducer extends ReconstructingReducer {

		@Override
		public ImportDeclarationExportDeclarationStatement reduceExport(@NotNull Export node, @NotNull Node declaration) {
			return (Statement) declaration;
		}

	}
}
