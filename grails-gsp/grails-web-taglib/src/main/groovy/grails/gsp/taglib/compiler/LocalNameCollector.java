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
package grails.gsp.taglib.compiler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;

/**
 * Collects every name declared within a body: its parameters, its local variables, the parameters of
 * the closures inside it, the variables its loops introduce and the names its catch blocks bind.
 *
 * <p>Used to decide whether an unqualified call such as {@code message(code: 'x')} could be reaching
 * something local rather than a tag. Scope is not tracked, so a name declared anywhere in the body
 * counts throughout it. That errs towards leaving a call to be dispatched dynamically, which is only
 * a missed optimisation, rather than towards sending it somewhere the author did not write.
 *
 * @since 8.0.0
 */
final class LocalNameCollector extends CodeVisitorSupport {

    /**
     * The parameter a closure that names none still has.
     */
    private static final String IMPLICIT_CLOSURE_PARAMETER = "it";

    private final Set<String> names = new HashSet<>();

    private LocalNameCollector() {
    }

    /**
     * @param code the body to read, or {@code null} when there is none
     * @param parameters the declaring method's parameters, or {@code null} when there are none
     * @return every name declared within, never {@code null}
     */
    static Set<String> collect(Statement code, Parameter[] parameters) {
        LocalNameCollector collector = new LocalNameCollector();
        collector.addParameters(parameters);
        if (code != null) {
            code.visit(collector);
        }
        return collector.names.isEmpty() ? Collections.emptySet() : collector.names;
    }

    private void addParameters(Parameter[] parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            names.add(parameter.getName());
        }
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        if (expression.isMultipleAssignmentDeclaration()) {
            TupleExpression tuple = expression.getTupleExpression();
            for (Expression declared : tuple.getExpressions()) {
                if (declared instanceof VariableExpression variable) {
                    names.add(variable.getName());
                }
            }
        }
        else {
            names.add(expression.getVariableExpression().getName());
        }
        super.visitDeclarationExpression(expression);
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (expression.isParameterSpecified()) {
            addParameters(expression.getParameters());
        }
        else {
            // A closure that names no parameter still has one, and a call to it is that parameter's
            // method rather than a tag.
            names.add(IMPLICIT_CLOSURE_PARAMETER);
        }
        super.visitClosureExpression(expression);
    }

    @Override
    public void visitForLoop(ForStatement forLoop) {
        // A classic for carries both, an enhanced for only the value, so both are asked for.
        addVariable(forLoop.getIndexVariable());
        addVariable(forLoop.getValueVariable());
        super.visitForLoop(forLoop);
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        // CodeVisitorSupport visits the body but not the parameter the exception is caught into.
        addVariable(statement.getVariable());
        super.visitCatchStatement(statement);
    }

    private void addVariable(Parameter parameter) {
        if (parameter != null) {
            names.add(parameter.getName());
        }
    }
}
