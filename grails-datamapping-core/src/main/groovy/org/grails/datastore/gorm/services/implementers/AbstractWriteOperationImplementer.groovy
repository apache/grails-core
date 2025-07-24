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

package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

import org.grails.datastore.gorm.services.ServiceEnhancer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform

/**
 * Abstract implementer for write operations
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractWriteOperationImplementer extends AbstractServiceImplementer implements ServiceEnhancer {
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
        if(!TransactionalTransform.hasTransactionalAnnotation(newMethodNode)) {
            // read-only transaction by default
            newMethodNode.addAnnotation( new AnnotationNode(TransactionalTransform.MY_TYPE) )
        }

        doImplement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)
        abstractMethodNode.putNodeMetaData(IMPLEMENTED, Boolean.TRUE)
    }

    @Override
    boolean doesEnhance(ClassNode domainClass, MethodNode methodNode) {
        return doesImplement(domainClass, methodNode)
    }

    @Override
    void enhance(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        if(!TransactionalTransform.hasTransactionalAnnotation(newMethodNode)) {
            // read-only transaction by default
            newMethodNode.addAnnotation( new AnnotationNode(TransactionalTransform.MY_TYPE) )
        }
    }
}
