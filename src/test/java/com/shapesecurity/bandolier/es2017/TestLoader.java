package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.loader.IResourceLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TestLoader implements IResourceLoader {
    private Map<String, String> modules = new HashMap<>();

    public TestLoader(Map<String, String> modules) {
        this.modules = modules;
    }

    @NotNull
    @Override
    public Boolean exists(@NotNull Path path) {
        return modules.containsKey(path.toString());
    }

    @NotNull
    @Override
    public String loadResource(@NotNull Path path) throws IOException {
        String code = modules.get(path.toString());
        if (code == null) throw new AssertionError(path);
        return code;
    }
}

