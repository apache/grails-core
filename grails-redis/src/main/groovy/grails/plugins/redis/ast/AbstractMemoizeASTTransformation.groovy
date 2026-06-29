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

package grails.plugins.redis.ast

import grails.plugins.redis.RedisService
import grails.util.Holders
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.VariableScopeVisitor
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority
import org.springframework.beans.factory.annotation.Autowired

import static org.springframework.asm.Opcodes.ACC_PUBLIC
import static org.springframework.asm.Opcodes.ACC_STATIC

/**
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
abstract class AbstractMemoizeASTTransformation implements ASTTransformation, TransformWithPriority {

    protected static final String KEY = 'key'
    protected static final String MEMOIZE_KEY = 'memKey'
    protected static final String EXPIRE = 'expire'
    protected static final String CLAZZ = 'clazz'
    protected static final String MEMBER = 'member'
    protected static final String HASH_CODE = '#'
    protected static final String GSTRING = '$'
    protected static final String REDIS_SERVICE = 'redisService'
    protected static final String GET_REDIS_SERVICE = 'getRedisService'
    protected static final String THIS = 'this'
    protected static final String PRINTLN = 'println'

    public static final ClassNode AUTOWIRED_CLASS_NODE = new ClassNode(Autowired).getPlainNodeReference()

    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        //return //todo: this isn't working with grails 3.0+, UGH!

        //map to hold the params we will pass to the memoize[?] method
        def memoizeProperties = [:]

        try {
            injectService(sourceUnit, REDIS_SERVICE, RedisService)
            generateMemoizeProperties(astNodes, sourceUnit, memoizeProperties)
            //if the key is missing there is an issue with the annotation
            if (!memoizeProperties.containsKey(KEY) || !memoizeProperties.get(KEY)) {
                return
            }
            addMemoizedStatements((MethodNode) astNodes[1], memoizeProperties)
            visitVariableScopes(sourceUnit)
        } catch (Exception e) {
            addError("Error during Memoize AST Transformation: ${e}", astNodes[0], sourceUnit)
            throw e
        }
    }

    /**
     * Create the statements for the memoized method, clear the node and then readd the memoized code back to the method.
     * @param methodNode The MethodNode we will be clearing and replacing with the redisService.memoize[?] method call with.
     * @param memoizeProperties The map of properties to use for the service invocation
     */
    private void addMemoizedStatements(MethodNode methodNode, LinkedHashMap memoizeProperties) {
        def stmt = memoizeMethod(methodNode, memoizeProperties)
        methodNode.code.statements.clear()
        methodNode.code.statements.addAll(stmt)
    }

    /**
     * Fix the variable scopes for closures.  Without this closures will be missing the input params being passed from the parent scope.
     * @param sourceUnit The SourceUnit to visit and add the variable scopes.
     */
    private static void visitVariableScopes(SourceUnit sourceUnit) {
        VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(sourceUnit);
        sourceUnit.AST.classes.each {
            scopeVisitor.visitClass(it)
        }
    }

    /**
     * Determine if the user missed injecting the redisService into the class with the @Memoized method.
     * @param sourceUnit SourceUnit to detect and/or inject service into
     * @param serviceName name of the service to detect and/or inject
     * @param serviceClass Class of the service
     */
    protected static void injectService(SourceUnit sourceUnit, String serviceName, Class serviceClass) {
        def classNode = (ClassNode) sourceUnit.AST.classes.toArray()[0]
        if (!classNode.fields?.any { it.name == serviceName }) {
            addImport(sourceUnit, serviceClass)
            addImport(sourceUnit, Holders)
            addStarImport(sourceUnit, Holders)
            addRedisServiceBuilder(classNode, serviceName, RedisService)
            addFieldToTransients(classNode, serviceName)
        }
    }


    private static void addStarImport(SourceUnit sourceUnit, Class serviceClass) {
        if (!sourceUnit.AST.starImports.any { it.packageName =~ "${ClassHelper.make(serviceClass).packageName}" }) {
            sourceUnit.AST.addStarImport(ClassHelper.make(serviceClass).packageName)
        }
    }

    private static void addImport(SourceUnit sourceUnit, Class serviceClass) {
        if (!sourceUnit.AST.imports.any { it.className == ClassHelper.make(serviceClass).name }
                && !sourceUnit.AST.starImports.any {
            it.packageName == "${ClassHelper.make(serviceClass).packageName}.".toString()
        }) {
            sourceUnit.AST.addImport(serviceClass.simpleName, ClassHelper.make(serviceClass))
        }
    }

    private static void addRedisService(ClassNode cNode,
                                        String propertyName,
                                        Class propertyType = Object.class,
                                        Expression initialValue = null) {
        //def ast = new AstBuilder().buildFromString("Holders?.findApplicationContext()")
        //?.getBean('redisService')")

        FieldNode fieldNode = new FieldNode(
                propertyName,
                ACC_PUBLIC,
                new ClassNode(propertyType),
                new ClassNode(cNode.class),
                initialValue
        )

        def holderExpression = new StaticMethodCallExpression(ClassHelper.make(Holders.class), 'findApplicationContext', null)
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        argumentListExpression.addExpression(makeConstantExpression('redisService'))
        def getBeanExpression = new MethodCallExpression(holderExpression, 'getBean', argumentListExpression)
        getBeanExpression.setSafe(true)

        def returnStatement = new ReturnStatement(getBeanExpression)


        def propertyNode = new PropertyNode(fieldNode, ACC_PUBLIC, returnStatement, null)
        cNode.addProperty(propertyNode)
    }

    private static void addRedisServiceBuilder(ClassNode cNode, String propertyName,
                                               Class propertyType = Object.class,
                                               Expression initialValue = null) {
        def ast = new AstBuilder().buildFromString("return Holders?.findApplicationContext()?.getBean('redisService')")

        FieldNode fieldNode = new FieldNode(
                propertyName,
                ACC_PUBLIC,
                new ClassNode(propertyType),
                new ClassNode(cNode.class),
                initialValue
        )

        def returnStatement = new ReturnStatement(ast[0].statements[0].expression)

        def propertyNode = new PropertyNode(fieldNode, ACC_PUBLIC, returnStatement, null)
        cNode.addProperty(propertyNode)

    }

    private static void addFieldToTransients(ClassNode parentClass, String propertyName) {
        if (parentClass != null) {
            FieldNode transients = parentClass.getField("transients")
            if (!transients) {
                transients = parentClass.addField(
                        "transients",
                        ACC_PUBLIC | ACC_STATIC,
                        ClassHelper.DYNAMIC_TYPE,
                        new ListExpression()
                )
            }
            ((ListExpression) transients.initialExpression).addExpression(new ConstantExpression(propertyName))
            ((ListExpression) transients.initialExpression).addExpression(new ConstantExpression("get${propertyName.capitalize()}"))
        }
    }

    /**
     * This method adds a new property to the class. Groovy automatically handles adding the getters and setters so you
     * don't have to create special methods for those.  This could be reused for other properties.
     * @param cNode Node to inject property onto.  Usually a ClassNode for the current class.
     * @param anotationName The name of the property to inject.
     * @param propertyType The object class of the property. (defaults to Object.class)
     * @param initialValue Initial value of the property. (defaults null)
     */
    private static void addAutowiredAnnotation(AnnotatedNode node) {
        final AnnotationNode autowiredAnnotation = new AnnotationNode(AUTOWIRED_CLASS_NODE)
        node.addAnnotation(autowiredAnnotation)
    }

    /**
     * method to add the key and expires and options if they exist
     * @param astNodes the ast nodes
     * @param sourceUnit the source unit
     * @param memoizeProperties map to put data in
     * @return
     */
    protected abstract void generateMemoizeProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map memoizeProperties)

    protected abstract ConstantExpression makeRedisServiceConstantExpression()

    protected abstract ArgumentListExpression makeRedisServiceArgumentListExpression(Map memoizeProperties)

    protected List<Statement> memoizeMethod(MethodNode methodNode, Map memoizeProperties) {
        BlockStatement body = new BlockStatement()
        addRedisServiceMemoizeInvocation(body, methodNode, memoizeProperties)
        body.statements
    }

    protected void addRedisServiceMemoizeInvocation(BlockStatement body, MethodNode methodNode, Map memoizeProperties) {
        ArgumentListExpression argumentListExpression = makeRedisServiceArgumentListExpression(memoizeProperties)
        argumentListExpression.addExpression(makeClosureExpression(methodNode))

        def ast = new AstBuilder().buildFromString("getRedisService()")

        def getRedisServiceMethodExpression = ast[0].statements[0].expression as MethodCallExpression
        getRedisServiceMethodExpression.setSafe(true)

        def redisServiceMethodMethodExpression = new MethodCallExpression(
                getRedisServiceMethodExpression,
                makeRedisServiceConstantExpression(),
                argumentListExpression
        )

        redisServiceMethodMethodExpression.setSafe(true)

        body.addStatement(
                new ReturnStatement(
                        redisServiceMethodMethodExpression
                )
        )
    }

    protected
    static void addRedisServiceMemoizeKeyExpression(Map memoizeProperties, ArgumentListExpression argumentListExpression) {
        if (memoizeProperties.get(KEY).toString().contains(HASH_CODE)) {
            def ast = new AstBuilder().buildFromString("""
                "${memoizeProperties.get(KEY).toString().replace(HASH_CODE, GSTRING).toString()}"
           """)
            argumentListExpression.addExpression(ast[0].statements[0].expression)
        } else {
            argumentListExpression.addExpression(new ConstantExpression(memoizeProperties.get(KEY).toString()))
        }
    }

    protected
    static void addRedisServiceMemoizeExpireExpression(Map memoizeProperties, ArgumentListExpression argumentListExpression) {
        if (memoizeProperties.get(EXPIRE).toString().contains(HASH_CODE)) {
            def ast = new AstBuilder().buildFromString("""
                Integer.parseInt("${memoizeProperties.get(EXPIRE).toString().replace(HASH_CODE, GSTRING).toString()}")
           """)

            argumentListExpression.addExpression(ast[0].statements[0].expression)
        } else {
            argumentListExpression.addExpression(makeConstantExpression(Integer.parseInt(memoizeProperties.get(EXPIRE).toString())))
        }
    }

    protected static ClosureExpression makeClosureExpression(MethodNode methodNode) {
        ClosureExpression closureExpression = new ClosureExpression(
                [] as Parameter[],
                new BlockStatement(methodNode.code.statements as Statement[], new VariableScope())
        )
        closureExpression.variableScope = new VariableScope()
        closureExpression
    }

    protected static ConstantExpression makeConstantExpression(constantExpression) {
        new ConstantExpression(constantExpression)
    }

    protected static void addError(String msg, ASTNode node, SourceUnit source) {
        int line = node.lineNumber
        int col = node.columnNumber
        SyntaxException se = new SyntaxException("${msg}\n", line, col)
        SyntaxErrorMessage sem = new SyntaxErrorMessage(se, source)
        source.errorCollector.addErrorAndContinue(sem)
    }
}
