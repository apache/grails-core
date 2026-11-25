/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.grails.security.cli

import groovy.transform.CompileStatic

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types

/**
 * Adds the definition of a bean, {@code name(Type)}, to the bean definitions a project is started with: the
 * closure that {@code grails-app/conf/spring/resources.groovy} leaves in {@code beans}, or the closure that
 * {@code doWithSpring()} of a plugin class returns.
 *
 * <p>An application runs {@code resources.groovy} and reads {@code beans} when the script has ended, so of several
 * assignments the last one counts. The file is read as Groovy, not as text, and the definition is added only when
 * that closure is known without running the script: every assignment of {@code beans} is a statement of the
 * script itself, {@code beans = { ... }}, and nothing else in the script names {@code beans} or returns from it.
 * The definition then goes to the start of the last of those closures. Anything else is refused with the reason,
 * and the file is left as it is, as it is when the changed file would not have the definition where it is
 * read.</p>
 */
@CompileStatic
class BeanDefinitionWriter {

    private static final String BEANS = 'beans'
    private static final String DO_WITH_SPRING = 'doWithSpring'
    private static final String ARROW = '->'
    private static final String NEW_LINE = '\n'

    private final String beanName
    private final String packageName
    private final String className

    /**
     * @param beanName the name of the bean
     * @param packageName the package of the class of the bean
     * @param className the name of the class of the bean, without its package
     */
    BeanDefinitionWriter(String beanName, String packageName, String className) {
        this.beanName = beanName
        this.packageName = packageName
        this.className = className
    }

    /**
     * @return the definition as it is written, {@code name(Type)}
     */
    String getDefinition() {
        "${beanName}(${className})"
    }

    /**
     * @return the import the definition needs
     */
    String getBeanImport() {
        "import ${packageName}.${className}"
    }

    /**
     * Adds the definition to {@code resources.groovy}, which is created when it is not there.
     *
     * @param resources the file
     * @param indent what the line of the definition starts with
     * @return {@code null} when the closure an application reads has the definition now, and otherwise what the
     * file has that kept the definition out of it, as the end of a sentence that starts with the name of the file
     */
    String addToResources(File resources, String indent) {
        List<String> lines = resources.isFile() ? resources.readLines('UTF-8') : [] as List<String>
        if (lines.every { String line -> line.trim().isEmpty() }) {
            resources.parentFile.mkdirs()
            write(resources, [beanImport, '', "${BEANS} = {".toString(), indent + definition, '}'])
            return null
        }
        BeansScript script
        try {
            script = new BeansScript(parse(lines, resources.name))
        } catch (CompilationFailedException e) {
            return unreadable(e)
        }
        if (script.closures.isEmpty()) {
            if (script.undecided() != null) {
                return script.undecided()
            }
            if (script.hasCode) {
                return "has no line with '${BEANS} = {'"
            }
            lines.addAll(["${BEANS} = {".toString(), indent + definition, '}'])
            addImport(lines, script.module)
            write(resources, lines)
            return null
        }
        String undecided = script.undecided()
        if (undecided != null) {
            return undecided
        }
        change(resources, lines, script.module, script.closures.last(), indent) { ModuleNode changed ->
            BeansScript after = new BeansScript(changed)
            after.closures.size() == script.closures.size() && after.undecided() == null ? after.closures.last() : null
        }
    }

    /**
     * Adds the definition to the closure that {@code doWithSpring()} of a plugin class returns.
     *
     * @param pluginClass the source file of the plugin class
     * @param indent what the line of the definition starts with
     * @return {@code null} when the closure has the definition now, and otherwise what the file has that kept the
     * definition out of it, as the end of a sentence that starts with the name of the file
     */
    String addToPluginClass(File pluginClass, String indent) {
        List<String> lines = pluginClass.readLines('UTF-8')
        ModuleNode module
        try {
            module = parse(lines, pluginClass.name)
        } catch (CompilationFailedException e) {
            return unreadable(e)
        }
        ClosureExpression closure = returnedByDoWithSpring(module)
        if (closure == null) {
            return "has no closure that '${DO_WITH_SPRING}()' returns as its only statement"
        }
        change(pluginClass, lines, module, closure, indent) { ModuleNode changed -> returnedByDoWithSpring(changed) }
    }

    // adds the definition and the import, and writes the file when the closure that is read then has the definition
    private String change(File file, List<String> lines, ModuleNode module, ClosureExpression closure, String indent,
                          Closure<ClosureExpression> closureOf) {
        MethodCallExpression present = definitionIn(closure)
        if (present != null) {
            return isOfTheClass(present) ? null : "has a bean '${beanName}' of another class in line ${present.lineNumber}"
        }
        String refused = "has a closure in line ${closure.lineNumber} that the command cannot add a line to"
        List<String> changed = new ArrayList<String>(lines)
        if (!addDefinition(changed, closure, indent)) {
            return refused
        }
        addImport(changed, module)
        ClosureExpression read
        try {
            read = closureOf.call(parse(changed, file.name))
        } catch (CompilationFailedException ignored) {
            return refused
        }
        MethodCallExpression added = read == null ? null : definitionIn(read)
        if (added == null || !isOfTheClass(added) || statements(read).size() != statements(closure).size() + 1) {
            return refused
        }
        write(file, changed)
        null
    }

    private static String unreadable(CompilationFailedException e) {
        String message = e.message?.readLines()?.find { String line -> line.trim() && !line.contains('startup failed') }
        "cannot be read as Groovy${message ? ' (' + message.trim() + ')' : ''}"
    }

    private static ModuleNode parse(List<String> lines, String name) {
        SourceUnit source = SourceUnit.create(name, lines.join(NEW_LINE) + NEW_LINE)
        source.parse()
        source.completePhase()
        source.nextPhase()
        source.convert()
        if (source.errorCollector.hasErrors()) {
            throw new MultipleCompilationErrorsException(source.errorCollector)
        }
        source.AST
    }

    // the closure doWithSpring() returns, when the method does nothing but return a closure it is written with
    private static ClosureExpression returnedByDoWithSpring(ModuleNode module) {
        List<MethodNode> methods = []
        for (ClassNode each : module.classes) {
            methods.addAll(each.methods.findAll { MethodNode method -> method.name == DO_WITH_SPRING && method.parameters.length == 0 })
        }
        if (methods.size() != 1 || !(methods[0].code instanceof BlockStatement)) {
            return null
        }
        List<Statement> body = ((BlockStatement) methods[0].code).statements
        if (body.size() != 1) {
            return null
        }
        Statement only = body[0]
        Expression returned = only instanceof ReturnStatement ? ((ReturnStatement) only).expression :
                only instanceof ExpressionStatement ? ((ExpressionStatement) only).expression : null
        returned instanceof ClosureExpression ? (ClosureExpression) returned : null
    }

    private static List<Statement> statements(ClosureExpression closure) {
        closure.code instanceof BlockStatement ? ((BlockStatement) closure.code).statements : [] as List<Statement>
    }

    // the statement of the closure that defines a bean of the name
    private MethodCallExpression definitionIn(ClosureExpression closure) {
        for (Statement statement : statements(closure)) {
            if (statement instanceof ExpressionStatement && ((ExpressionStatement) statement).expression instanceof MethodCallExpression) {
                MethodCallExpression call = (MethodCallExpression) ((ExpressionStatement) statement).expression
                if (call.implicitThis && call.methodAsString == beanName) {
                    return call
                }
            }
        }
        null
    }

    private boolean isOfTheClass(MethodCallExpression call) {
        if (!(call.arguments instanceof ArgumentListExpression)) {
            return false
        }
        List<Expression> arguments = ((ArgumentListExpression) call.arguments).expressions
        arguments && arguments[0].text in [className, "${packageName}.${className}".toString()]
    }

    // adds the line of the definition after what opens the closure: its brace, or the arrow after its parameters
    private boolean addDefinition(List<String> lines, ClosureExpression closure, String indent) {
        int line = closure.lineNumber - 1
        int column = closure.columnNumber
        if (line < 0 || line >= lines.size() || column < 1 || column > lines[line].length() || lines[line].charAt(column - 1) != ('{' as char)) {
            return false
        }
        if (closure.parameters == null || closure.parameters.length > 0) {
            int arrow = lines[line].indexOf(ARROW, column)
            while (arrow < 0 && ++line < lines.size()) {
                arrow = lines[line].indexOf(ARROW)
            }
            if (arrow < 0) {
                return false
            }
            column = arrow + ARROW.length()
        }
        List<Statement> statements = statements(closure)
        boolean statementOnLine = statements && statements[0].lineNumber == line + 1
        if (!statementOnLine && closure.lastLineNumber != line + 1) {
            lines.add(line + 1, indent + definition)
            return true
        }
        String opening = lines[line]
        String outer = opening.substring(0, opening.length() - opening.stripLeading().length())
        lines[line] = opening.substring(0, column).stripTrailing()
        lines.add(line + 1, indent + definition)
        lines.add(line + 2, (statementOnLine ? indent : outer) + opening.substring(column).trim())
        true
    }

    private void addImport(List<String> lines, ModuleNode module) {
        String qualified = "${packageName}.${className}"
        String packageOfFile = module.packageName ?: ''
        if (packageOfFile == packageName + '.' ||
                module.imports.any { ImportNode each -> each.type.name == qualified } ||
                module.starImports.any { ImportNode each -> each.packageName == packageName + '.' }) {
            return
        }
        if (module.package != null && module.package.lastLineNumber > 0) {
            lines.addAll(module.package.lastLineNumber, ['', beanImport])
        } else {
            lines.add(lines && lines[0].startsWith('#!') ? 1 : 0, beanImport)
        }
    }

    private static void write(File target, List<String> lines) {
        target.withWriter('UTF-8') { Writer writer ->
            lines.each { String line ->
                writer.write(line)
                writer.write(System.lineSeparator())
            }
        }
    }

    /**
     * What a script says about {@code beans}: the closures it assigns with a statement of its own, in their
     * order, and whether anything else in it can decide the value {@code beans} has when the script ends.
     */
    private static class BeansScript {

        final ModuleNode module
        final List<ClosureExpression> closures = []
        boolean hasCode
        private final Mentions mentions = new Mentions()

        BeansScript(ModuleNode module) {
            this.module = module
            mentions.inScript = true
            for (Statement statement : module.statementBlock.statements) {
                if (statement instanceof ReturnStatement && statement.lineNumber < 1) {
                    // what the parser gives a script without a statement
                    continue
                }
                ClosureExpression assigned = assignedToBeans(statement)
                if (assigned != null) {
                    closures.add(assigned)
                } else {
                    hasCode = true
                    statement.visit(mentions)
                }
            }
            mentions.inScript = false
            for (MethodNode method : module.methods) {
                hasCode = true
                mentions.visitMethod(method)
            }
            for (ClassNode each : module.classes) {
                if (!each.script) {
                    hasCode = true
                    mentions.visitClass(each)
                }
            }
        }

        /**
         * @return {@code null} when the last of the closures is the value {@code beans} has when the script ends,
         * and otherwise what the script has that can give it another value
         */
        String undecided() {
            if (mentions.beans != null) {
                return "sets or reads '${BEANS}' in line ${mentions.beans.lineNumber}, in another way than with " +
                        "'${BEANS} = { ... }' as a statement of the script, so which closure the application reads is not known"
            }
            if (mentions.returns != null) {
                return "returns in line ${mentions.returns.lineNumber}, so which closure the application reads is not known"
            }
            null
        }

        private static ClosureExpression assignedToBeans(Statement statement) {
            if (!(statement instanceof ExpressionStatement)) {
                return null
            }
            Expression expression = ((ExpressionStatement) statement).expression
            if (expression.getClass() != BinaryExpression) {
                return null
            }
            BinaryExpression assignment = (BinaryExpression) expression
            Expression target = assignment.leftExpression
            boolean beans = target instanceof VariableExpression && ((VariableExpression) target).name == BEANS
            beans && assignment.operation.type == Types.EQUAL && assignment.rightExpression instanceof ClosureExpression ?
                    (ClosureExpression) assignment.rightExpression : null
        }
    }

    /**
     * Finds where code names {@code beans}, as a variable, a property, a method or a string, and where the code of
     * the script itself returns.
     */
    private static class Mentions extends ClassCodeVisitorSupport {

        ASTNode beans
        ASTNode returns
        boolean inScript
        private int closures

        @Override
        protected SourceUnit getSourceUnit() {
            null
        }

        @Override
        void visitVariableExpression(VariableExpression expression) {
            if (expression.name == BEANS) {
                beans = beans ?: expression
            }
            super.visitVariableExpression(expression)
        }

        @Override
        void visitConstantExpression(ConstantExpression expression) {
            if (BEANS == expression.value) {
                beans = beans ?: expression
            }
            super.visitConstantExpression(expression)
        }

        @Override
        void visitField(FieldNode node) {
            if (node.name == BEANS) {
                beans = beans ?: node
            }
            super.visitField(node)
        }

        @Override
        void visitClosureExpression(ClosureExpression expression) {
            closures++
            super.visitClosureExpression(expression)
            closures--
        }

        @Override
        void visitReturnStatement(ReturnStatement statement) {
            if (inScript && closures == 0) {
                returns = returns ?: statement
            }
            super.visitReturnStatement(statement)
        }
    }
}
