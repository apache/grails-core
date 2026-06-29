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
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class MemoizeScoreASTTransformation extends MemoizeASTTransformation {

    @Override
    protected void generateMemoizeProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map memoizeProperties) {
        super.generateMemoizeProperties(astNodes, sourceUnit, memoizeProperties)
        def member = astNodes[0]?.members?.member?.value

        if(!member || member?.class != String) {
            addError('Internal Error: member is required for score', astNodes[0], sourceUnit)
            return
        }

        memoizeProperties.put(MEMBER, member)
    }

    @Override
    protected ConstantExpression makeRedisServiceConstantExpression() {
        new ConstantExpression('memoizeScore')
    }

    @Override
    int priority() {
        RedisTransformOrder.MEMOIZE_SCORE_ORDER
    }
}