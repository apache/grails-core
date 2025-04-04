/* Copyright (C) 2013 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.query.transform;

import grails.gorm.DetachedCriteria;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.LocatedMessage;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.transform.trait.Traits;
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion;
import org.grails.datastore.mapping.reflect.AstUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * ClassCodeVisitorSupport that transforms where methods into detached criteria queries
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public class DetachedCriteriaTransformer extends ClassCodeVisitorSupport {

    private static final Class<?>[] EMPTY_JAVA_CLASS_ARRAY = {};
    private static final Object TRANSFORMED_MARKER = new Object();
    public static final String AND_OPERATOR = "&";
    public static final String OR_OPERATOR = "|";
    public static final ClassNode DETACHED_CRITERIA_CLASS_NODE = ClassHelper.make(DetachedCriteria.class);
    public static final Set<String> CANDIDATE_METHODS_WHERE_ONLY = newSet("where");
    public static final ClassNode FUNCTION_CALL_CRITERION = new ClassNode(FunctionCallingCriterion.class);
    public static final String EQUALS_OPERATOR = "==";
    public static final String IS_NULL_CRITERION = "isNull";
    public static final ConstantExpression WHERE_LAZY = new ConstantExpression("whereLazy");

    private SourceUnit sourceUnit;
    private static final Set<String> CANDIDATE_METHODS = newSet("where", "whereLazy", "whereAny", "findAll", "find");

    private static final Set<String> SUPPORTED_FUNCTIONS = newSet(
            "lower", "upper", "trim", "length", "second",
            "hour", "minute", "day", "month", "year");

    private static final Map<String, String> OPERATOR_TO_CRITERIA_METHOD_MAP = (Map<String, String>) newMap(
            "==", "eq",
            "!=", "ne",
            ">", "gt",
            "<", "lt",
            ">=", "ge",
            "<=", "le",
            "==~", "like",
            "=~", "ilike",
            "in", "inList");


    private static final Map<String, String> METHOD_TO_SUBQUERY_MAP = (Map<String, String>) newMap(
            "eq", "eqAll",
            "gt", "gtAll",
            "lt", "ltAll",
            "ge", "geAll",
            "le", "leAll");

    private static final Map<String, ClassNode> OPERATOR_TO_CRITERION_METHOD_MAP = (Map<String, ClassNode>) newMap(
            "==", new ClassNode(Query.Equals.class),
            "!=", new ClassNode(Query.NotEquals.class),
            ">", new ClassNode(Query.GreaterThan.class),
            "<", new ClassNode(Query.LessThan.class),
            ">=", new ClassNode(Query.GreaterThanEquals.class),
            "<=", new ClassNode(Query.LessThanEquals.class),
            "==~", new ClassNode(Query.Like.class),
            "=~", new ClassNode(Query.ILike.class),
            "in", new ClassNode(Query.In.class));

    private static final Map<String, String> PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP = (Map<String, String>) newMap(
            "==", "eqProperty",
            "!=", "neProperty",
            ">", "gtProperty",
            "<", "ltProperty",
            ">=", "geProperty",
            "<=", "leProperty");

    private static final Map<String, String> SIZE_OPERATOR_TO_CRITERIA_METHOD_MAP = (Map<String, String>) newMap(
            "==", "sizeEq",
            "!=", "sizeNe",
            ">", "sizeGt",
            "<", "sizeLt",
            ">=", "sizeGe",
            "<=", "sizeLe");

    private static final Map<String, String> AGGREGATE_FUNCTIONS = (Map<String, String>) newMap(
            "avg", "avg",
            "max", "max",
            "min", "min",
            "sum", "sum",
            "property", "property",
            "count", "countDistinct");

    protected Map<String, ClassNode> detachedCriteriaVariables = new HashMap<String, ClassNode>();
    protected Map<String, Object> aliases = new HashMap<String, Object>();
    protected Map<String, ClassNode> staticDetachedCriteriaVariables = new HashMap<String, ClassNode>();
    protected Set<Expression> aliasExpressions = new HashSet<Expression>();
    protected ClassNode currentClassNode;

    public DetachedCriteriaTransformer(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    @Override
    public void visitClass(ClassNode node) {
        try {
            this.currentClassNode = node;
            super.visitClass(node);
        } catch (Exception e) {
            logTransformationError(node, e);
        } finally {
            currentClassNode = null;
            detachedCriteriaVariables.clear();
        }
    }

    @Override
    public void visitMethod(MethodNode node) {
        try {
            super.visitMethod(node);
        } finally {
            detachedCriteriaVariables.clear();
        }
    }

    @Override
    public void visitField(FieldNode node) {
        ClassNode classNode = node.getOwner();
        if (node.isStatic() && AstUtils.isDomainClass(classNode)) {
            Expression initialExpression = node.getInitialExpression();
            if (initialExpression instanceof MethodCallExpression) {
                MethodCallExpression mce = (MethodCallExpression) initialExpression;

                if (isCandidateWhereMethod(mce.getMethod(), mce.getArguments())) {
                    ArgumentListExpression args = (ArgumentListExpression) mce.getArguments();
                    Expression target = mce.getObjectExpression();

                    List<Expression> argsExpressions = args.getExpressions();
                    int totalExpressions = argsExpressions.size();
                    if (totalExpressions > 0) {
                        Expression expression = argsExpressions.get(totalExpressions - 1);
                        if (expression instanceof ClosureExpression) {
                            ClosureExpression closureExpression = (ClosureExpression) expression;
                            transformClosureExpression(classNode, closureExpression);
                            if (target instanceof VariableExpression) {
                                VariableExpression ve = (VariableExpression) target;
                                ClassNode type = ve.getType();
                                if (type.equals(DETACHED_CRITERIA_CLASS_NODE)) {
                                    // don't turn into build method if the target is a DetachedCriteria instance
                                    return;
                                }
                            }

                            String buildMethod = mce.getMethodAsString().equals("whereLazy") ? "buildLazy" : "build";
                            ClassNode detachedCriteriaClassNode = getParameterizedDetachedCriteriaClassNode(classNode);

                            MethodCallExpression newInitialExpression = new MethodCallExpression(new ConstructorCallExpression(detachedCriteriaClassNode, new ArgumentListExpression(new ClassExpression(classNode))), buildMethod, new ArgumentListExpression(closureExpression));
                            node.setInitialValueExpression(newInitialExpression);
                            node.setType(detachedCriteriaClassNode);
                            staticDetachedCriteriaVariables.put(node.getName(), classNode);
                        }
                    }
                }
            }
        } else {
            try {
                Expression initialExpression = node.getInitialExpression();
                ClosureExpression newClosureExpression = handleDetachedCriteriaCast(initialExpression);

                if (newClosureExpression != null) {
                    node.setInitialValueExpression(newClosureExpression);
                }
            } catch (Exception e) {
                logTransformationError(node, e);
            }
        }

        super.visitField(node);
    }

    protected ClassNode getParameterizedDetachedCriteriaClassNode(ClassNode classNode) {
        ClassNode detachedCriteriaClassNode = DETACHED_CRITERIA_CLASS_NODE.getPlainNodeReference();
        if (classNode != null) {
            detachedCriteriaClassNode.setGenericsTypes(new GenericsType[]{new GenericsType(AstUtils.nonGeneric(classNode))});
        }
        return detachedCriteriaClassNode;
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        Expression initializationExpression = expression.getRightExpression();
        if (initializationExpression instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) initializationExpression;
            Expression objectExpression = call.getObjectExpression();
            Expression method = call.getMethod();
            Expression arguments = call.getArguments();
            if (isCandidateMethod(method.getText(), arguments, CANDIDATE_METHODS_WHERE_ONLY)) {
                ClassNode classNode = new ClassNode(DetachedCriteria.class);
                ClassNode targetType = objectExpression.getType();
                if (AstUtils.isDomainClass(targetType)) {
                    classNode.setGenericsTypes(new GenericsType[]{new GenericsType(targetType)});

                    VariableExpression variableExpression = expression.getVariableExpression();
                    if (variableExpression.isClosureSharedVariable()) {
                        Variable accessedVariable = variableExpression.getAccessedVariable();
                        if (accessedVariable instanceof VariableExpression) {
                            ((VariableExpression) accessedVariable).setType(classNode);
                        }
                    } else {
                        variableExpression.setType(classNode);
                    }
                    String variableName = expression.getVariableExpression().getName();
                    detachedCriteriaVariables.put(variableName, targetType);
                }
            }
        } else if (initializationExpression instanceof ConstructorCallExpression) {
            String variableName = expression.getVariableExpression().getName();
            ConstructorCallExpression cce = (ConstructorCallExpression) initializationExpression;

            ClassNode type = cce.getType();
            if (DETACHED_CRITERIA_CLASS_NODE.getName().equals(type.getName())) {
                Expression arguments = cce.getArguments();
                if (arguments instanceof ArgumentListExpression) {
                    ArgumentListExpression ale = (ArgumentListExpression) arguments;
                    if (ale.getExpressions().size() == 1) {
                        Expression exp = ale.getExpression(0);
                        if (exp instanceof ClassExpression) {
                            ClassExpression clse = (ClassExpression) exp;
                            detachedCriteriaVariables.put(variableName, clse.getType());
                        }
                    }
                }
            }
        } else {
            try {
                ClosureExpression newClosureExpression = handleDetachedCriteriaCast(initializationExpression);
                if (newClosureExpression != null) {
                    expression.setRightExpression(newClosureExpression);
                }
            } catch (Exception e) {
                logTransformationError(initializationExpression, e);
            }
        }
        super.visitDeclarationExpression(expression);
    }

    private void logTransformationError(ASTNode astNode, Exception e) {
        StringBuilder message = new StringBuilder("Fatal error occurred applying query transformations [ " + e.getMessage() + "] to source [" + sourceUnit.getName() + "]. Please report an issue.");
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        message.append(System.getProperty("line.separator"));
        message.append(sw.toString());
        sourceUnit.getErrorCollector().addError(new LocatedMessage(message.toString(), Token.newString(astNode.getText(), astNode.getLineNumber(), astNode.getColumnNumber()), sourceUnit));
    }

    private ClosureExpression handleDetachedCriteriaCast(Expression initializationExpression) {
        ClosureExpression newClosureExpression = null;
        if ((initializationExpression instanceof CastExpression) && ((CastExpression) initializationExpression).getExpression() instanceof ClosureExpression) {
            CastExpression ce = (CastExpression) initializationExpression;
            Expression castTarget = ce.getExpression();
            ClosureExpression cle = (ClosureExpression) castTarget;
            ClassNode targetCastType = ce.getType();
            if (targetCastType.getName().equals(DetachedCriteria.class.getName())) {
                GenericsType[] genericsTypes = targetCastType.getGenericsTypes();
                if (genericsTypes.length > 0) {
                    ClassNode genericType = genericsTypes[0].getType();
                    transformClosureExpression(genericType, cle);
                    newClosureExpression = cle;
                }
            }
        }
        return newClosureExpression;
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        Expression objectExpression = call.getObjectExpression();
        Expression method = call.getMethod();
        Expression arguments = call.getArguments();
        try {
            if (isCandidateMethodCallForTransform(objectExpression, method, arguments)) {
                ClassExpression ce = getTargetClassExpresssion(objectExpression);
                if (ce != null) {
                    ClassNode classNode = ce.getType();
                    this.currentClassNode = classNode;
                    visitMethodCall(classNode, arguments);
                }
            } else if (objectExpression instanceof VariableExpression) {
                VariableExpression var = (VariableExpression) objectExpression;
                String varName = var.getName();

                ClassNode varType = detachedCriteriaVariables.get(varName);
                if (varType != null && isCandidateWhereMethod(method, arguments)) {
                    this.currentClassNode = varType;
                    visitMethodCall(varType, arguments);
                } else if (var.isThisExpression() &&
                           currentClassNode != null &&
                           isCandidateWhereMethod(method.getText(), arguments)) {
                    if (AstUtils.isDomainClass(currentClassNode)) {
                        visitMethodCall(this.currentClassNode, arguments);
                        call.setMethod(WHERE_LAZY);
                    }
                }
                else {
                    varType = var.getType();
                    if(varType != null && varType.getName().equals(DETACHED_CRITERIA_CLASS_NODE.getName()) && isCandidateWhereMethod(method, arguments)) {
                        GenericsType[] genericsTypes = varType.getGenericsTypes();
                        if(genericsTypes != null && genericsTypes.length == 1) {
                            this.currentClassNode = genericsTypes[0].getType();
                            visitMethodCallOnDetachedCriteria(this.currentClassNode, (ArgumentListExpression) arguments);
                        }
                    }
                }
            } else if (objectExpression instanceof PropertyExpression && !(((PropertyExpression) objectExpression).getProperty() instanceof GStringExpression)) {
                PropertyExpression pe = (PropertyExpression) objectExpression;
                String propName = pe.getPropertyAsString();
                ClassNode classNode = pe.getObjectExpression().getType();
                if (AstUtils.isDomainClass(classNode)) {
                    ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(classNode, propName);
                    if (propertyType != null && DETACHED_CRITERIA_CLASS_NODE.equals(propertyType)) {
                        visitMethodCall(classNode, arguments);
                    }
                }
            } else if(objectExpression instanceof MethodCallExpression) {
                MethodCallExpression mce = (MethodCallExpression)objectExpression;
                MethodNode methodTarget = mce.getMethodTarget();
                String methodName = mce.getMethodAsString();
                Expression targetObject = mce.getObjectExpression();
                ClassNode targetType = null;
                if(targetObject instanceof ClassExpression) {
                    targetType = ((ClassExpression)targetObject).getType();
                }
                else if(targetObject instanceof VariableExpression) {
                    targetType = ((VariableExpression)targetObject).getType();
                }

                if(targetType != null && AstUtils.isDomainClass(targetType) && isCandidateWhereMethod(method, arguments)) {
                    List<MethodNode> methods = targetType.getMethods(methodName);
                    for (MethodNode methodNode : methods) {
                        if(methodNode.getReturnType().equals(DETACHED_CRITERIA_CLASS_NODE)) {
                            visitMethodCall(targetType, arguments);
                        }
                    }

                }
            }
        } catch (Exception e) {
            logTransformationError(call, e);
        }
        super.visitMethodCallExpression(call);
    }

    private ClassExpression getTargetClassExpresssion(Expression objectExpression) {
        if (objectExpression instanceof ClassExpression) {
            return (ClassExpression) objectExpression;
        }
        if (objectExpression instanceof MethodCallExpression) {
            MethodCallExpression mce = (MethodCallExpression) objectExpression;

            Expression oe = mce.getObjectExpression();
            if (oe instanceof ClassExpression) {
                return (ClassExpression) oe;
            }
        } else if (objectExpression instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression) objectExpression;
            if (ve.isThisExpression() &&
                AstUtils.isDomainClass(this.currentClassNode)) {
                return new ClassExpression(this.currentClassNode);
            }
        }
        return null;
    }

    private boolean isCandidateMethodCallForTransform(Expression objectExpression, Expression method, Expression arguments) {
        return ((objectExpression instanceof ClassExpression) || isObjectExpressionWhereCall(objectExpression)) &&
                isCandidateWhereMethod(method, arguments);
    }

    private boolean isObjectExpressionWhereCall(Expression objectExpression) {
        if (objectExpression instanceof MethodCallExpression) {
            MethodCallExpression mce = (MethodCallExpression) objectExpression;
            return isCandidateWhereMethod(mce.getMethodAsString(), mce.getArguments());
        }
/*        else if(objectExpression instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression) objectExpression;
            if(ve.getName().equals(THIS_EXPRESSION.getName()) && isDomainClass(this.currentClassNode)) {
                return true;
            }
        }*/
        return false;
    }

    private void visitMethodCall(ClassNode classNode, Expression arguments) {
        if (AstUtils.isDomainClass(classNode) && (arguments instanceof ArgumentListExpression)) {
            visitMethodCallOnDetachedCriteria(classNode, (ArgumentListExpression) arguments);
        }
    }

    private void visitMethodCallOnDetachedCriteria(ClassNode classNode, ArgumentListExpression arguments) {
        if (arguments.getExpressions().size() > 0) {
            Expression expression = arguments.getExpression(arguments.getExpressions().size() - 1);
            if (expression instanceof ClosureExpression) {
                ClosureExpression closureExpression = (ClosureExpression) expression;
                transformClosureExpression(classNode, closureExpression);
            }
        }
    }

    private boolean isCandidateWhereMethod(Expression method, Expression arguments) {
        String methodName = method.getText();
        return ((method instanceof ConstantExpression) && isCandidateWhereMethod(methodName, arguments));
    }

    private boolean isCandidateWhereMethod(String methodName, Expression arguments) {
        return isCandidateMethod(methodName, arguments, CANDIDATE_METHODS);
    }

    private boolean isCandidateMethod(String methodName, Expression arguments, Set<String> candidateMethods) {

        if (candidateMethods.contains(methodName)) {
            if (arguments instanceof ArgumentListExpression) {
                ArgumentListExpression ale = (ArgumentListExpression) arguments;
                List<Expression> expressions = ale.getExpressions();
                if (expressions.size() > 0) {
                    Expression expression = expressions.get(expressions.size() - 1);
                    if (expression instanceof ClosureExpression) {
                        return true;
                    } else if (expression instanceof VariableExpression) {
                        VariableExpression ve = (VariableExpression) expression;
                        if (detachedCriteriaVariables.containsKey(ve.getName()) || isDetachedCriteriaVariable(ve)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isDetachedCriteriaVariable(VariableExpression ve) {
        String variableType = ve.getType().getName();
        return DETACHED_CRITERIA_CLASS_NODE.getName().equals(variableType);
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        String method = call.getMethod();
        Expression arguments = call.getArguments();
        if (isCandidateWhereMethod(method, arguments)) {
            ClassNode classNode = call.getOwnerType();
            visitMethodCall(classNode, arguments);
        }
        super.visitStaticMethodCallExpression(call);
    }

    public void transformClosureExpression(ClassNode classNode, ClosureExpression closureExpression) {
        if (closureExpression.getNodeMetaData(TRANSFORMED_MARKER) !=  null) return;
        ClassNode previousClassNode = this.currentClassNode;
        try {
            this.currentClassNode = classNode;
            List<String> propertyNames = AstPropertyResolveUtils.getPropertyNames(classNode);
            Statement code = closureExpression.getCode();
            BlockStatement newCode = new BlockStatement();
            boolean addAll = false;

            if (code instanceof BlockStatement) {
                BlockStatement bs = (BlockStatement) code;

                addBlockStatementToNewQuery(bs, newCode, addAll, propertyNames, closureExpression.getVariableScope());
                newCode.setVariableScope(bs.getVariableScope());
            }

            if (!newCode.getStatements().isEmpty()) {
                closureExpression.putNodeMetaData(TRANSFORMED_MARKER, Boolean.TRUE);
                closureExpression.setCode(newCode);
            }
        } finally {
            this.currentClassNode = previousClassNode;
        }
    }


    private void addBlockStatementToNewQuery(BlockStatement blockStatement, BlockStatement newCode, boolean addAll, List<String> propertyNames, VariableScope variableScope) {
        List<Statement> statements = blockStatement.getStatements();
        for (Statement statement : statements) {
            addStatementToNewQuery(statement, newCode, addAll, propertyNames, variableScope);
        }
    }

    private void addStatementToNewQuery(Statement statement, BlockStatement newCode, boolean addAll, List<String> propertyNames, VariableScope variableScope) {
        if (statement instanceof BlockStatement) {
            addBlockStatementToNewQuery((BlockStatement) statement, newCode, addAll, propertyNames, variableScope);
        } else if (statement instanceof ExpressionStatement) {
            ExpressionStatement es = (ExpressionStatement) statement;

            Expression expression = es.getExpression();
            if (expression instanceof DeclarationExpression) {
                DeclarationExpression de = (DeclarationExpression)expression;
                Expression leftExpression = de.getLeftExpression();
                Expression rightExpression = de.getRightExpression();
                if((leftExpression instanceof VariableExpression) && (rightExpression instanceof ClassExpression)) {
                    ClassExpression classExpression = (ClassExpression) rightExpression;
                    if(currentClassNode.equals(classExpression.getType())) {
                        ArgumentListExpression arguments = new ArgumentListExpression();
                        String aliasName = leftExpression.getText();
                        aliases.put(aliasName, currentClassNode);
                        arguments.addExpression(new ConstantExpression(aliasName));
                        MethodCallExpression setAliasMethodCall = new MethodCallExpression(new VariableExpression("this"), "setAlias", arguments);
                        newCode.addStatement(new ExpressionStatement(setAliasMethodCall));
                    }
                    newCode.addStatement(es);
                }
                else if((leftExpression instanceof VariableExpression) && (rightExpression instanceof VariableExpression)) {
                    String referencedProperty = rightExpression.getText();
                    if(propertyNames.contains(referencedProperty)) {
                        ArgumentListExpression arguments = new ArgumentListExpression();
                        arguments.addExpression(new ConstantExpression(referencedProperty));
                        String aliasName = leftExpression.getText();
                        aliases.put(aliasName, referencedProperty);
                        arguments.addExpression(new ConstantExpression(aliasName));
                        MethodCallExpression createAliasMethodCall = new MethodCallExpression(new VariableExpression("this"), "createAlias", arguments);
                        newCode.addStatement(new ExpressionStatement(createAliasMethodCall));
                    }
                    newCode.addStatement(es);
                }
                else {
                    newCode.addStatement(es);
                }
            } else if (expression instanceof BinaryExpression) {
                BinaryExpression be = (BinaryExpression) expression;
                addBinaryExpressionToNewBody(propertyNames, newCode, be, addAll, variableScope);
            } else if (expression instanceof NotExpression) {
                NotExpression not = (NotExpression) expression;

                handleNegation(propertyNames, newCode, not, variableScope);
            } else if (expression instanceof MethodCallExpression) {
                MethodCallExpression methodCall = (MethodCallExpression) expression;

                handleAssociationMethodCallExpression(newCode, methodCall, propertyNames, variableScope);
            }
        } else {
            if (statement instanceof IfStatement) {
                IfStatement ifs = (IfStatement) statement;
                Statement ifb = ifs.getIfBlock();
                BlockStatement newIfBlock = new BlockStatement();
                addStatementToNewQuery(ifb, newIfBlock, addAll, propertyNames, variableScope);
                ifs.setIfBlock(flattenStatementIfNecessary(newIfBlock));

                Statement elseBlock = ifs.getElseBlock();
                if (elseBlock != null) {
                    BlockStatement newElseBlock = new BlockStatement();
                    addStatementToNewQuery(elseBlock, newElseBlock, addAll, propertyNames, variableScope);
                    ifs.setElseBlock(flattenStatementIfNecessary(newElseBlock));
                }
                newCode.addStatement(ifs);
            } else if (statement instanceof SwitchStatement) {
                SwitchStatement sw = (SwitchStatement) statement;

                List<CaseStatement> caseStatements = sw.getCaseStatements();
                for (CaseStatement caseStatement : caseStatements) {
                    Statement existingCode = caseStatement.getCode();
                    BlockStatement newCaseCode = new BlockStatement();
                    addStatementToNewQuery(existingCode, newCaseCode, addAll, propertyNames, variableScope);
                    caseStatement.setCode(flattenStatementIfNecessary(newCaseCode));
                }

                newCode.addStatement(sw);
            } else if (statement instanceof ForStatement) {
                ForStatement fs = (ForStatement) statement;
                Statement loopBlock = fs.getLoopBlock();
                BlockStatement newLoopBlock = new BlockStatement();
                addStatementToNewQuery(loopBlock, newLoopBlock, addAll, propertyNames, variableScope);
                fs.setLoopBlock(flattenStatementIfNecessary(newLoopBlock));
                newCode.addStatement(fs);
            } else if (statement instanceof WhileStatement) {
                WhileStatement ws = (WhileStatement) statement;
                Statement loopBlock = ws.getLoopBlock();
                BlockStatement newLoopBlock = new BlockStatement();
                addStatementToNewQuery(loopBlock, newLoopBlock, addAll, propertyNames, variableScope);
                ws.setLoopBlock(flattenStatementIfNecessary(newLoopBlock));
                newCode.addStatement(ws);
            } else if (statement instanceof TryCatchStatement) {
                TryCatchStatement tcs = (TryCatchStatement) statement;
                Statement tryStatement = tcs.getTryStatement();

                BlockStatement newTryStatement = new BlockStatement();
                addStatementToNewQuery(tryStatement, newTryStatement, addAll, propertyNames, variableScope);
                tcs.setTryStatement(flattenStatementIfNecessary(newTryStatement));

                List<CatchStatement> catchStatements = tcs.getCatchStatements();

                for (CatchStatement catchStatement : catchStatements) {
                    BlockStatement newCatchStatement = new BlockStatement();
                    Statement code = catchStatement.getCode();
                    addStatementToNewQuery(code, newCatchStatement, addAll, propertyNames, variableScope);
                    catchStatement.setCode(flattenStatementIfNecessary(newCatchStatement));
                }

                Statement finallyStatement = tcs.getFinallyStatement();
                if (finallyStatement != null) {
                    BlockStatement newFinallyStatement = new BlockStatement();
                    addStatementToNewQuery(finallyStatement, newFinallyStatement, addAll, propertyNames, variableScope);
                    tcs.setFinallyStatement(flattenStatementIfNecessary(newFinallyStatement));
                }
                newCode.addStatement(tcs);
            } else if (statement instanceof ReturnStatement) {
                ReturnStatement rs = (ReturnStatement) statement;
                addStatementToNewQuery(new ExpressionStatement(rs.getExpression()), newCode, addAll, propertyNames,variableScope);

            }
            else {
                newCode.addStatement(statement);
            }
        }
    }

    private Statement flattenStatementIfNecessary(BlockStatement blockStatement) {
        if (blockStatement.getStatements().size() == 1) {
            return blockStatement.getStatements().get(0);
        }
        return blockStatement;
    }

    private void handleAssociationMethodCallExpression(BlockStatement newCode, MethodCallExpression methodCall, List<String> propertyNames, VariableScope variableScope) {
        Expression method = methodCall.getMethod();
        String methodName = method.getText();
        ArgumentListExpression arguments = methodCall.getArguments() instanceof ArgumentListExpression ? (ArgumentListExpression) methodCall.getArguments() : null;

        if (methodName.equals("call") && hasClosureArgument(arguments)) {
            methodName = methodCall.getObjectExpression().getText();
        }
        if (isAssociationMethodCall(propertyNames, methodName, arguments)) {
            ClosureAndArguments closureAndArguments = new ClosureAndArguments(variableScope);
            ClosureExpression associationQuery = (ClosureExpression) arguments.getExpression(0);
            BlockStatement currentBody = closureAndArguments.getCurrentBody();
            ArgumentListExpression argList = closureAndArguments.getArguments();
            newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("delegate"), methodName, argList)));
            Statement associationCode = associationQuery.getCode();
            if (associationCode instanceof BlockStatement) {

                List<String> associationPropertyNames = null;
                ClassNode type = getPropertyType(methodName);
                if (!AstUtils.isDomainClass(type)) {
                    ClassNode associationTypeFromGenerics = getAssociationTypeFromGenerics(type);
                    if (associationTypeFromGenerics != null) {
                        type = associationTypeFromGenerics;
                        associationPropertyNames = getPropertyNamesForAssociation(associationTypeFromGenerics);
                    }
                }
                if (associationPropertyNames == null) {
                    associationPropertyNames = AstPropertyResolveUtils.getPropertyNames(type);
                }

                ClassNode existing = currentClassNode;
                try {
                    if (!associationPropertyNames.isEmpty() && !AstUtils.isDomainClass(type)) {

                        type = getAssociationTypeFromGenerics(type);
                        if (type != null) {
                            associationPropertyNames = AstPropertyResolveUtils.getPropertyNames(type);
                        }
                    }
                    if (type != null) {
                        currentClassNode = type;
                        addBlockStatementToNewQuery((BlockStatement) associationCode, currentBody, associationPropertyNames.isEmpty(), associationPropertyNames, variableScope);
                    }

                } finally {
                    currentClassNode = existing;
                }
            }
        }
        else {
            newCode.addStatement(new ExpressionStatement(methodCall));
        }
//        else {
//            sourceUnit.getErrorCollector().addError(new LocatedMessage("Method call ["+methodName+"] is invalid. Only binary expressions are allowed in queries.", Token.newString(methodName,methodCall.getLineNumber(), methodCall.getColumnNumber()), sourceUnit));
//        }
    }

    private List<String> getPropertyNamesForAssociation(ClassNode type) {
        List<String> associationPropertyNames = Collections.emptyList();
        if (type != null) {
            if (AstUtils.isDomainClass(type)) {
                associationPropertyNames = AstPropertyResolveUtils.getPropertyNames(type);
            } else {
                ClassNode associationType = getAssociationTypeFromGenerics(type);
                if (associationType != null) {
                    associationPropertyNames = AstPropertyResolveUtils.getPropertyNames(associationType);
                }
            }
        }
        return associationPropertyNames;
    }

    private ClassNode getAssociationTypeFromGenerics(ClassNode type) {
        GenericsType[] genericsTypes = type.getGenericsTypes();
        ClassNode associationType = null;
        if (genericsTypes != null && genericsTypes.length == 1) {
            GenericsType genericType = genericsTypes[0];
            associationType = genericType.getType();
        }
        return associationType;
    }

    private ClassNode getPropertyType(String prop) {
        ClassNode classNode = this.currentClassNode;
        return AstPropertyResolveUtils.getPropertyType(classNode, prop);
    }


    private boolean isAssociationMethodCall(List<String> propertyNames, String methodName, ArgumentListExpression arguments) {
        return propertyNames.contains(methodName) && hasClosureArgument(arguments);
    }

    private boolean hasClosureArgument(ArgumentListExpression arguments) {
        return arguments != null && arguments.getExpressions().size() == 1 && (arguments.getExpression(0) instanceof ClosureExpression);
    }

    private void handleNegation(List<String> propertyNames, BlockStatement newCode, NotExpression not, VariableScope variableScope) {
        Expression subExpression = not.getExpression();
        if (subExpression instanceof BinaryExpression) {
            ArgumentListExpression arguments = new ArgumentListExpression();
            BlockStatement currentBody = new BlockStatement();
            ClosureExpression newClosureExpression = new ClosureExpression(new Parameter[0], currentBody);
            newClosureExpression.setVariableScope(new VariableScope());
            arguments.addExpression(newClosureExpression);
            addBinaryExpressionToNewBody(propertyNames, currentBody, (BinaryExpression) subExpression, false, variableScope);

            newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), "not", arguments)));
        } else {
            sourceUnit.getErrorCollector().addError(new LocatedMessage("You can only negate a binary expressions in queries.", Token.newString(not.getText(), not.getLineNumber(), not.getColumnNumber()), sourceUnit));
        }
    }

    private void addBinaryExpressionToNewBody(List<String> propertyNames, BlockStatement newCode, BinaryExpression be, boolean addAll, VariableScope variableScope) {
        Token operation = be.getOperation();

        String operator = operation.getRootText();

        Expression leftExpression = be.getLeftExpression();
        Expression rightExpression = be.getRightExpression();
        if (leftExpression instanceof VariableExpression) {
            VariableExpression leftVariable = (VariableExpression) leftExpression;
            String propertyName = leftVariable.getText();
            if (propertyNames.contains(propertyName) || addAll) {
                if (OPERATOR_TO_CRITERIA_METHOD_MAP.containsKey(operator)) {
                    addCriteriaCallMethodExpression(newCode, operator, leftExpression, rightExpression, propertyName, propertyNames, addAll, variableScope);
                } else {
                    sourceUnit.getErrorCollector().addError(new LocatedMessage("Unsupported operator [" + operator + "] used in query", operation, sourceUnit));
                }
            } else {
                if (sourceUnit != null) {
                    sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot query on property \"" + propertyName + "\" - no such property on class " + currentClassNode.getName() + " exists.", Token.newString(propertyName, leftExpression.getLineNumber(), leftExpression.getColumnNumber()), sourceUnit));
                }
            }
        } else {

            if (leftExpression instanceof MethodCallExpression) {
                MethodCallExpression mce = (MethodCallExpression) leftExpression;
                String methodName = mce.getMethodAsString();
                Expression objectExpression = mce.getObjectExpression();
                if ("size".equals(methodName) && (objectExpression instanceof VariableExpression)) {
                    String propertyName = objectExpression.getText();
                    if (propertyNames.contains(propertyName)) {
                        String sizeOperator = SIZE_OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
                        if (sizeOperator != null) {
                            addCriteriaCall(newCode, operator, mce, rightExpression, propertyName, propertyNames, addAll, sizeOperator, variableScope);
                        } else {
                            sourceUnit.getErrorCollector().addError(new LocatedMessage("Unsupported operator [" + operator + "] used in size() query", operation, sourceUnit));
                        }
                    } else {
                        sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot query size of property \"" + propertyName + "\" - no such property on class " + currentClassNode.getName() + " exists.", Token.newString(propertyName, leftExpression.getLineNumber(), leftExpression.getColumnNumber()), sourceUnit));
                    }

                    return;
                } else {
                    boolean isFunctionCall = isFunctionCall(mce, methodName, objectExpression);
                    if (isFunctionCall) {
                        String functionName = methodName;
                        ArgumentListExpression existingArgs = (ArgumentListExpression) mce.getArguments();
                        Expression propertyNameExpression = existingArgs.getExpression(0);
                        if (propertyNameExpression instanceof PropertyExpression) {
                            handleAssociationQueryViaPropertyExpression((PropertyExpression) propertyNameExpression, rightExpression, operator, newCode, propertyNames, functionName, variableScope);
                        } else {
                            handleFunctionCall(newCode, operator, rightExpression, functionName, propertyNameExpression);
                        }
                        return;
                    }
                }
            }

            String methodNameToCall = null;
            if (operator.contains(AND_OPERATOR)) {
                methodNameToCall = "and";
            } else if (operator.contains(OR_OPERATOR)) {
                methodNameToCall = "or";
            }
            ArgumentListExpression arguments = new ArgumentListExpression();
            BlockStatement currentBody = new BlockStatement();
            handleBinaryExpressionSide(leftExpression, rightExpression, operator, currentBody, addAll, propertyNames, variableScope);
            handleBinaryExpressionSide(rightExpression, rightExpression, operator, currentBody, addAll, propertyNames, variableScope);

            ClosureExpression newClosureExpression = new ClosureExpression(new Parameter[0], currentBody);
            newClosureExpression.setVariableScope(variableScope);
            arguments.addExpression(newClosureExpression);
            if (methodNameToCall != null) {
                newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), methodNameToCall, arguments)));
            } else {
                List<Statement> statements = currentBody.getStatements();
                for (Statement statement : statements) {
                    newCode.addStatement(statement);
                }
            }
        }
    }

    private boolean isFunctionCall(MethodCallExpression mce) {
        boolean isThis = "this".equals(mce.getObjectExpression().getText());
        Expression arguments = mce.getArguments();
        boolean hasOneArg = arguments instanceof ArgumentListExpression ? ((ArgumentListExpression) arguments).getExpressions().size() == 1 : false;
        return isThis && hasOneArg && SUPPORTED_FUNCTIONS.contains(mce.getMethodAsString());
    }

    private boolean isFunctionCall(MethodCallExpression mce, String methodName, Expression objectExpression) {
        boolean isThis = "this".equals(objectExpression.getText());
        Expression arguments = mce.getArguments();
        boolean hasOneArg = arguments instanceof ArgumentListExpression ? ((ArgumentListExpression) arguments).getExpressions().size() == 1 : false;
        return isThis && hasOneArg && SUPPORTED_FUNCTIONS.contains(methodName);
    }

    private void handleFunctionCall(BlockStatement newCode, String operator, Expression rightExpression, String functionName, Expression propertyNameExpression) {
        ArgumentListExpression newArgs = new ArgumentListExpression();
        ArgumentListExpression constructorArgs = new ArgumentListExpression();
        constructorArgs.addExpression(new ConstantExpression(functionName));
        ClassNode criterionClassNode = OPERATOR_TO_CRITERION_METHOD_MAP.get(operator);
        if (criterionClassNode != null) {
            ArgumentListExpression criterionConstructorArguments = new ArgumentListExpression();
            if (!(propertyNameExpression instanceof ConstantExpression)) {
                propertyNameExpression = new ConstantExpression(propertyNameExpression.getText());
            }
            criterionConstructorArguments.addExpression(propertyNameExpression);
            criterionConstructorArguments.addExpression(rightExpression);
            constructorArgs.addExpression(new ConstructorCallExpression(criterionClassNode, criterionConstructorArguments));
            ConstructorCallExpression constructorCallExpression = new ConstructorCallExpression(FUNCTION_CALL_CRITERION, constructorArgs);
            newArgs.addExpression(constructorCallExpression);
            newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), "add", newArgs)));
        } else {
            sourceUnit.getErrorCollector().addError(new LocatedMessage("Unsupported operator [" + operator + "] used with function call [" + functionName + "] in query", Token.newString(functionName, rightExpression.getLineNumber(), rightExpression.getColumnNumber()), sourceUnit));
        }
    }

    private void handleBinaryExpressionSide(Expression expressionSide, Expression oppositeSide, String operator, BlockStatement newCode, boolean addAll, List<String> propertyNames, VariableScope variableScope) {
        if(aliasExpressions.contains(expressionSide)) return;
        if (expressionSide instanceof BinaryExpression) {
            addBinaryExpressionToNewBody(propertyNames, newCode, (BinaryExpression) expressionSide, addAll, variableScope);
        } else if (expressionSide instanceof NotExpression) {
            handleNegation(propertyNames, newCode, (NotExpression) expressionSide, variableScope);
        } else if (expressionSide instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression) expressionSide;
            handleAssociationMethodCallExpression(newCode, methodCallExpression, propertyNames, variableScope);
        } else if (expressionSide instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) expressionSide;
            handleAssociationQueryViaPropertyExpression(pe, oppositeSide, operator, newCode, propertyNames, null, variableScope);
        } else {
            // TODO: compilation error?
        }
    }

    private void handleAssociationQueryViaPropertyExpression(PropertyExpression pe, Expression oppositeSide, String operator, BlockStatement newCode, List<String> propertyNames, String functionName, VariableScope variableScope) {
        Expression objectExpression = pe.getObjectExpression();
        if (objectExpression instanceof PropertyExpression) {
            // nested property expression, we have to find the root variable expression and walk backwards through all the properties involved
            List<String> associationMethodCalls = new ArrayList<String>();

            while (objectExpression instanceof PropertyExpression) {
                PropertyExpression currentPe = (PropertyExpression) objectExpression;
                associationMethodCalls.add(currentPe.getPropertyAsString());
                objectExpression = currentPe.getObjectExpression();
            }

            if (objectExpression instanceof VariableExpression) {
                VariableExpression ve = (VariableExpression) objectExpression;

                String propertyName = ve.getName();
                // handle trait
                if(!(propertyName.equals("$self") && Traits.isTrait(objectExpression.getType()))) {
                    associationMethodCalls.add(propertyName);
                }


                Collections.reverse(associationMethodCalls);

                ClassNode currentType = currentClassNode;
                BlockStatement currentBody = newCode;

                VariableExpression delegateExpression = new VariableExpression("delegate");
                for (Iterator<String> iterator = associationMethodCalls.iterator(); iterator.hasNext(); ) {
                    String associationMethodCall = iterator.next();

                    ClosureAndArguments closureAndArguments = new ClosureAndArguments(variableScope);

                    ArgumentListExpression arguments = closureAndArguments.getArguments();
                    ClassNode type = getPropertyTypeFromGenerics(associationMethodCall, currentType);

                    if (type == null) break;

                    currentType = type;
                    currentBody.addStatement(new ExpressionStatement(new MethodCallExpression(delegateExpression, associationMethodCall, arguments)));
                    currentBody = closureAndArguments.getCurrentBody();

                    if (!iterator.hasNext()) {
                        String associationProperty = pe.getPropertyAsString();
                        List<String> associationPropertyNames = getPropertyNamesForAssociation(type);
                        ClassNode existing = this.currentClassNode;
                        try {

                            this.currentClassNode = type;
                            boolean hasNoProperties = associationPropertyNames.isEmpty();
                            if (functionName != null) {
                                handleFunctionCall(currentBody, operator, oppositeSide, functionName, new ConstantExpression(associationProperty));
                            } else {
                                addCriteriaCallMethodExpression(currentBody, operator, pe, oppositeSide, associationProperty, associationPropertyNames, hasNoProperties, variableScope);
                            }
                        } finally {
                            this.currentClassNode = existing;
                        }
                    }
                }
            }
        } else if (objectExpression instanceof VariableExpression) {
            String propertyName = objectExpression.getText();
            // handle trait
            if(propertyName.equals("$self") && Traits.isTrait(objectExpression.getType())) {
                propertyName = pe.getPropertyAsString();
            }
            Object aliased = aliases.get(propertyName);
            if (propertyNames.contains(propertyName) || (aliased != null && propertyNames.contains(aliased))) {
                String associationProperty = pe.getPropertyAsString();
                String actualPropertyName = aliased != null ? aliased.toString() : propertyName;

                ClassNode classNode = currentClassNode;
                ClassNode type = getPropertyTypeFromGenerics(actualPropertyName, classNode);
                if(!AstUtils.isDomainClass(type)) {

                    if(AstUtils.isGroovyType(type)) {
                        // an embedded property
                        List<String> associationPropertyNames = AstPropertyResolveUtils.getPropertyNames(type);
                        boolean hasNoProperties = associationPropertyNames.isEmpty();
                        if (!hasNoProperties && !associationPropertyNames.contains(associationProperty)) {
                            sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot query property \"" + associationProperty + "\" - no such property on class " + type.getName() + " exists.", Token.newString(propertyName, pe.getLineNumber(), pe.getColumnNumber()), sourceUnit));
                        }

                        ClosureAndArguments closureAndArguments = new ClosureAndArguments(variableScope);
                        BlockStatement currentBody = closureAndArguments.getCurrentBody();
                        ArgumentListExpression arguments = closureAndArguments.getArguments();

                        ClassNode existing = this.currentClassNode;
                        try {
                            this.currentClassNode = type;
                            if (functionName != null) {
                                handleFunctionCall(currentBody, operator, oppositeSide, functionName, new ConstantExpression(associationProperty));
                            } else {
                                addCriteriaCallMethodExpression(currentBody, operator, pe, oppositeSide, associationProperty, associationPropertyNames, hasNoProperties, variableScope);
                            }
                        } finally {
                            this.currentClassNode = existing;
                        }

                        newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("delegate"), actualPropertyName, arguments)));
                    }
                    else {
                        addCriteriaCallMethodExpression(newCode, operator, pe, oppositeSide, associationProperty, Collections.<String>emptyList(), false, variableScope);
                    }
                }
                else {

                    List<String> associationPropertyNames = getPropertyNamesForAssociation(type);
                    if (associationPropertyNames == null) {
                        associationPropertyNames = getPropertyNamesForAssociation(classNode);
                    }

                    ClosureAndArguments closureAndArguments = new ClosureAndArguments(variableScope);
                    BlockStatement currentBody = closureAndArguments.getCurrentBody();
                    ArgumentListExpression arguments = closureAndArguments.getArguments();

                    boolean hasNoProperties = associationPropertyNames.isEmpty();
                    if (!hasNoProperties && !associationPropertyNames.contains(associationProperty)) {
                        sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot query property \"" + associationProperty + "\" - no such property on class " + type.getName() + " exists.", Token.newString(propertyName, pe.getLineNumber(), pe.getColumnNumber()), sourceUnit));
                    }
                    ClassNode existing = this.currentClassNode;
                    try {
                        this.currentClassNode = type;
                        if (functionName != null) {
                            handleFunctionCall(currentBody, operator, oppositeSide, functionName, new ConstantExpression(associationProperty));
                        } else {
                            addCriteriaCallMethodExpression(currentBody, operator, pe, oppositeSide, associationProperty, associationPropertyNames, hasNoProperties, variableScope);
                        }
                    } finally {
                        this.currentClassNode = existing;
                    }
                    newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("delegate"), actualPropertyName, arguments)));
                }
            } else if((aliased instanceof ClassNode) && (oppositeSide instanceof PropertyExpression)) {
                String rootReference = pe.getText();
                PropertyExpression oppositeProperty = (PropertyExpression) oppositeSide;
                String targetObject = oppositeProperty.getObjectExpression().getText();
                if(aliases.containsKey(targetObject)) {
                    String methodToCall = PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
                    ArgumentListExpression args = new ArgumentListExpression();
                    args.addExpression(new ConstantExpression(rootReference));
                    args.addExpression(new ConstantExpression(oppositeProperty.getText()));
                    newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), methodToCall, args)));
                }

            } else if (!variableScope.isReferencedLocalVariable(propertyName)) {
                sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot query property \"" + propertyName + "\" - no such property on class " + this.currentClassNode.getName() + " exists.", Token.newString(propertyName, pe.getLineNumber(), pe.getColumnNumber()), sourceUnit));
            }
        }
    }


    private ClassNode getPropertyTypeFromGenerics(String propertyName, ClassNode classNode) {
        ClassNode type = AstPropertyResolveUtils.getPropertyType(classNode, propertyName);
        if (type != null && !AstUtils.isDomainClass(type)) {
            ClassNode associationTypeFromGenerics = getAssociationTypeFromGenerics(type);
            if (associationTypeFromGenerics != null && AstUtils.isDomainClass(associationTypeFromGenerics)) {
                type = associationTypeFromGenerics;
            }
        }
        return type;
    }

    private void addCriteriaCallMethodExpression(BlockStatement newCode, String operator, Expression leftExpression, Expression rightExpression, String propertyName, List<String> propertyNames, boolean addAll, VariableScope variableScope) {
        String methodToCall = OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
        if (methodToCall == null) {
            sourceUnit.getErrorCollector().addError(new LocatedMessage("Unsupported operator [" + operator + "] used in query", Token.newString(rightExpression.getText(), rightExpression.getLineNumber(), rightExpression.getColumnNumber()), sourceUnit));
        }
        addCriteriaCall(newCode, operator, leftExpression, rightExpression, propertyName, propertyNames, addAll, methodToCall, variableScope);
    }

    private void addCriteriaCall(BlockStatement newCode, String operator, Expression leftExpression, Expression rightExpression, String propertyName, List<String> propertyNames, boolean addAll, String methodToCall, VariableScope variableScope) {
        if (rightExpression instanceof VariableExpression) {
            String rightPropertyName = rightExpression.getText();
            if (!variableScope.isReferencedLocalVariable(rightPropertyName)) {
                if ((propertyNames.contains(rightPropertyName) || addAll) && PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP.containsKey(operator)) {
                    methodToCall = PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
                    rightExpression = new ConstantExpression(rightPropertyName);
                }
            }
        } else if (rightExpression instanceof MethodCallExpression) {
            // potential aggregation
            MethodCallExpression aggregateMethodCall = (MethodCallExpression) rightExpression;
            Expression methodTarget = aggregateMethodCall.getObjectExpression();

            String methodName = aggregateMethodCall.getMethodAsString();
            String aggregateFunctionName = AGGREGATE_FUNCTIONS.get(methodName);
            if ("of".equals(methodName) && aggregateMethodCall.getObjectExpression() instanceof MethodCallExpression) {
                ArgumentListExpression arguments = (ArgumentListExpression) aggregateMethodCall.getArguments();
                if (arguments.getExpressions().size() == 1 && arguments.getExpression(0) instanceof ClosureExpression) {
                    ClosureExpression ce = (ClosureExpression) arguments.getExpression(0);
                    transformClosureExpression(this.currentClassNode, ce);
                    aggregateMethodCall = (MethodCallExpression) aggregateMethodCall.getObjectExpression();

                    aggregateFunctionName = AGGREGATE_FUNCTIONS.get(aggregateMethodCall.getMethodAsString());
                    ArgumentListExpression aggregateMethodCallArguments = (ArgumentListExpression) aggregateMethodCall.getArguments();
                    if (aggregateFunctionName != null && aggregateMethodCallArguments.getExpressions().size() == 1) {
                        Expression expression = aggregateMethodCallArguments.getExpression(0);
                        String aggregatePropertyName = null;
                        if (expression instanceof VariableExpression || expression instanceof ConstantExpression) {
                            aggregatePropertyName = expression.getText();
                        }

                        boolean validProperty = aggregatePropertyName != null && propertyNames.contains(aggregatePropertyName);
                        if (validProperty) {
                            BlockStatement bs = (BlockStatement) ce.getCode();
                            addProjectionToCurrentBody(bs, aggregateFunctionName, aggregatePropertyName, variableScope);
                            rightExpression = new MethodCallExpression(new ConstructorCallExpression(getParameterizedDetachedCriteriaClassNode(null), new ArgumentListExpression(new ClassExpression(this.currentClassNode))), "build", new ArgumentListExpression(ce));
                        }
                    }
                }
            } else if (aggregateFunctionName != null) {
                if((methodTarget instanceof VariableExpression) &&
                    ((VariableExpression)methodTarget).isThisExpression()) {
                    Expression arguments = aggregateMethodCall.getArguments();
                    if (arguments instanceof ArgumentListExpression) {
                        ArgumentListExpression argList = (ArgumentListExpression) arguments;
                        List<Expression> expressions = argList.getExpressions();
                        int argCount = expressions.size();
                        if (argCount == 1) {
                            Expression expression = expressions.get(0);
                            String aggregatePropertyName;
                            if (expression instanceof VariableExpression || expression instanceof ConstantExpression) {
                                aggregatePropertyName = expression.getText();
                            } else {
                                sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot use aggregate function " + aggregateFunctionName + " on expressions \"" + expression.getText() + "\".", Token.newString(propertyName, aggregateMethodCall.getLineNumber(), aggregateMethodCall.getColumnNumber()), sourceUnit));
                                return;
                            }

                            boolean validProperty = aggregatePropertyName != null && propertyNames.contains(aggregatePropertyName);
                            if (validProperty) {
                                ClosureAndArguments closureAndArguments = new ClosureAndArguments(variableScope);
                                BlockStatement currentBody = closureAndArguments.getCurrentBody();

                                addProjectionToCurrentBody(currentBody, aggregateFunctionName, aggregatePropertyName, variableScope);

                                rightExpression = closureAndArguments.getClosureExpression();

                                if ("property".equals(aggregateFunctionName) && METHOD_TO_SUBQUERY_MAP.containsKey(methodToCall)) {
                                    methodToCall = methodToCall + "All";
                                }
                            } else {
                                sourceUnit.getErrorCollector().addError(new LocatedMessage("Cannot use aggregate function " + aggregateFunctionName + " on property \"" + aggregatePropertyName + "\" - no such property on class " + this.currentClassNode.getName() + " exists.", Token.newString(propertyName, aggregateMethodCall.getLineNumber(), aggregateMethodCall.getColumnNumber()), sourceUnit));
                            }
                        }
                    }
                }
            } else if (isFunctionCall(aggregateMethodCall)) {
                // TODO: Allow function calls on right hand arguments
                ArgumentListExpression existingArgs = (ArgumentListExpression) aggregateMethodCall.getArguments();
                Expression propertyNameExpression = existingArgs.getExpression(0);
                sourceUnit.getErrorCollector().addError(new LocatedMessage("Function call " + aggregateFunctionName + " not allowed on property \"" + propertyNameExpression.getText() + "\". Function calls can currently only be used on the left-hand side of expressions", Token.newString(propertyName, aggregateMethodCall.getLineNumber(), aggregateMethodCall.getColumnNumber()), sourceUnit));
                return;
//
//                ArgumentListExpression newArgs = new ArgumentListExpression();
//                ArgumentListExpression constructorArgs = new ArgumentListExpression();
//                constructorArgs.addExpression(new ConstantExpression(methodName));
//                ClassNode criterionClassNode = OPERATOR_TO_PROPERTY_CRITERION_METHOD_MAP.get(operator);
//                if (criterionClassNode != null) {
//                    ArgumentListExpression criterionConstructorArguments = new ArgumentListExpression();
//                    if (!(propertyNameExpression instanceof ConstantExpression)) {
//                        propertyNameExpression = new ConstantExpression(propertyNameExpression.getText());
//                    }
//                    criterionConstructorArguments.addExpression(new ConstantExpression(propertyName));
//                    criterionConstructorArguments.addExpression(propertyNameExpression);
//
//                    constructorArgs.addExpression(new ConstructorCallExpression(criterionClassNode, criterionConstructorArguments));
//                    constructorArgs.addExpression(new ConstantExpression(true));
//                    ConstructorCallExpression constructorCallExpression = new ConstructorCallExpression(FUNCTION_CALL_CRITERION, constructorArgs);
//                    newArgs.addExpression(constructorCallExpression );
//
//                    newCode.addStatement(new ExpressionStatement(new MethodCallExpression(THIS_EXPRESSION, "add", newArgs)));
//                }
//                else {
//                    sourceUnit.getErrorCollector().addError(new LocatedMessage("Function call "+aggregateFunctionName+" not allowed on property \""+propertyNameExpression.getText()+"\".", Token.newString(propertyName,aggregateMethodCall.getLineNumber(), aggregateMethodCall.getColumnNumber()), sourceUnit));
//                }
//                return;
            }
        } else {
            if ("like".equals(methodToCall) && rightExpression instanceof BitwiseNegationExpression) {
                methodToCall = "rlike";
                BitwiseNegationExpression bne = (BitwiseNegationExpression) rightExpression;
                rightExpression = bne.getExpression();
            } else if ("inList".equals(methodToCall) && rightExpression instanceof RangeExpression) {
                methodToCall = "between";
                RangeExpression re = (RangeExpression) rightExpression;
                ArgumentListExpression betweenArgs = new ArgumentListExpression();
                betweenArgs.addExpression(new ConstantExpression(propertyName))
                        .addExpression(re.getFrom())
                        .addExpression(re.getTo());
                rightExpression = betweenArgs;
            }
        }
        ArgumentListExpression arguments;

        if (rightExpression instanceof ArgumentListExpression) {
            arguments = (ArgumentListExpression) rightExpression;
        } else if (rightExpression instanceof ConstantExpression) {
            ConstantExpression constant = (ConstantExpression) rightExpression;
            if (constant.getValue() == null) {
                boolean singleArg = false;
                if (operator.equals(EQUALS_OPERATOR)) {
                    singleArg = true;
                    methodToCall = IS_NULL_CRITERION;
                } else if (operator.equals("!=")) {
                    singleArg = true;
                    methodToCall = "isNotNull";
                }

                arguments = new ArgumentListExpression();
                arguments.addExpression(new ConstantExpression(propertyName));
                if (!singleArg) {
                    arguments.addExpression(rightExpression);
                }
            } else {
                arguments = new ArgumentListExpression();
                arguments.addExpression(new ConstantExpression(propertyName))
                        .addExpression(rightExpression);
            }
        } else if(rightExpression instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) rightExpression;
            String property = pe.getObjectExpression().getText();
            if((leftExpression instanceof PropertyExpression ) && aliases.containsKey(property)) {
                aliasExpressions.add(pe);
                arguments = new ArgumentListExpression();
                arguments.addExpression(new ConstantExpression(((PropertyExpression)leftExpression).getPropertyAsString()));
                arguments.addExpression(new ConstantExpression(pe.getText()));
                methodToCall = PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
            }
            else if((leftExpression instanceof VariableExpression) && aliases.containsKey(property)) {
                aliasExpressions.add(pe);
                arguments = new ArgumentListExpression();
                arguments.addExpression(new ConstantExpression(((VariableExpression)leftExpression).getName()));
                arguments.addExpression(new ConstantExpression(pe.getText()));
                methodToCall = PROPERTY_COMPARISON_OPERATOR_TO_CRITERIA_METHOD_MAP.get(operator);
            }
            else {
                arguments = new ArgumentListExpression();
                arguments.addExpression(new ConstantExpression(propertyName))
                        .addExpression(rightExpression);
            }
        } else {
            arguments = new ArgumentListExpression();
            arguments.addExpression(new ConstantExpression(propertyName))
                    .addExpression(rightExpression);
        }
        newCode.addStatement(new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), methodToCall, arguments)));
    }

    private void addProjectionToCurrentBody(BlockStatement currentBody, String functionName, String aggregatePropertyName, VariableScope variableScope) {
        ClosureAndArguments projectionsBody = new ClosureAndArguments(variableScope);
        ArgumentListExpression aggregateArgs = new ArgumentListExpression();
        aggregateArgs.addExpression(new ConstantExpression(aggregatePropertyName));
        VariableExpression thisExpression = new VariableExpression("this");
        projectionsBody.getCurrentBody().addStatement(new ExpressionStatement(new MethodCallExpression(thisExpression, functionName, aggregateArgs)));
        currentBody.addStatement(new ExpressionStatement(new MethodCallExpression(thisExpression, "projections", projectionsBody.getArguments())));
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return this.sourceUnit;
    }

    private class ClosureAndArguments {
        private BlockStatement currentBody;
        private ArgumentListExpression arguments;
        private ClosureExpression closureExpression;
        private VariableScope variableScope;

        private ClosureAndArguments(VariableScope variableScope) {
            this.variableScope = variableScope;
            build();
        }

        public BlockStatement getCurrentBody() {
            return currentBody;
        }

        public ArgumentListExpression getArguments() {
            return arguments;
        }

        private ClosureAndArguments build() {
            currentBody = new BlockStatement();
            closureExpression = new ClosureExpression(new Parameter[0], currentBody);
            closureExpression.setVariableScope(variableScope);
            closureExpression.setCode(currentBody);

            arguments = new ArgumentListExpression();
            arguments.addExpression(closureExpression);
            return this;
        }

        public ClosureExpression getClosureExpression() {
            return closureExpression;
        }
    }

    @SuppressWarnings("rawtypes")
    private static <K, V> Map newMap(Object... keysAndValues) {
        if (keysAndValues == null) {
            return Collections.emptyMap();
        }
        if (keysAndValues.length % 2 == 1) {
            throw new IllegalArgumentException("Must have an even number of keys and values");
        }

        Map<K, V> map = new HashMap<K, V>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((K) keysAndValues[i], (V) keysAndValues[i + 1]);
        }
        return map;
    }

    private static <T> Set<T> newSet(T... values) {
        if (values == null) {
            return Collections.emptySet();
        }

        return new HashSet<T>(Arrays.asList(values));
    }
}
