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

package org.grails.compiler.gorm

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

/**
 * Additional transformations applied to GORM entities
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface AdditionalGormEntityTransformation extends ASTTransformation, CompilationUnitAware, TransformWithPriority {

    /**
     * @return Whether the transformation is available
     */
    boolean isAvailable()

    /**
     * Visit the transform
     *
     * @param classNode The class node
     * @param sourceUnit The source unit
     */
    void visit(ClassNode classNode, SourceUnit sourceUnit)
}