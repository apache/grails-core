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

package org.grails.datastore.gorm.transactions.transform

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation

import grails.gorm.transactions.Rollback
import org.apache.grails.common.compiler.GroovyTransformOrder

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RollbackTransform extends TransactionalTransform {

    public static final ClassNode MY_TYPE = new ClassNode(Rollback)

    @Override
    protected String getTransactionTemplateMethodName() {
        return "executeAndRollback"
    }

    @Override
    int priority() {
        GroovyTransformOrder.ROLLBACK_ORDER
    }
}
