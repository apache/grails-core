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

/**
 * A trait with two declared generic type parameters, used only to exercise
 * {@link AbstractTraitApplyingGormASTTransformation#weaveTraitWithGenerics} in tests where only
 * some, but not all, of the declared generic type parameters are supplied with an argument -
 * so that both the "argument supplied" and "argument padded with Object" code paths run in the
 * same call.
 */
trait DoubleGenericTestTrait<A, B> {

    A firstValue(A value) {
        value
    }

    B secondValue(B value) {
        value
    }
}
