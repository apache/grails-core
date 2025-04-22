/* Copyright (C) 2010 SpringSource
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
package org.grails.datastore.mapping.transactions;

/**
 * Class giving the ability to start, commit and rollback a transaction.
 *
 * @author Guillaume Laforge
 * @author Graeme Rocher
 * @since 1.0
 *
 */
public interface Transaction<T> {

    /**
     * Commit the transaction.
     */
    void commit();

    /**
     * Rollback the transaction.
     */
    void rollback();

    /**
     * @return the native transaction object.
     */
    T getNativeTransaction();

    /**
     * Whether the transaction is active
     * @return True if it is
     */
    boolean isActive();

    /**
     * Sets the transaction timeout period
     * @param timeout The timeout
     */
    void setTimeout(int timeout);
}
