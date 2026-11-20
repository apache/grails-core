/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.urlmappings

import groovy.transform.CompileStatic

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

/**
 * Reads the static URL mappings a source file states.
 *
 * <p>Parsing stops once the syntax tree exists, so a mapping is never executed to be indexed.
 * A value that is not a constant is left out: the runtime evaluation of that mapping stays
 * authoritative, and the index must not guess it.</p>
 */
@CompileStatic
final class UrlMappingsSourceIndex {

    private static final Set<String> HTTP_METHODS = Set.of(
            'get', 'post', 'put', 'patch', 'delete', 'options', 'head')

    private static final Set<String> RECORDED_KEYS = Set.of(
            'controller', 'action', 'method', 'namespace', 'plugin', 'view')

    private UrlMappingsSourceIndex() {
    }

    /**
     * @param sources {@code *UrlMappings.groovy} files from one project
     * @return the constant mappings those files declare, in a stable order
     */
    static List<Mapping> read(List<File> sources) {
        List<Mapping> mappings = []
        List<File> files = new ArrayList<>(sources)
        files.sort { File left, File right -> left.absolutePath <=> right.absolutePath }
        for (File source : files) {
            mappings.addAll(readOne(source))
        }
        return ordered(mappings)
    }

    static List<Mapping> ordered(List<Mapping> mappings) {
        List<Mapping> copy = new ArrayList<>(mappings)
        copy.sort { Mapping left, Mapping right ->
            int pattern = left.pattern <=> right.pattern
            if (pattern != 0) {
                return pattern
            }
            int controller = (left.controller ?: '') <=> (right.controller ?: '')
            if (controller != 0) {
                return controller
            }
            int action = (left.action ?: '') <=> (right.action ?: '')
            if (action != 0) {
                return action
            }
            return (left.httpMethod ?: '') <=> (right.httpMethod ?: '')
        }
        return copy
    }

    private static List<Mapping> readOne(File source) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.setSourceEncoding('UTF-8')
        CompilationUnit unit = new CompilationUnit(configuration)
        unit.addSource(source)
        unit.compile(Phases.CONVERSION)
        List<Mapping> mappings = []
        for (ModuleNode module : unit.getAST().getModules()) {
            for (ClassNode type : module.getClasses()) {
                if (!type.getNameWithoutPackage().endsWith('UrlMappings')) {
                    continue
                }
                ClosureExpression closure = mappingsClosure(type)
                if (closure != null && closure.getCode() != null) {
                    walk(closure.getCode(), '', mappings)
                }
            }
        }
        return mappings
    }

    private static ClosureExpression mappingsClosure(ClassNode type) {
        for (FieldNode field : type.getFields()) {
            if ('mappings' == field.getName() && field.getInitialExpression() instanceof ClosureExpression) {
                return (ClosureExpression) field.getInitialExpression()
            }
        }
        return null
    }

    private static void walk(Statement statement, String prefix, List<Mapping> into) {
        if (statement instanceof BlockStatement) {
            for (Statement child : ((BlockStatement) statement).getStatements()) {
                walk(child, prefix, into)
            }
            return
        }
        if (statement instanceof ExpressionStatement) {
            visit(((ExpressionStatement) statement).getExpression(), prefix, into)
        }
    }

    private static void visit(Expression expression, String prefix, List<Mapping> into) {
        if (!(expression instanceof MethodCallExpression)) {
            return
        }
        MethodCallExpression call = (MethodCallExpression) expression
        String name = methodName(call)
        if (name == null) {
            return
        }
        if ('group' == name) {
            visitGroup(call, prefix, into)
            return
        }
        if (HTTP_METHODS.contains(name)) {
            List<Expression> httpArgs = arguments(call)
            if (httpArgs.size() == 1 && httpArgs.get(0) instanceof MethodCallExpression) {
                MethodCallExpression nested = (MethodCallExpression) httpArgs.get(0)
                String pattern = methodName(nested)
                if (pattern != null && pattern.startsWith('/')) {
                    record(join(prefix, pattern), name.toUpperCase(Locale.ROOT), arguments(nested), into)
                    return
                }
            }
            visitHttp(call, name.toUpperCase(Locale.ROOT), prefix, into)
            return
        }
        if (name.startsWith('/')) {
            record(join(prefix, name), null, arguments(call), into)
        }
    }

    private static void visitGroup(MethodCallExpression call, String prefix, List<Mapping> into) {
        List<Expression> args = arguments(call)
        if (args.size() < 2 || !(args.get(0) instanceof ConstantExpression)) {
            return
        }
        Object value = ((ConstantExpression) args.get(0)).getValue()
        if (!(value instanceof String) || !(args.get(1) instanceof ClosureExpression)) {
            return
        }
        ClosureExpression body = (ClosureExpression) args.get(1)
        if (body.getCode() != null) {
            walk(body.getCode(), join(prefix, (String) value), into)
        }
    }

    private static void visitHttp(MethodCallExpression call, String httpMethod, String prefix, List<Mapping> into) {
        List<Expression> args = arguments(call)
        if (args.isEmpty() || !(args.get(0) instanceof ConstantExpression)) {
            return
        }
        Object value = ((ConstantExpression) args.get(0)).getValue()
        if (!(value instanceof String) || !((String) value).startsWith('/')) {
            return
        }
        record(join(prefix, (String) value), httpMethod, args.subList(1, args.size()), into)
    }

    private static void record(String pattern, String httpMethod, List<Expression> args, List<Mapping> into) {
        if (!pattern.startsWith('/')) {
            return
        }
        ParsedMap parsed = new ParsedMap()
        parsed.add(args)
        if (parsed.dynamic) {
            return
        }
        String controller = parsed.values.get('controller')
        String view = parsed.values.get('view')
        if ((controller == null || controller.isEmpty()) && (view == null || view.isEmpty())) {
            return
        }
        String method = parsed.values.get('method') ?: httpMethod
        into.add(new Mapping(pattern, controller, parsed.values.get('action'), method,
                parsed.values.get('namespace'), parsed.values.get('plugin'), view))
    }

    private static String join(String prefix, String pattern) {
        if (prefix == null || prefix.isEmpty()) {
            return pattern
        }
        if (prefix.endsWith('/') && pattern.startsWith('/')) {
            return prefix + pattern.substring(1)
        }
        if (!prefix.endsWith('/') && !pattern.startsWith('/')) {
            return prefix + '/' + pattern
        }
        return prefix + pattern
    }

    private static String methodName(MethodCallExpression call) {
        Expression method = call.getMethod()
        if (!(method instanceof ConstantExpression)) {
            return null
        }
        Object value = ((ConstantExpression) method).getValue()
        return value == null ? null : value.toString()
    }

    private static List<Expression> arguments(MethodCallExpression call) {
        Expression arguments = call.getArguments()
        if (arguments instanceof TupleExpression) {
            return ((TupleExpression) arguments).getExpressions()
        }
        return Collections.singletonList(arguments)
    }

    private static final class ParsedMap {

        private final Map<String, String> values = new LinkedHashMap<>()
        private boolean dynamic

        private void add(List<Expression> args) {
            for (Expression arg : args) {
                if (arg instanceof MapExpression) {
                    for (MapEntryExpression entry : ((MapExpression) arg).getMapEntryExpressions()) {
                        addEntry(entry)
                    }
                }
                else if (arg instanceof MapEntryExpression) {
                    addEntry((MapEntryExpression) arg)
                }
            }
        }

        private void addEntry(MapEntryExpression entry) {
            if (!(entry.getKeyExpression() instanceof ConstantExpression)) {
                return
            }
            String key = String.valueOf(((ConstantExpression) entry.getKeyExpression()).getValue())
            if (!RECORDED_KEYS.contains(key)) {
                return
            }
            Expression value = entry.getValueExpression()
            if (!(value instanceof ConstantExpression) || ((ConstantExpression) value).getValue() == null) {
                dynamic = true
                return
            }
            values.put(key, ((ConstantExpression) value).getValue().toString())
        }
    }

    /**
     * One constant mapping taken from source.
     */
    static final class Mapping {

        final String pattern
        final String controller
        final String action
        final String httpMethod
        final String namespace
        final String plugin
        final String view

        Mapping(String pattern, String controller, String action, String httpMethod, String namespace,
                String plugin, String view) {
            this.pattern = pattern
            this.controller = controller
            this.action = action
            this.httpMethod = httpMethod
            this.namespace = namespace
            this.plugin = plugin
            this.view = view
        }
    }
}
