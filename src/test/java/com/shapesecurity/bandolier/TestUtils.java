package com.shapesecurity.bandolier;

import com.shapesecurity.bandolier.bundlers.StandardModuleBundler;
import com.shapesecurity.bandolier.loader.IResolver;
import com.shapesecurity.bandolier.loader.IResourceLoader;
import com.shapesecurity.bandolier.loader.ModuleLoaderException;
import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.es2016.ast.CallExpression;
import com.shapesecurity.shift.es2016.ast.ExpressionStatement;
import com.shapesecurity.shift.es2016.ast.Script;
import com.shapesecurity.shift.es2016.ast.StaticMemberExpression;
import com.shapesecurity.shift.es2016.codegen.CodeGen;

import java.nio.file.Paths;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

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
        ExpressionStatement statement = (ExpressionStatement) script.statements.maybeHead().fromJust();
        CallExpression callExpression = (CallExpression) statement.expression;
        StaticMemberExpression memberExpression = new StaticMemberExpression(callExpression, "result");

        script = new Script(script.directives, ImmutableList.of(new ExpressionStatement(memberExpression)));
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
