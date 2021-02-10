package com.shapesecurity.bandolier.es2017.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;

public class RootedFileLoader extends FileLoader {
    @Nonnull
    private final Path root;

    public RootedFileLoader(@Nonnull Path root) {
        super();
        this.root = root.normalize();
    }

    @Override
    @Nonnull
    public Boolean existsBackend(@Nonnull Path path) {
        return super.existsBackend(this.resolve(path));
    }

    @Override
    @Nonnull
    public String loadResourceBackend(@Nonnull Path path) throws IOException {
        return super.loadResourceBackend(this.resolve(path));
    }

    // paths cannot be resolved above the given root directory
    @Nonnull
    private Path resolve(@Nonnull Path path) {
        return this.root.resolve("./" + path.normalize().toString()).normalize();
    }
}

