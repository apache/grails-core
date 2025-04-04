/*
 * Copyright 2015 original authors
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

package org.grails.compiler.gorm


/**
 * Provides the implementation to use for the GormEntity trait
 *
 * @author Graeme Rocher
 * @since 5.0
 */
interface GormEntityTraitProvider {

    Class getEntityTrait()

    /**
     * @return Whether this trait provided is available
     */
    boolean isAvailable()
}