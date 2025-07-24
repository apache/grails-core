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
package org.grails.compiler

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL

import grails.util.GrailsNameUtils
import org.grails.compiler.injection.GrailsASTUtils

/**
 *
 * @author Jeff Brown
 * @since 2.4.3
 *
 */
class RelationshipManagementMethodTypeCheckingExtension extends TypeCheckingDSL {

    @Override
    def run() {
        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            def dynamicCall
            def matcher = name =~ /(addTo|removeFrom)([A-Z].*)/
            if(matcher) {
                def sourceUnit = receiver.module?.context
                if(GrailsASTUtils.isDomainClass(receiver, sourceUnit)) {
                    def propertyName = GrailsNameUtils.getPropertyName(matcher.group(2))
                    if(receiver.getField(propertyName)) {
                        dynamicCall = makeDynamic(call, receiver)
                        dynamicCall.declaringClass = receiver
                    }
                }
            }
            dynamicCall
        }
        null
    }
}
