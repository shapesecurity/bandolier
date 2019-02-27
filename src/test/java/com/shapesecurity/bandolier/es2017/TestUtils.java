package com.shapesecurity.bandolier.es2017;

import com.shapesecurity.bandolier.es2017.bundlers.BundlerOptions;
import com.shapesecurity.bandolier.es2017.bundlers.PiercedModuleBundler;
import com.shapesecurity.bandolier.es2017.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.es2017.loader.IResolver;
import com.shapesecurity.bandolier.es2017.loader.IResourceLoader;
import com.shapesecurity.bandolier.es2017.loader.ModuleLoaderException;
import com.shapesecurity.shift.es2017.ast.Script;
import com.shapesecurity.shift.es2017.codegen.CodeGen;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.nio.file.Paths;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import static org.junit.Assert.assertEquals;

public class TestUtils {

    static void testResult(String filePath, Object expected, IResolver resolver, IResourceLoader loader) throws Exception {
        Object resultStandard = runInNashorn(BundlerOptions.SPEC_OPTIONS, filePath, resolver, loader, false);
        Object[] piercedResults = new Object[3];
        piercedResults[0] = runInNashorn(BundlerOptions.SPEC_OPTIONS, filePath, resolver, loader, true);
        piercedResults[1] = runInNashorn(BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.BALANCED), filePath, resolver, loader, true);
        piercedResults[2] = runInNashorn(BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS), filePath, resolver, loader, true);
        assertEquals(resultStandard, piercedResults[0]);
        assertEquals(resultStandard, piercedResults[1]);
        assertEquals(resultStandard, piercedResults[2]);
        if (resultStandard instanceof Double) {
            assertEquals((Double) expected, (Double) resultStandard, 0.0);
        } else if (resultStandard instanceof Integer) {
            assertEquals(((Double) expected).intValue(), (int) resultStandard);
        } else {
            assertEquals(expected, resultStandard);
        }
    }

    static void testResultPierced(BundlerOptions options, String filePath, Object expected, IResolver resolver, IResourceLoader loader) throws Exception {
        Object result = runInNashorn(options, filePath, resolver, loader, true);
        if (result instanceof Double) {
            assertEquals((Double) expected, (Double) result, 0.0);
        } else if (result instanceof Integer) {
            assertEquals(((Double) expected).intValue(), (int) result);
        } else {
            assertEquals(expected, result);
        }
    }

    static void testResultPierced(String filePath, Object expected, IResolver resolver, IResourceLoader loader) throws Exception {
        Object[] piercedResults = new Object[3];
        piercedResults[0] = runInNashorn(BundlerOptions.SPEC_OPTIONS, filePath, resolver, loader, true);
        piercedResults[1] = runInNashorn(BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.BALANCED), filePath, resolver, loader, true);
        piercedResults[2] = runInNashorn(BundlerOptions.SPEC_OPTIONS.withDangerLevel(BundlerOptions.DangerLevel.DANGEROUS), filePath, resolver, loader, true);
        assertEquals(piercedResults[0], piercedResults[1]);
        assertEquals(piercedResults[0], piercedResults[2]);
        if (piercedResults[0] instanceof Double) {
            assertEquals((Double) expected, (Double) piercedResults[0], 0.0);
        } else if (piercedResults[0] instanceof Integer) {
            assertEquals(((Double) expected).intValue(), (int) piercedResults[0]);
        } else {
            assertEquals(expected, piercedResults[0]);
        }
    }

    static Script bundlePierced(BundlerOptions options, String filePath, IResolver resolver, IResourceLoader loader) throws ModuleLoaderException {
        return Bundler.bundle(options, Paths.get(filePath), resolver, loader, new PiercedModuleBundler());
    }

    static Script bundleStandard(BundlerOptions options, String filePath, IResolver resolver, IResourceLoader loader) throws ModuleLoaderException {
        return Bundler.bundle(options, Paths.get(filePath), resolver, loader, new StandardModuleBundler());
    }

    static String toString(Script script) {
        return CodeGen.codeGen(script, true);
    }

    static Object runInNashorn(BundlerOptions options, String filePath, IResolver resolver, IResourceLoader loader, boolean pierced) throws Exception {
        String newProgramText;
        if (pierced) {
            newProgramText = toString(bundlePierced(options, filePath, resolver, loader));
        } else {
            newProgramText = toString(bundleStandard(options, filePath, resolver, loader));
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        try {
            Object returned = engine.eval(newProgramText);
            returned = ((ScriptObjectMirror)returned).get("result");
            // resolving weird nashorn inconsistency
            if (returned instanceof Integer) {
                returned = ((Integer) returned).doubleValue();
            }
            return returned;
        } catch (ScriptException e) {
            System.out.println(newProgramText);
            throw e;
        }
    }
}
