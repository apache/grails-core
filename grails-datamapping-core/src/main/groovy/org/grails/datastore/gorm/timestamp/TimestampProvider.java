/*******************************************************************************
 * Copyright 2014 original authors
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
 *******************************************************************************/
package org.grails.datastore.gorm.timestamp;

/**
 * Interface for implementations that create timestamps for GORM dateCreated/lastUpdated fields
 *
 */
public interface TimestampProvider {
    /**
     * Whether a timestamp can be created for the given type
     *
     * @param dateTimeClass The date time class
     *
     * @return True if it can
     */
    boolean supportsCreating(Class<?> dateTimeClass);

    /**
     * Creates a timestamp for the given class
     * @param dateTimeClass The time stamp
     * @param <T> The type of the timestamp class
     * @return An instance of the timestamp
     */
    <T> T createTimestamp(Class<T> dateTimeClass);
}
