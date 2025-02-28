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
package grails.async

import java.util.concurrent.Future

/**
 * Encapsulates the notion of a Promise, a Future-like interface designed to easy integration of asynchronous functions
 *
 * @author Graeme Rocher
 * @since 2.3
 */
interface Promise<T> extends Future<T> {

    /**
     * Assigns a value to an unfulfilled promise
     *
     * @param value The value
     */
    Promise<T> accept(T value)

    /**
     * Execute the given closure when the promise completes
     *
     * @param callable
     * @return The Promise
     */
    Promise<T> onComplete(Closure<T> callable)

    /**
     * Execute the given closure when an error occurs
     *
     * @param callable
     * @return The Promise
     */
    Promise<T> onError(Closure<T> callable)

    /**
     * Same as #onComplete
     */
    Promise<T> then(Closure<T> callable)
}
