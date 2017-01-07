package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.ast.StaticMemberExpression;
import com.shapesecurity.shift.codegen.CodeGen;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class TestUtils {


    static void testResult(String filePath, Object expected, IResolver resolver, IResourceLoader loader) throws Exception {
        Object result = runInNashorn(filePath, resolver, loader);
        if (result instanceof Double) {
            assertEquals((Double) expected, (Double) result, 0.0);
        } else if (result instanceof Integer) {
            assertEquals(((Double) expected).intValue(), (int) result);
        } else {
            assertEquals(expected, result);
        }
    }

    static Script bundleStandard(String filePath, IResolver resolver, IResourceLoader loader) throws ModuleLoaderException {
        return Bundler.bundle(Paths.get(filePath), resolver, loader, new StandardModuleBundler());
    }

    static String toString(Script script) throws ModuleLoaderException {
        ExpressionStatement statement = (ExpressionStatement) script.getStatements().maybeHead().fromJust();
        CallExpression callExpression = (CallExpression) statement.getExpression();
        StaticMemberExpression memberExpression = new StaticMemberExpression("result", callExpression);

        script = new Script(script.getDirectives(), ImmutableList.of(new ExpressionStatement(memberExpression)));
        return CodeGen.codeGen(script, true);
    }

    static void testSource(String filePath, String expected, IResolver resolver, IResourceLoader loader) throws ModuleLoaderException {
        assertEquals(toString(bundleStandard(filePath, resolver, loader)), expected);
    }

    static Object runInNashorn(String filePath, IResolver resolver, IResourceLoader loader) throws Exception {
        String newProgramText = toString(bundleStandard(filePath, resolver, loader));
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

        try {
            return engine.eval(newProgramText);
        } catch (ScriptException e) {
            System.out.println(newProgramText);
            throw e;
        }
    }
}
