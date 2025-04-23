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

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type
import static org.codehaus.groovy.ast.ClassHelper.Integer_TYPE
import static org.codehaus.groovy.ast.ClassHelper.LIST_TYPE

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.grails.compiler.injection.GrailsASTUtils
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL

/**
 *
 * @since 2.4
 */
class DynamicFinderTypeCheckingExtension extends TypeCheckingDSL {

    @Override
    Object run() {
        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            def dynamicCall = null
            if(receiver == CLASS_Type) {
                def genericsTypes = receiver.genericsTypes
                if(genericsTypes) {
                    def staticMethodCallTargetType = genericsTypes[0].type
                    if(staticMethodCallTargetType) {
                        def sourceUnit = staticMethodCallTargetType?.module?.context
                        if(GrailsASTUtils.isDomainClass(staticMethodCallTargetType, sourceUnit)) {
                            switch(name) {
                                case ~/countBy[A-Z].*/:
                                    dynamicCall = makeDynamicGormCall(call, Integer_TYPE, staticMethodCallTargetType)
                                    break
                                case ~/findAllBy[A-Z].*/:
                                case ~/listOrderBy[A-Z].*/:
                                    def returnType = parameterizedType(LIST_TYPE, staticMethodCallTargetType)
                                    dynamicCall = makeDynamicGormCall(call, returnType, staticMethodCallTargetType)
                                    break
                                case ~/findBy[A-Z].*/:
                                case ~/findOrCreateBy[A-Z].*/:
                                case ~/findOrSaveBy[A-Z].*/:
                                    dynamicCall = makeDynamicGormCall(call, staticMethodCallTargetType, staticMethodCallTargetType)
                                    break
                            }
                        }
                    }
                }
            }
            return dynamicCall
        }
        null
    }

    protected makeDynamicGormCall(MethodCall call, ClassNode returnTypeNode, ClassNode domainClassTypeNode) {
        def dynamicCall = makeDynamic(call, returnTypeNode)
        dynamicCall.modifiers = Modifier.STATIC
        dynamicCall.declaringClass = domainClassTypeNode
        dynamicCall
    }
}
