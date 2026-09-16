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
package org.grails.datastore.gorm.transform

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation

/**
 * A minimal {@link ASTTransformation} that deliberately does NOT implement
 * {@code groovy.transform.CompilationUnitAware}, used only to exercise the branch of
 * {@link OrderedGormTransformation#collectAndOrderGormTransformations} taken for a discovered
 * transform that isn't compilation-unit-aware (every other GORM transform in this codebase is,
 * via {@link AbstractGormASTTransformation}, so that branch is otherwise unexercised).
 *
 * @see OrderedGormTransformationSpec
 */
class NonCompilationUnitAwareTestTransformation implements ASTTransformation {

    @Override
    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
    }
}
