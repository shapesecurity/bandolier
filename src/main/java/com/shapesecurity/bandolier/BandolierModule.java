package com.shapesecurity.bandolier;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2016.ast.ExportAllFrom;
import com.shapesecurity.shift.es2016.ast.ExportDeclaration;
import com.shapesecurity.shift.es2016.ast.ExportFrom;
import com.shapesecurity.shift.es2016.ast.Import;
import com.shapesecurity.shift.es2016.ast.ImportDeclaration;
import com.shapesecurity.shift.es2016.ast.ImportNamespace;
import com.shapesecurity.shift.es2016.ast.Module;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
					return ImmutableList.of(((Import) s).moduleSpecifier);
				} else if (s instanceof ImportNamespace) {
					return ImmutableList.of(((ImportNamespace) s).moduleSpecifier);
				}
			} else if (s instanceof ExportDeclaration) {
				if (s instanceof ExportAllFrom) {
					return ImmutableList.of(((ExportAllFrom) s).moduleSpecifier);
				} else if (s instanceof ExportFrom) {
					return ImmutableList.of(((ExportFrom) s).moduleSpecifier);
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
}
