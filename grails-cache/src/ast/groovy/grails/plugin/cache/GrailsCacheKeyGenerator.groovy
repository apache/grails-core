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
package grails.plugin.cache

/**
 * Generates a cache key for the given arguments
 *
 * @since 4.0.0
 * @author Jeff Brown
 * @author Graeme Rocher
 */
interface GrailsCacheKeyGenerator {
    /**
     *
     * @param className The name of the class
     * @param methodName The name of the object
     * @param objHashCode The hash code of the instance
     * @param keyGenerator A closure that generates the key
     * @return The generated key
     */
    Serializable generate(String className, String methodName, int objHashCode, Closure keyGenerator)

    /**
     *
     * @param className The name of the class
     * @param methodName The name of the object
     * @param objHashCode The hash code of the instance
     * @param methodParams The parameters to the method as a map of parameter name to value
     * @return The generated key
     */
    Serializable generate(String className, String methodName, int objHashCode, Map methodParams)
}
