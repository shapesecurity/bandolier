package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.bandolier.BandolierModule;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.shift.es2016.ast.Script;

import org.jetbrains.annotations.NotNull;

public interface IModuleBundler {
	@NotNull
	Script bundleEntrypoint(String entry, HashTable<String, BandolierModule> modules) throws Exception;

}
