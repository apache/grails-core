/*
 * Copyright 2013 SpringSource
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
package grails.async.decorator

/**
 * Decorates any function execution potentially wrapping an asynchronous function execution in new functionality.
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@FunctionalInterface
interface PromiseDecorator {

    /**
     * Decorates the given closures, returning the decorated closure
     *
     * @param c The closure to decorate
     * @return The decorated closure
     */
    <D> Closure<D> decorate(Closure<D> c)
}
