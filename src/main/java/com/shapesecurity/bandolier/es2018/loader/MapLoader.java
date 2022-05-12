package com.shapesecurity.bandolier.es2018.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class MapLoader implements IResourceLoader {
    private final Map<Path, String> resources;

    public MapLoader(@Nonnull Map<Path, String> resources) {
        this.resources = resources;
    }

    @Nonnull
    public Boolean exists(@Nonnull Path path) {
        return this.resources.containsKey(normalise(path));
    }

    @Nonnull
    public String loadResource(@Nonnull Path path) throws IOException {
        Path normal = normalise(path);
        if (this.resources.containsKey(normal)) {
            return this.resources.get(normal);
        }
        throw new IOException("Path not found in Map loader: " + normal.toString());
    }

    @Nonnull
    private Path normalise(@Nonnull Path path) {
        return Paths.get("/" + path.normalize().toString()).normalize();
    }
}
