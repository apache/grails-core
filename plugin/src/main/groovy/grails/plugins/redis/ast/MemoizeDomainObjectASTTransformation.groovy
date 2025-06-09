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
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class MemoizeDomainObjectASTTransformation extends AbstractMemoizeASTTransformation {

    @Override
    protected void generateMemoizeProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map memoizeProperties) {
        def keyString = astNodes[0]?.members?.key?.text
        def clazz = astNodes[0]?.members?.clazz
        def expire = astNodes[0]?.members?.expire?.text

        if(!validateMemoizeProperties(clazz, astNodes, sourceUnit, keyString, expire)) {
            return
        }

        //***************************************************************************

        memoizeProperties.put(KEY, keyString)
        memoizeProperties.put(CLAZZ, clazz)
        if(expire) {
            memoizeProperties.put(EXPIRE, expire)
        }
    }

    private Boolean validateMemoizeProperties(clazz, ASTNode[] astNodes, SourceUnit sourceUnit, keyString, expire) {
        if(!clazz?.class == ClassExpression) {
            addError('Internal Error: annotation does not contain clazz property', astNodes[0], sourceUnit)
            return false
        }

        if(keyString?.class != String) {
            addError('Internal Error: annotation does not contain key String', astNodes[0], sourceUnit)
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
        new ConstantExpression('memoizeDomainObject')
    }

    @Override
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map memoizeProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        argumentListExpression.addExpression((Expression) memoizeProperties.get(CLAZZ))
        addRedisServiceMemoizeKeyExpression(memoizeProperties, argumentListExpression)
        if(memoizeProperties.containsKey(EXPIRE)) {
            addRedisServiceMemoizeExpireExpression(memoizeProperties, argumentListExpression)
        }
        argumentListExpression
    }
}