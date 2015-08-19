package com.shapesecurity.es6bundler.loader;


public class ModuleLoaderException extends Exception {
	public ModuleLoaderException(String module, Exception cause) {
		super("Module Loader Exception: module " + module + " cannot be loaded: " + cause.getMessage(), cause);
	}
}