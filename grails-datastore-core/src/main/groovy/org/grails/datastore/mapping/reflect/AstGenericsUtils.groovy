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

package org.grails.datastore.mapping.reflect

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.tools.GenericsUtils

/**
 * Generics utilities
 */
@CompileStatic
class AstGenericsUtils extends GenericsUtils {

    /**
     * Resolves a single generic type from the given class node
     *
     * @param classNode The class node
     * @return The generic type
     */
    static ClassNode resolveSingleGenericType(ClassNode classNode) {
        if(classNode.isArray()) {
            return classNode.componentType.plainNodeReference
        }
        GenericsType[] genericsTypes = classNode.genericsTypes
        if(genericsTypes) {
            return genericsTypes[0].type.plainNodeReference
        }
        else {
            return ClassHelper.OBJECT_TYPE
        }
    }
}
