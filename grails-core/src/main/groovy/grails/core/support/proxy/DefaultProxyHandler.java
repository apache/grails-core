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
 * Trivial default implementation that always returns true and the object.
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
public class DefaultProxyHandler implements ProxyHandler {

    public boolean isInitialized(Object o) {
        return true;
    }

    public boolean isInitialized(Object obj, String associationName) {
        return true;
    }

    public Object unwrapIfProxy(Object instance) {
        return instance;
    }

    public boolean isProxy(Object o) {
        return false;
    }

    public void initialize(Object o) {
        // do nothing
    }
}
