package com.shapesecurity.bandolier.es2018;

import com.shapesecurity.bandolier.es2018.loader.IResourceLoader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TestLoader implements IResourceLoader {
    private Map<String, String> modules = new HashMap<>();

    public TestLoader(Map<String, String> modules) {
        this.modules = modules;
    }

    @Nonnull
    @Override
    public Boolean exists(@Nonnull Path path) {
        return modules.containsKey(path.toString());
    }

    @Nonnull
    @Override
    public String loadResource(@Nonnull Path path) throws IOException {
        String code = modules.get(path.toString());
        if (code == null) throw new AssertionError(path);
        return code;
    }
}

