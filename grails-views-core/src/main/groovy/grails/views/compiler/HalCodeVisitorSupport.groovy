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

package grails.views.compiler

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

@CompileStatic
class HalCodeVisitorSupport extends CodeVisitorSupport {

    Map<BlockStatement, Statement> newStatements = [:]
    BlockStatement currentBlock

    CompilationUnit unit

    HalCodeVisitorSupport(CompilationUnit unit) {
        this.unit = unit
    }

    @Override
    void visitBlockStatement(BlockStatement block) {
        currentBlock = block
        List<Statement> statements = block.statements
        for(int i = 0; i < statements.size(); i++) {
            statements[i].visit(this)
            if (newStatements.containsKey(block)) {
                statements.add(i, newStatements.get(block))
                i++
                newStatements.remove(block)
            }
        }
    }

    @Override
    void visitVariableExpression(VariableExpression expression) {
        if (expression.accessedVariable && expression.accessedVariable.name == "hal") {
            Statement statement = stmt(
                assignX(propX(varX(expression.accessedVariable), 'delegate'), varX('delegate'))
            )
            newStatements.put(currentBlock, statement)
        }
    }
}
