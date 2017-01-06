package com.shapesecurity.bandolier.bundlers;

import com.shapesecurity.bandolier.BandolierModule;
import com.shapesecurity.bandolier.bundlers.TreeShakingModuleBundler;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TreeShakingModuleBundlerTest {

    @Test
    public void basic_sort() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("/a", "export var a = 1");
        m.put("/main", "import { a } from '/a';");
        BandolierModule[] sorted = makeBundler(m).sort().toArray(new BandolierModule[2]);
        assertEquals("/a", sorted[0].getId());
        assertEquals("/main", sorted[1].getId());
    }

    @Test
    public void sort_of_many_deps() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("/b", "import { c } from '/c';");
        m.put("/c", "export var c = 1;");
        m.put("/a", "import { b } from '/b'; export var a = 1;");
        m.put("/main", "import { a } from '/a';");
        BandolierModule[] sorted = makeBundler(m).sort().toArray(new BandolierModule[2]);

        assertEquals("/c", sorted[0].getId());
        assertEquals("/b", sorted[1].getId());
        assertEquals("/a", sorted[2].getId());
        assertEquals("/main", sorted[3].getId());
    }

    @Test
    public void sort_of_diamond_deps() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("/c", "export var c = 1;");
        m.put("/b", "import { c } from '/c'; export var b = 1;");
        m.put("/a", "import { c } from '/c'; export var a = 1;");
        m.put("/main", "import { a } from '/a'; import { b } from '/b';");
        BandolierModule[] sorted = makeBundler(m).sort().toArray(new BandolierModule[2]);

        assertEquals("/c", sorted[0].getId());
        assertTrue(sorted[1].getId().equals("/a") || sorted[1].getId().equals("/b"));
        assertTrue(sorted[2].getId().equals("/a") || sorted[2].getId().equals("/b"));
        assertEquals("/main", sorted[3].getId());
    }

    @Test
    public void sort_of_cycle_throws() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("/b", "import { c } from '/main'; export var b = 1;");
        m.put("/a", "import { b } from '/b'; export var a = 1;");
        m.put("/main", "import { a } from '/a'");

        try {
            makeBundler(m).sort().toArray(new BandolierModule[2]);
            Assert.fail("Should throw exception for cycle");
        } catch (Exception ignored) { }
    }

    @Test
    public void rename_does_not_clash_globals() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("/a", "export var a = 1;");
        m.put("/main", "import { a } from '/a'; var result = a");

        TreeShakingModuleBundler bundler = makeBundler(m);
        ImmutableList<BandolierModule> sorted = bundler.sort();
        ImmutableList<BandolierModule> renamed = bundler.rename(sorted);

        BandolierModule[] mods = renamed.toArray(new BandolierModule[2]);
        mods[0].topLevel().forEach(var -> {
            mods[1].topLevel().forEach(var2 -> {
                assertNotEquals(var.name, var2.name);
            });
        });
    }

    private TreeShakingModuleBundler makeBundler(Map<String, String> modules) throws JsError {
        Map<String, BandolierModule> bandolierModules = new HashMap<>();
        for (Map.Entry<String, String> entry : modules.entrySet()) {
        	bandolierModules.put(
        	    entry.getKey(),
                new BandolierModule(entry.getKey(), Parser.parseModule(entry.getValue())));
        }
        return new TreeShakingModuleBundler(bandolierModules);
    }
}
