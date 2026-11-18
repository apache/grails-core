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
 * An {@link ASTTransformation} whose no-arg constructor always throws, used only to exercise the
 * branch of {@link OrderedGormTransformation#collectAndOrderGormTransformations} where the
 * transform class loads but cannot be instantiated: the constructor's exception arrives wrapped in
 * an {@code InvocationTargetException} and its message must still reach the compile error.
 *
 * @see OrderedGormTransformationSpec
 */
class ThrowingConstructorTestTransformation implements ASTTransformation {

    static final String CONSTRUCTOR_MESSAGE = 'transform constructor exploded'

    ThrowingConstructorTestTransformation() {
        throw new IllegalStateException(CONSTRUCTOR_MESSAGE)
    }

    @Override
    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
    }
}
