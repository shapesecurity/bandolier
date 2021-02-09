package com.shapesecurity.bandolier.es2017.loader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class MapLoader implements IResourceLoader {
    private final Map<Path, String> resources;

    public MapLoader(@NotNull Map<Path, String> resources) {
        this.resources = resources;
    }

    @NotNull
    public Boolean exists(@NotNull Path path) {
        return this.resources.containsKey(normalise(path));
    }

    @NotNull
    public String loadResource(@NotNull Path path) throws IOException {
        Path normal = normalise(path);
        if (this.resources.containsKey(normal)) {
            return this.resources.get(normal);
        }
        throw new IOException("Path not found in Map loader: " + normal.toString());
    }

    @NotNull
    private Path normalise(@NotNull Path path) {
        return Paths.get("/" + path.normalize().toString()).normalize();
    }
}
