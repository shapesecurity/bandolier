package com.shapesecurity.bandolier.es2018.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;

public class EmptyLoader implements IResourceLoader {
    private static final EmptyLoader INSTANCE = new EmptyLoader();

    public static EmptyLoader getInstance() {
        return INSTANCE;
    }

    private EmptyLoader() {}

    @Override
    @Nonnull
    public Boolean exists(@Nonnull Path path) {
        return false;
    }

    @Override
    @Nonnull
    public String loadResource(@Nonnull Path path) throws IOException {
        throw new IOException("Cannot find resource " + path.toString());
    }
}
