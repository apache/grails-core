/*
 * Copyright 2017 the original author or authors.
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
package org.grails.datastore.mapping.services

import org.grails.datastore.mapping.core.Datastore

/**
 * Represents a service available exposed by the GORM {@link Datastore}
 *
 * @author Graeme Rocher
 * @since 6.1
 *
 * <T> The domain class type this service operates with
 */
trait Service<T> {

    /**
     * The datastore that this service is related to
     */
    Datastore datastore
}