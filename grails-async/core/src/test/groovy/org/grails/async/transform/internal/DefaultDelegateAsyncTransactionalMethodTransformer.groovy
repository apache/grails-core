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
package org.grails.async.transform.internal

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ListExpression

/**
 * Stands in for the transactional transformer the async plugin supplies. {@code DelegateAsyncTransformation}
 * looks the implementation up by this name, so having it on the test classpath is what lets the
 * lookup succeed in this module, and recording the calls shows that the transformation consults it
 * once for every method it wraps.
 */
class DefaultDelegateAsyncTransactionalMethodTransformer implements DelegateAsyncTransactionalMethodTransformer {

    static final List<String> TRANSFORMED = Collections.synchronizedList([])

    @Override
    void transformTransactionalMethod(ClassNode classNode, ClassNode delegateClassNode, MethodNode methodNode,
            ListExpression promiseDecoratorLookupArguments) {
        TRANSFORMED.add(delegateClassNode.name + '.' + methodNode.name)
    }

}
