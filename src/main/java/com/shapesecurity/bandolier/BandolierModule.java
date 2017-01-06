package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.ExportAllFrom;
import com.shapesecurity.shift.ast.ExportDeclaration;
import com.shapesecurity.shift.ast.ExportFrom;
import com.shapesecurity.shift.ast.Import;
import com.shapesecurity.shift.ast.ImportDeclaration;
import com.shapesecurity.shift.ast.ImportNamespace;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.scope.GlobalScope;
import com.shapesecurity.shift.scope.ScopeAnalyzer;
import com.shapesecurity.shift.scope.Variable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BandolierModule {
	@NotNull
	private final Module ast;
	@NotNull
	private final String id;

	@NotNull
	private final ImmutableList<ImportDeclaration> imports;
	@NotNull
	private final ImmutableList<ExportDeclaration> exports;

	public BandolierModule(@NotNull String id, @NotNull Module ast) {
		this.id = id;
		this.ast = ast;
		List<ImportDeclaration> imports = new ArrayList<>();
		List<ExportDeclaration> exports = new ArrayList<>();
		this.ast.items.forEach(s -> {
			if (s instanceof ImportDeclaration) {
				imports.add((ImportDeclaration) s);
			} else if (s instanceof ExportDeclaration) {
				exports.add((ExportDeclaration) s);
			}
		});
		this.imports = ImmutableList.from(imports);
		this.exports = ImmutableList.from(exports);
	}

	@NotNull
	public ImmutableList<String> getDependencies() {
		return this.ast.items.bind(s -> {
			if (s instanceof ImportDeclaration) {
				if (s instanceof Import) {
					return ImmutableList.of(((Import) s).getModuleSpecifier());
				} else if (s instanceof ImportNamespace) {
					return ImmutableList.of(((ImportNamespace) s).getModuleSpecifier());
				}
			} else if (s instanceof ExportDeclaration) {
				if (s instanceof ExportAllFrom) {
					return ImmutableList.of(((ExportAllFrom) s).getModuleSpecifier());
				} else if (s instanceof ExportFrom) {
					return ((ExportFrom) s).getModuleSpecifier().toList();
				}
			}
			return ImmutableList.empty();
		});
	}

	@NotNull
	public ImmutableList<ImportDeclaration> getImports() {
		return this.imports;
	}

	@NotNull
	public ImmutableList<ExportDeclaration> getExports() {
		return exports;
	}

	@NotNull
	public Module getAst() {
		return this.ast;
	}

	@NotNull
	public String getId() {
		return id;
	}

	public ImmutableList<Variable> topLevel() {
		GlobalScope globalScope = ScopeAnalyzer.analyze(this.ast);
		return globalScope.children.flatMap((scope) -> ImmutableList.from(StreamSupport.stream(scope.variables().spliterator(), false).collect(Collectors.toList())));
	}
}
