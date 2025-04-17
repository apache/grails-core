/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.compiler.transform;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Convert line number information to that based on the line number array passed
 * into the line number array in the {@link LineNumber} annotation.
 *
 * @author Andrew Eisenberg
 */
@GroovyASTTransformation(phase=CompilePhase.SEMANTIC_ANALYSIS)
public class LineNumberTransform implements ASTTransformation {

    // LOG statements commented out because they were causing
    // compilation problems when used outside of grails
//    static final Log LOG = LogFactory.getLog(LineNumberTransform)

    public void visit(ASTNode[] nodes, SourceUnit source) {
        List<ClassNode> classes = source.getAST().getClasses();

        AnnotationNode annotation = null;
        for (ClassNode clazz : classes) {
            annotation = findAnnotation(clazz);
            if (annotation != null) {
                break;
            }
        }

        if (annotation == null) {
            return;
        }

        int[] array = extractLineNumberArray(annotation);
        if (array != null) {
            LineNumberVisitor visitor = new LineNumberVisitor(array);
            for (ClassNode clazz : classes) {
                visitor.visitClass(clazz);
            }
        }

        String sourceName = extractSourceName(annotation);
        if (sourceName != null) {
            source.getAST().setDescription(sourceName);
            // source.name = sourceName
            Field field = ReflectionUtils.findField(SourceUnit.class, "name");
            field.setAccessible(true);
            ReflectionUtils.setField(field, source, sourceName);
        }
    }

    String extractSourceName(AnnotationNode node) {
        ConstantExpression newName = (ConstantExpression)node.getMember("sourceName");
        return (String)newName.getValue();
    }

    AnnotationNode findAnnotation(ClassNode clazz) {
        if (clazz != null) {
            for (AnnotationNode node : clazz.getAnnotations()) {
                if (node.getClassNode().getName().equals(LineNumber.class.getName())) {
                    // LOG.debug "Transforming in ${clazz.name}"
                    return node;
                }
            }
        }
        return null;
    }

    int[] extractLineNumberArray(AnnotationNode node) {
        ListExpression lineNumberArray = (ListExpression)node.getMember("lines");
        // make assumption that this is a simple array of constants
        List<Integer> numbers = new ArrayList<Integer>();
        for (Expression e : lineNumberArray.getExpressions()) {
            if (e instanceof ConstantExpression) {
                numbers.add((Integer)((ConstantExpression)e).getValue());
            }
            else {
                numbers.add(-1);
            }
        }
        // LOG.debug "We have transformed: ${numbers}"
        if (numbers.isEmpty()) {
            return null;
        }
        int[] array = new int[numbers.size()];
        for (int i = 0, count = numbers.size(); i < count; i++) {
            array[i] = numbers.get(i);
        }
        return array;
    }

    class LineNumberVisitor extends ClassCodeVisitorSupport {
        int[] lineNumbers;

        LineNumberVisitor(int[] lineNumbers) {
            this.lineNumbers = lineNumbers;
//            if (LOG.isDebugEnabled()) {
//                String numbers = "Line numbers: ";
//                for (int number : lineNumbers) {
//                    numbers += number + ", ";
//                }
//                LOG.debug numbers
//            }
        }

        @Override
        protected void visitStatement(Statement statement) {
            // LOG.debug "Transforming statement '${statement}':"

            if (statement.getLineNumber() >= 0 && statement.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${statement.lineNumber} to ${lineNumbers[statement.lineNumber]}"
                statement.setLineNumber(lineNumbers[statement.getLineNumber() - 1]);
            }

            if (statement.getLastLineNumber() > 0 && statement.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${statement.lastLineNumber} to ${lineNumbers[statement.lastLineNumber]}"
                statement.setLastLineNumber(lineNumbers[statement.getLastLineNumber() - 1]);
            }
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitMethodCallExpression(expression);
        }
        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
             // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitStaticMethodCallExpression(expression);
        }
        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitConstructorCallExpression(expression);
        }
        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitBinaryExpression(expression);
        }
        @Override
        public void visitTernaryExpression(TernaryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitTernaryExpression(expression);
        }
        @Override
        public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitShortTernaryExpression(expression);
        }
        @Override
        public void visitPostfixExpression(PostfixExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitPostfixExpression(expression);
        }
        @Override
        public void visitPrefixExpression(PrefixExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitPrefixExpression(expression);
        }
        @Override
        public void visitBooleanExpression(BooleanExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitBooleanExpression(expression);
        }
        @Override
        public void visitNotExpression(NotExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitNotExpression(expression);
        }
        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitClosureExpression(expression);
        }
        @Override
        public void visitTupleExpression(TupleExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitTupleExpression(expression);
        }
        @Override
        public void visitListExpression(ListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitListExpression(expression);
        }
        @Override
        public void visitArrayExpression(ArrayExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitArrayExpression(expression);
        }
        @Override
        public void visitMapExpression(MapExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitMapExpression(expression);
        }
        @Override
        public void visitMapEntryExpression(MapEntryExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitMapEntryExpression(expression);
        }
        @Override
        public void visitRangeExpression(RangeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitRangeExpression(expression);
        }
        @Override
        public void visitSpreadExpression(SpreadExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitSpreadExpression(expression);
        }
        @Override
        public void visitSpreadMapExpression(SpreadMapExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitSpreadMapExpression(expression);
        }
        @Override
        public void visitMethodPointerExpression(
                MethodPointerExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitMethodPointerExpression(expression);
        }
        @Override
        public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitUnaryMinusExpression(expression);
        }
        @Override
        public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitUnaryPlusExpression(expression);
        }
        @Override
        public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitBitwiseNegationExpression(expression);
        }
        @Override
        public void visitCastExpression(CastExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitCastExpression(expression);
        }
        @Override
        public void visitConstantExpression(ConstantExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitConstantExpression(expression);
        }
        @Override
        public void visitClassExpression(ClassExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitClassExpression(expression);
        }
        @Override
        public void visitDeclarationExpression(DeclarationExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitDeclarationExpression(expression);
        }
        @Override
        public void visitPropertyExpression(PropertyExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitPropertyExpression(expression);
        }
        @Override
        public void visitAttributeExpression(AttributeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitAttributeExpression(expression);
        }
        @Override
        public void visitFieldExpression(FieldExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitFieldExpression(expression);
        }
        @Override
        public void visitGStringExpression(GStringExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitGStringExpression(expression);
        }
        @Override
        public void visitArgumentlistExpression(ArgumentListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitArgumentlistExpression(expression);
        }
        @Override
        public void visitClosureListExpression(ClosureListExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitClosureListExpression(expression);
        }
        @Override
        public void visitBytecodeExpression(BytecodeExpression expression) {
            // LOG.debug "Transforming expression '${expression}':"

            if (expression.getLineNumber() >= 0 && expression.getLineNumber() < lineNumbers.length) {
                // LOG.debug "   start from ${expression.lineNumber} to ${lineNumbers[expression.lineNumber - 1]}"
                expression.setLineNumber(lineNumbers[expression.getLineNumber() - 1]);
            }

            if (expression.getLastLineNumber() > 0 && expression.getLastLineNumber() < lineNumbers.length) {
                // LOG.debug "   end from ${expression.lastLineNumber} to ${lineNumbers[expression.lastLineNumber - 1]}"
                expression.setLastLineNumber(lineNumbers[expression.getLastLineNumber() - 1]);
            }
            super.visitBytecodeExpression(expression);
        }
        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }
    }
}
