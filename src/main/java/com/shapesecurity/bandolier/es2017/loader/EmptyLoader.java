package com.shapesecurity.bandolier.es2017.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class EmptyLoader implements IResourceLoader {
    private static final EmptyLoader INSTANCE = new EmptyLoader();

    public static EmptyLoader getInstance() {
        return INSTANCE;
    }

    private EmptyLoader() {}

    @Override
    @NotNull
    public Boolean exists(@NotNull Path path) {
        return false;
    }

    @Override
    @NotNull
    public String loadResource(@NotNull Path path) throws IOException {
        throw new IOException("Cannot find resource " + path.toString());
    }
}
