/*
 * Copyright 2013 the original author or authors.
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
package org.grails.encoder;

/**
 * This interface marks an instance capable of getting a EncodedAppender
 * instance that is connected to it. For example a buffer provides it's
 * EncodedAppender instance with this interface.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface EncodedAppenderFactory {

    /**
     * Gets the EncodedAppender that is connected to the instance of this
     * implementation.
     *
     * @return the EncodedAppender
     */
    EncodedAppender getEncodedAppender();
}
