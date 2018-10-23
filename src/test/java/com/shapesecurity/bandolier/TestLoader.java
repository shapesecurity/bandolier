package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.functional.data.HashTable;
import com.shapesecurity.functional.data.Maybe;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class TestLoader implements IResourceLoader {
    private final HashTable<String, String> modules;

    public TestLoader(HashTable<String, String> modules) {
        this.modules = modules;
    }

    @NotNull
    @Override
    public Boolean exists(@NotNull Path path) {
        return modules.containsKey(path.toString());
    }

    @NotNull
    @Override
    public String loadResource(@NotNull Path path) {
        Maybe<String> code = modules.get(path.toString());
        if (code.isNothing()) throw new AssertionError(path);
        return code.fromJust();
    }
}

