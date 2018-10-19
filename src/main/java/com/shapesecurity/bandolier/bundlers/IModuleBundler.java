package com.shapesecurity.bandolier.bundlers;

<<<<<<< HEAD
import com.shapesecurity.bandolier.BandolierModule;
=======
import com.shapesecurity.functional.data.HashTable;
>>>>>>> 48571d5... Remove java.util stuff, fixes #7
import com.shapesecurity.shift.es2016.ast.Script;

import org.jetbrains.annotations.NotNull;

public interface IModuleBundler {
	@NotNull
	Script bundleEntrypoint(String entry, HashTable<String, BandolierModule> modules) throws Exception;

}
