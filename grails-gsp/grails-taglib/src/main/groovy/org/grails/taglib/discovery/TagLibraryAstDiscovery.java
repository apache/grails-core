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
package org.grails.taglib.discovery;

import java.util.Set;

import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;

/**
 * Reads a tag library's namespace and tag names from its syntax tree.
 *
 * <p>Classification is delegated to {@link TagDiscoveryRules}, the same rules an application applies
 * when it registers tag libraries, so the two cannot disagree about what a tag is. What remains here
 * is reading the namespace and gathering the candidate members from the tree.
 *
 * @since 8.0.0
 */
public final class TagLibraryAstDiscovery {

    public static final String DEFAULT_NAMESPACE = "g";

    private static final String NAMESPACE_FIELD = "namespace";

    private static final ClassNode CLOSURE_TYPE = ClassHelper.make(Closure.class);

    private TagLibraryAstDiscovery() {
    }

    /**
     * Resolves the namespace the way {@code DefaultGrailsTagLibClass} does at runtime, which reads the
     * static {@code namespace} property through the class hierarchy.
     *
     * @param classNode the tag library
     * @return the namespace, or {@code null} when it cannot be determined without running the code, in
     *         which case no descriptor should be written and the tag library resolves dynamically
     */
    public static String resolveNamespace(ClassNode classNode) {
        for (ClassNode current = classNode; current != null && !ClassHelper.isObjectType(current);
                current = current.getSuperClass()) {
            FieldNode namespaceField = current.getDeclaredField(NAMESPACE_FIELD);
            if (namespaceField == null || !namespaceField.isStatic()) {
                continue;
            }
            Expression initial = namespaceField.getInitialExpression();
            if (initial instanceof ConstantExpression constant && constant.getValue() != null) {
                String value = constant.getValue().toString().trim();
                return value.isEmpty() ? DEFAULT_NAMESPACE : value;
            }
            // Declared, but its value is only known once the initialiser runs - a reference to a shared
            // constant, a concatenation, and so on. Guessing "g" here would file the tags under the
            // wrong namespace, so the tag library is left out of the index entirely.
            return null;
        }
        return DEFAULT_NAMESPACE;
    }

    /**
     * @param classNode the tag library
     * @param parameterNamesRetained whether this compilation writes parameter names into the class file
     * @return every tag name the library declares
     */
    public static Set<String> findTags(ClassNode classNode,
            boolean parameterNamesRetained) {
        return TagDiscoveryRules.findTags(new AstTagLibraryView(classNode, parameterNamesRetained));
    }

}
