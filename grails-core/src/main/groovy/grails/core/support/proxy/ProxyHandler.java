/*
 * Copyright 2004-2008 the original author or authors.
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
package grails.core.support.proxy;

/**
 * Interface that defines logic for handling proxied instances
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
public interface ProxyHandler {

    /**
     * Returns true if the specified object is a proxy.
     * @param o The object in question
     * @return true if it is a proxy
     */
    boolean isProxy(Object o);

    /**
     * Returns the unwrapped proxy instance or the original object if not proxied.
     *
     * @param instance The instance to unwrap
     * @return The unwrapped instance
     */
    Object unwrapIfProxy(Object instance);

    /**
     * Returns whether a lazy proxied instance has been initialized.
     *
     * @param o The instance to test
     * @return true if it has been initialized false otherwise
     */
    boolean isInitialized(Object o);

    /**
     * Initializes an existing uninitialized proxy instance.
     * @param o The proxy instance
     */
    void initialize(Object o);

    /**
     * Tests whether an association of the given object has been initialized.
     * @param obj The object to check
     * @param associationName The association
     * @return true if has been init
     */
    boolean isInitialized(Object obj, String associationName);
}
