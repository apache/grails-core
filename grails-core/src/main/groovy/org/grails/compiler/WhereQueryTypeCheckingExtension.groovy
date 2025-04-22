/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.compiler

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.grails.compiler.injection.GrailsASTUtils
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL


/**
 *
 * @since 2.4.1
 */
class WhereQueryTypeCheckingExtension extends TypeCheckingDSL {

    @Override
    public Object run() {
        setup { newScope() }

        finish { scopeExit() }
        
        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            def dynamicCall
            if(currentScope.processingWhereQueryClosure) {
                dynamicCall = makeDynamic (call)
            }
            dynamicCall
        }
        
        afterMethodCall { MethodCall call ->
            if(isWhereQueryCall(call)) {
                scopeExit()
            }
        }
        
        beforeMethodCall { MethodCall call ->
            if(isWhereQueryCall(call)) {
                newScope {
                    processingWhereQueryClosure = true
                }
            }
        }
        null
    }
    
    protected boolean isWhereQueryCall(MethodCall call) {
        call instanceof MethodCallExpression && 
            call.objectExpression instanceof ClassExpression && 
            GrailsASTUtils.isDomainClass(call.objectExpression.type, null) && 
            call.method.value == 'where'
    }
}
