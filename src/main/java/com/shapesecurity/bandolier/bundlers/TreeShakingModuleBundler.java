package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.bandolier.BandolierModule;
import com.shapesecurity.bandolier.RenameState;
import com.shapesecurity.bandolier.RenamingReducer;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.Directive;
import com.shapesecurity.shift.ast.EmptyStatement;
import com.shapesecurity.shift.ast.Export;
import com.shapesecurity.shift.ast.ExportDeclaration;
import com.shapesecurity.shift.ast.ExportDefault;
import com.shapesecurity.shift.ast.ImportDeclaration;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.ast.VariableDeclaration;
import com.shapesecurity.shift.ast.VariableDeclarationStatement;
import com.shapesecurity.shift.scope.GlobalScope;
import com.shapesecurity.shift.scope.ScopeAnalyzer;
import com.shapesecurity.shift.scope.ScopeLookup;
import com.shapesecurity.shift.scope.Variable;
import com.shapesecurity.shift.visitor.Director;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TreeShakingModuleBundler extends ModuleBundler {

	enum VisitStatus {
		Pending, Visited
	}

	public TreeShakingModuleBundler(Map<String, BandolierModule> modules) {
		super(modules);
	}

	@NotNull
	@Override
	public Script bundleEntrypoint(@NotNull String entry) throws Exception {
		ImmutableList<BandolierModule> sorted = this.sort();
		ImmutableList<BandolierModule> renamed = this.rename(sorted);
		return this.render(renamed);
	}

	@NotNull
	ImmutableList<BandolierModule> sort() throws Exception {
		Map<String, VisitStatus> visited = new HashMap<>();
		Deque<BandolierModule> sorted = new ArrayDeque<>();
		for (Map.Entry<String, ? extends BandolierModule> entry : this.modules.entrySet()) {
			if (!visited.containsKey(entry.getKey())) {
				this.visit(entry.getValue(), visited, sorted);
			}
		}
		return ImmutableList.from(StreamSupport.stream(sorted.spliterator(), false).collect(Collectors.toList()));
	}

	private void visit(@NotNull BandolierModule module, @NotNull Map<String, VisitStatus> visited, @NotNull Deque<BandolierModule> sorted) throws Exception {
		if (visited.containsKey(module.getId()) && visited.get(module.getId()) == VisitStatus.Pending) {
			throw new Exception("Cycle detected");
		}
		if (!visited.containsKey(module.getId())) {
			visited.put(module.getId(), VisitStatus.Pending);
			for (String dep : module.getDependencies()) {
				this.visit(this.modules.get(dep), visited, sorted);
			}
			sorted.addLast(module);
			visited.put(module.getId(), VisitStatus.Visited);
		}
	}

	@NotNull
	ImmutableList<BandolierModule> rename(@NotNull ImmutableList<BandolierModule> sorted) {
		RenameState state = new RenameState();
		return sorted.map(mod -> renameModule(mod, state));
	}

	@NotNull
	Script render(@NotNull ImmutableList<BandolierModule> sorted) {
		List<ImmutableList<Statement>> allStatements = new ArrayList<>();
		sorted.forEach(mod -> allStatements.add(renderModule(mod)));

		ImmutableList<Statement> finalBody = ImmutableList.empty();
		for (ImmutableList<Statement> list : allStatements) {
			finalBody = finalBody.append(list);
		}

		return new Script(ImmutableList.of(new Directive("use strict")), finalBody);
	}

	@NotNull
	private ImmutableList<Statement> renderModule(BandolierModule module) {
		return module.getAst().items.map(s -> {
			if (s instanceof ImportDeclaration) {
				return new EmptyStatement();
			} else if (s instanceof ExportDeclaration) {
				if (s instanceof Export) {
					if (((Export) s).declaration instanceof VariableDeclaration) {
						return new VariableDeclarationStatement((VariableDeclaration) (((Export) s).declaration));
					}
					return (Statement) ((Export) s).declaration;
				} else if (s instanceof ExportDefault) {
					return (Statement) ((ExportDefault) s).body;
				}
				return new EmptyStatement();
			}
			return (Statement) s;
		});
	}

	@NotNull
	private BandolierModule renameModule(@NotNull BandolierModule module, @NotNull RenameState state) {
		GlobalScope scope = ScopeAnalyzer.analyze(module.getAst());
		ScopeLookup lookup = new ScopeLookup(scope);
		Module renamed = (Module) Director.reduceModule(new RenamingReducer(state, lookup, scope), module.getAst());
		return new BandolierModule(module.getId(), renamed);
	}
}
