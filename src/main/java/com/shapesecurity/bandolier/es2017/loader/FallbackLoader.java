package com.shapesecurity.bandolier.es2017.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FallbackLoader implements IResourceLoader {
    @Nonnull
    private final Iterable<IResourceLoader> loaders;

    public FallbackLoader(@Nonnull IResourceLoader primary, @Nonnull IResourceLoader fallback, @Nonnull IResourceLoader ...fallbacks) {
        this.loaders = Stream.concat(Stream.of(primary, fallback), Stream.of(fallbacks)).collect(Collectors.toList());
    }

    public FallbackLoader(@Nonnull Iterable<IResourceLoader> loaders) {
        this.loaders = loaders;
    }

    @Override
    @Nonnull
    public Boolean exists(@Nonnull Path path) {
        for (IResourceLoader loader : this.loaders) {
            if (loader.exists(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nonnull
    public String loadResource(@Nonnull Path path) throws IOException {
        for (IResourceLoader loader : this.loaders) {
            if (loader.exists(path)) {
                return loader.loadResource(path);
            }
        }
        throw new IOException("Failed to load resource at path " + path.toString());
    }
}
