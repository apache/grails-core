/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.grails.datastore.gorm.services.ServiceEnhancer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.reflect.AstUtils

import java.lang.reflect.Modifier

/**
 * Abstract implementor for read operations
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractReadOperationImplementer extends AbstractServiceImplementer implements ServiceEnhancer {

    /**
     * Subclasses should override to add the logic that implements the method
     *
     * @param targetClassNode The target class
     * @param abstractMethodNode The abstract method
     * @param newMethodNode The newly added method
     */
    abstract void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode)

    @Override
    final void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        // copy any annotations from the abstract method
        copyClassAnnotations(abstractMethodNode, newMethodNode)
        if(!TransactionalTransform.hasTransactionalAnnotation(targetClassNode) && !TransactionalTransform.hasTransactionalAnnotation(newMethodNode) && Modifier.isPublic(newMethodNode.modifiers)) {
            // read-only transaction by default
            applyDefaultTransactionHandling(newMethodNode)
        }

        ClassNode domainClassFromSignature = resolveDomainClassFromSignature(domainClassNode, abstractMethodNode)
        if(domainClassFromSignature != null && AstUtils.isDomainClass(domainClassFromSignature)) {
            domainClassNode = domainClassFromSignature
        }
        doImplement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)
        abstractMethodNode.putNodeMetaData(IMPLEMENTED, Boolean.TRUE)
    }

    /**
     * Subclasses can override to provide resolution of the domain class from the method signature
     *
     * @param currentDomainClassNode The current defined domain class node
     * @param methodNode The method node
     * @return The resolved domain class
     */
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    protected void applyDefaultTransactionHandling(MethodNode newMethodNode) {
        newMethodNode.addAnnotation(new AnnotationNode(TransactionalTransform.READ_ONLY_TYPE))
    }

    @Override
    boolean doesEnhance(ClassNode domainClass, MethodNode methodNode) {
        return doesImplement(domainClass, methodNode)
    }

    @Override
    void enhance(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        if(!TransactionalTransform.hasTransactionalAnnotation(newMethodNode) && Modifier.isPublic(newMethodNode.modifiers)) {
            // read-only transaction by default
            applyDefaultTransactionHandling(newMethodNode)
        }
    }

    protected Expression findArgsExpression(MethodNode newMethodNode) {
        Expression argsExpression = null
        for (parameter in newMethodNode.parameters) {
            if (parameter.name == 'args' && parameter.type == ClassHelper.MAP_TYPE) {
                argsExpression = GeneralUtils.varX(parameter)
            }
        }
        argsExpression
    }

}
