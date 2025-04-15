/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.util

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class EnvironmentBlockEvaluator extends GroovyObjectSupport {
    private Environment current;
    private Closure<?> callable;

    Closure<?> getCallable() {
        return callable;
    }

    Object execute() {
        return callable == null ? null : callable.call();
    }

    @PackageScope
    EnvironmentBlockEvaluator(Environment e) {
        current = e;
    }

    @SuppressWarnings("unused")
    void environments(Closure<?> c) {
        if (c != null) {
            c.setDelegate(this);
            c.call();
        }
    }
    @SuppressWarnings("unused")
    void production(Closure<?> c) {
        if (current == Environment.PRODUCTION) {
            callable = c;
        }
    }
    @SuppressWarnings("unused")
    void development(Closure<?> c) {
        if (current == Environment.DEVELOPMENT) {
            callable = c;
        }
    }
    @SuppressWarnings("unused")
    void test(Closure<?> c) {
        if (current == Environment.TEST) {
            callable = c;
        }
    }

    Object methodMissing(String name, Object args) {
        Object[] argsArray = (Object[])args;
        if (args != null && argsArray.length > 0 && (argsArray[0] instanceof Closure)) {
            if (current == Environment.CUSTOM && current.getName().equals(name)) {
                callable = (Closure<?>) argsArray[0];
            }
            return null;
        }
        throw new MissingMethodException(name, Environment.class, argsArray);
    }
}
