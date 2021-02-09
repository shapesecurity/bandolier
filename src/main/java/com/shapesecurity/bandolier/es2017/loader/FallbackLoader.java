package com.shapesecurity.bandolier.es2017.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FallbackLoader implements IResourceLoader {
    @NotNull
    private final Iterable<IResourceLoader> loaders;

    public FallbackLoader(@NotNull IResourceLoader primary, @NotNull IResourceLoader fallback, @NotNull IResourceLoader ...fallbacks) {
        this.loaders = Stream.concat(Stream.of(primary, fallback), Stream.of(fallbacks)).collect(Collectors.toList());
    }

    public FallbackLoader(@NotNull Iterable<IResourceLoader> loaders) {
        this.loaders = loaders;
    }

    @Override
    @NotNull
    public Boolean exists(@NotNull Path path) {
        for (IResourceLoader loader : this.loaders) {
            if (loader.exists(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    public String loadResource(@NotNull Path path) throws IOException {
        for (IResourceLoader loader : this.loaders) {
            if (loader.exists(path)) {
                return loader.loadResource(path);
            }
        }
        throw new IOException("Failed to load resource at path " + path.toString());
    }
}
