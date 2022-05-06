package com.shapesecurity.bandolier.es2017.loader;

import com.shapesecurity.shift.es2018.ast.Module;
import com.shapesecurity.shift.es2018.codegen.CodeGen;
import com.shapesecurity.shift.es2018.parser.JsError;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MapModulesLoader implements IResourceLoader {
    private final Map<Path, Module> resources;
    private final Map<Path, String> stringCache;

    public MapModulesLoader(@Nonnull Map<Path, Module> resources) {
        this.resources = resources;
        stringCache = new HashMap<>();
    }

    @Nonnull
    public Boolean exists(@Nonnull Path path) {
        return this.resources.containsKey(normalise(path));
    }

    @Nonnull
    public String loadResource(@Nonnull Path path) throws IOException {
        Path normal = normalise(path);
        if (this.resources.containsKey(normal)) {
            return stringCache.computeIfAbsent(normal, p -> CodeGen.codeGen(this.resources.get(normal)));
        }
        throw new IOException("Path not found in Map loader: " + normal);
    }

    @Override
    @Nonnull
    public Module loadModule(@Nonnull Path path) throws IOException, JsError {
        Path normal = normalise(path);
        if (this.resources.containsKey(normal)) {
            return this.resources.get(normal);
        }
        throw new IOException("Path not found in Map loader: " + normal);
    }

    @Nonnull
    private Path normalise(@Nonnull Path path) {
        return Paths.get("/" + path.normalize().toString()).normalize();
    }
}
