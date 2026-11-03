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
package org.grails.gsp.compiler.transform

import java.lang.reflect.Field

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ClosureListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.RangeExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
import org.codehaus.groovy.ast.expr.SpreadMapExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority
import org.springframework.util.ReflectionUtils

import org.apache.grails.common.compiler.GroovyTransformOrder

/**
 * Convert line number information to that based on the line number array passed
 * into the line number array in the {@link LineNumber} annotation.
 *
 * @author Andrew Eisenberg
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
@CompileStatic
class LineNumberTransform implements ASTTransformation, TransformWithPriority {

    // LOG statements commented out because they were causing
    // compilation problems when used outside of grails
    // static final Log LOG = LogFactory.getLog(LineNumberTransform)

    void visit(ASTNode[] nodes, SourceUnit source) {
        List<ClassNode> classes = source.getAST().getClasses()

        AnnotationNode annotation = null
        for (ClassNode clazz in classes) {
            annotation = findAnnotation(clazz)
            if (annotation != null) {
                break
            }
        }

        if (annotation == null) {
            return
        }

        int[] array = extractLineNumberArray(annotation)
        if (array != null) {
            LineNumberVisitor visitor = new LineNumberVisitor(array)
            for (ClassNode clazz in classes) {
                visitor.visitClass(clazz)
            }
        }

        String sourceName = extractSourceName(annotation)
        if (sourceName != null) {
            source.getAST().setDescription(sourceName)
            // source.name = sourceName
            Field field = ReflectionUtils.findField(SourceUnit, 'name')
            field.setAccessible(true)
            ReflectionUtils.setField(field, source, sourceName)
        }
    }

    String extractSourceName(AnnotationNode node) {
        ConstantExpression newName = (ConstantExpression) node.getMember('sourceName')
        return (String) newName.getValue()
    }

    AnnotationNode findAnnotation(ClassNode clazz) {
        if (clazz != null) {
            for (AnnotationNode node in clazz.getAnnotations()) {
                if (node.getClassNode().getName().equals(LineNumber.getName())) {
                    // LOG.debug "Transforming in ${clazz.name}"
                    return node
                }
            }
        }
        return null
    }

    int[] extractLineNumberArray(AnnotationNode node) {
        ListExpression lineNumberArray = (ListExpression) node.getMember('lines')
        // make assumption that this is a simple array of constants
        List<Integer> numbers = new ArrayList<>()
        for (Expression e in lineNumberArray.getExpressions()) {
            if (e instanceof ConstantExpression) {
                numbers.add((Integer) ((ConstantExpression) e).getValue())
            }
            else {
                numbers.add(-1)
            }
        }
        // LOG.debug "We have transformed: ${numbers}"
        if (numbers.isEmpty()) {
            return null
        }
        int[] array = new int[numbers.size()]
        for (int i = 0, count = numbers.size(); i < count; i++) {
            array[i] = numbers.get(i)
        }
        return array
    }

    @Override
    int priority() {
        return GroovyTransformOrder.GSP_LINE_ORDER
    }

    class LineNumberVisitor extends ClassCodeVisitorSupport {
        int[] lineNumbers

        LineNumberVisitor(int[] lineNumbers) {
            this.lineNumbers = lineNumbers
            /*
            if (LOG.isDebugEnabled()) {
                String numbers = "Line numbers: ";
                for (int number : lineNumbers) {
                    numbers += number + ", ";
                }
                LOG.debug numbers
            }
            */
        }

        @Override
        protected void visitStatement(Statement statement) {
            // LOG.debug "Transforming statement '${statement}':"

            if (statement.getLineNumber() >= 0 && statement.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${statement.lineNumber} to ${lineNumbers[statement.lineNumber]}"
                statement.setLineNumber(lineNumbers[statement.getLineNumber() - 1])
            }

            if (statement.getLastLineNumber() > 0 && statement.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${statement.lastLineNumber} to ${lineNumbers[statement.lastLineNumber]}"
                statement.setLastLineNumber(lineNumbers[statement.getLastLineNumber() - 1])
            }
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitMethodCallExpression(expression)
        }

        @Override
        void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitStaticMethodCallExpression(expression)
        }

        @Override
        void visitConstructorCallExpression(ConstructorCallExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitConstructorCallExpression(expression)
        }

        @Override
        void visitBinaryExpression(BinaryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitBinaryExpression(expression)
        }

        @Override
        void visitTernaryExpression(TernaryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitTernaryExpression(expression)
        }

        @Override
        void visitShortTernaryExpression(ElvisOperatorExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitShortTernaryExpression(expression)
        }

        @Override
        void visitPostfixExpression(PostfixExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitPostfixExpression(expression)
        }

        @Override
        void visitPrefixExpression(PrefixExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitPrefixExpression(expression)
        }

        @Override
        void visitBooleanExpression(BooleanExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitBooleanExpression(expression)
        }

        @Override
        void visitNotExpression(NotExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitNotExpression(expression)
        }

        @Override
        void visitClosureExpression(ClosureExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitClosureExpression(expression)
        }

        @Override
        void visitTupleExpression(TupleExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitTupleExpression(expression)
        }

        @Override
        void visitListExpression(ListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitListExpression(expression)
        }

        @Override
        void visitArrayExpression(ArrayExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitArrayExpression(expression)
        }

        @Override
        void visitMapExpression(MapExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitMapExpression(expression)
        }

        @Override
        void visitMapEntryExpression(MapEntryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitMapEntryExpression(expression)
        }

        @Override
        void visitRangeExpression(RangeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitRangeExpression(expression)
        }

        @Override
        void visitSpreadExpression(SpreadExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitSpreadExpression(expression)
        }

        @Override
        void visitSpreadMapExpression(SpreadMapExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitSpreadMapExpression(expression)
        }

        @Override
        void visitMethodPointerExpression(
                MethodPointerExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitMethodPointerExpression(expression)
        }

        @Override
        void visitUnaryMinusExpression(UnaryMinusExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitUnaryMinusExpression(expression)
        }

        @Override
        void visitUnaryPlusExpression(UnaryPlusExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitUnaryPlusExpression(expression)
        }

        @Override
        void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitBitwiseNegationExpression(expression)
        }

        @Override
        void visitCastExpression(CastExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitCastExpression(expression)
        }

        @Override
        void visitConstantExpression(ConstantExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitConstantExpression(expression)
        }

        @Override
        void visitClassExpression(ClassExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitClassExpression(expression)
        }

        @Override
        void visitDeclarationExpression(DeclarationExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitDeclarationExpression(expression)
        }

        @Override
        void visitPropertyExpression(PropertyExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitPropertyExpression(expression)
        }

        @Override
        void visitAttributeExpression(AttributeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitAttributeExpression(expression)
        }

        @Override
        void visitFieldExpression(FieldExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitFieldExpression(expression)
        }

        @Override
        void visitGStringExpression(GStringExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitGStringExpression(expression)
        }

        @Override
        void visitArgumentlistExpression(ArgumentListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitArgumentlistExpression(expression)
        }

        @Override
        void visitClosureListExpression(ClosureListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitClosureListExpression(expression)
        }

        @Override
        void visitBytecodeExpression(BytecodeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1])
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1])
            }
            super.visitBytecodeExpression(expression)
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null
        }
    }

}
