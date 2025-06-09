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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.VariableScopeVisitor

import com.google.gson.Gson

import grails.plugins.redis.RedisService

import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class MemoizeObjectASTTransformation extends AbstractMemoizeASTTransformation {
	
	@Override
	void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
		//map to hold the params we will pass to the memoize[?] method
		def memoizeProperties = [:]

		try {
			injectService(sourceUnit, REDIS_SERVICE, RedisService)
			injectImport(sourceUnit, Gson)
			generateMemoizeProperties(astNodes, sourceUnit, memoizeProperties)
			//if the key is missing there is an issue with the annotation
			if(!memoizeProperties.containsKey(KEY) || !memoizeProperties.get(KEY)) {
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
	
	@Override
	protected List<Statement> memoizeMethod(MethodNode methodNode, Map memoizeProperties) {
		BlockStatement body = new BlockStatement()
		addToJson(methodNode)
		addRedisServiceMemoizeInvocation(body, methodNode, memoizeProperties)
		body = addFromJson(body, memoizeProperties)
		body.statements
	}

	private ConstructorCallExpression createGson(){
		// new Gson()
		return new ConstructorCallExpression(
			new ClassNode(Gson.class), 
			new ArgumentListExpression())
	}
	
	private void addToJson(MethodNode methodNode){
		List stmts = methodNode.code.getStatements()
		
		// new Gson().toJson(...)
		ReturnStatement toJsonStatment = new ReturnStatement(
			new MethodCallExpression(
				createGson(),
				new ConstantExpression('toJson'),
				new ArgumentListExpression(
					stmts[-1].expression
				)
			)
		)
		
		stmts[-1] = toJsonStatment
		methodNode.setCode(new BlockStatement(stmts as Statement[], new VariableScope()))
	}

	private BlockStatement addFromJson(BlockStatement body, Map memoizeProperties){
		// last statement should be the redisService.memoize(...){...} call
		List stmts = body.getStatements()
		
		ArgumentListExpression fromJsonArgList = new ArgumentListExpression()
		fromJsonArgList.addExpression(stmts[-1].expression)
		fromJsonArgList.addExpression((Expression) memoizeProperties.get(CLAZZ))
		
		// new Gson().fromJson(..., <return type>.class)
		ReturnStatement fromJsonStatement = new ReturnStatement(
			new MethodCallExpression(
			createGson(),
			new ConstantExpression('fromJson'),
			fromJsonArgList
			)
		)
		stmts[-1] = fromJsonStatement
		new BlockStatement(stmts as Statement[], new VariableScope())
	}
	
    @Override
    protected void generateMemoizeProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map memoizeProperties) {
        def expire = astNodes[0]?.members?.expire?.text
        def keyString = astNodes[0]?.members?.key?.text
		def clazz = astNodes[0]?.members?.clazz

        if(!validateMemoizeProperties(astNodes, sourceUnit, keyString, expire, clazz)) {
            return
        }

        memoizeProperties.put(KEY, keyString)
		memoizeProperties.put(CLAZZ, clazz)
        if(expire) {
            memoizeProperties.put(EXPIRE, expire)
        }
    }

    private Boolean validateMemoizeProperties(ASTNode[] astNodes, SourceUnit sourceUnit, keyString, expire, clazz) {
        if(keyString.class != String) {
            addError('Internal Error: annotation does not contain key closure or key property', astNodes[0], sourceUnit)
            return false
        }
		if(!clazz?.class == ClassExpression) {
			addError('Internal Error: annotation does not contain clazz property', astNodes[0], sourceUnit)
			return false
		}
        if(expire && expire.class != String && !Integer.parseInt(expire)) {
            addError('Internal Error: provided expire is not an String (in millis)', astNodes[0], sourceUnit)
            return false
        }
        true
    }

    @Override
    protected ConstantExpression makeRedisServiceConstantExpression() {
        new ConstantExpression('memoize')
    }

    @Override
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map memoizeProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        addRedisServiceMemoizeKeyExpression(memoizeProperties, argumentListExpression)
        if(memoizeProperties.containsKey(EXPIRE)) {
            addRedisServiceMemoizeExpireExpression(memoizeProperties, argumentListExpression)
        }
        argumentListExpression
    }
	
	/**
	 * Fix the variable scopes for closures.  Without this closures will be missing the input params being passed from the parent scope.
	 * @param sourceUnit The SourceUnit to visit and add the variable scopes.
	 */
	private void visitVariableScopes(SourceUnit sourceUnit) {
		VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(sourceUnit);
		sourceUnit.AST.classes.each {
			scopeVisitor.visitClass(it)
		}
	}

	/**
	 * Determine if the class trying to use MemoizeObject annotation has the needed imports.
	 * @param sourceUnit SourceUnit to detect and/or inject import into
	 * @param importClass Class of the import
	 */
	private void injectImport(SourceUnit sourceUnit, Class importClass) {
		if(!sourceUnit.AST.imports.any {it.className == ClassHelper.make(importClass).name}
				&& !sourceUnit.AST.starImports.any {it.packageName == "${ClassHelper.make(importClass).packageName}."}) {
			sourceUnit.AST.addImport(importClass.simpleName, ClassHelper.make(importClass))
		}
	}
	
}