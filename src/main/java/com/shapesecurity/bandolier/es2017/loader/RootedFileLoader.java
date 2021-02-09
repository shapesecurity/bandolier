package com.shapesecurity.bandolier.es2017.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

class RootedFileLoader extends FileLoader {
    @NotNull
    private final Path root;

    public RootedFileLoader(@NotNull Path root) {
        super();
        this.root = root.normalize();
    }

    @Override
    @NotNull
    public Boolean existsBackend(@NotNull Path path) {
        return super.existsBackend(this.resolve(path));
    }

    @Override
    @NotNull
    public String loadResourceBackend(@NotNull Path path) throws IOException {
        return super.loadResourceBackend(this.resolve(path));
    }

    // paths cannot be resolved above the given root directory
    @NotNull
    private Path resolve(@NotNull Path path) {
        return this.root.resolve("./" + path.normalize().toString()).normalize();
    }
}

