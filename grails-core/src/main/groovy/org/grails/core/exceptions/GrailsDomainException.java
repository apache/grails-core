/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.core.exceptions;

/**
 * Thrown when creation of the Grails domain from the Grails domain classes fails.
 *
 * @author Graeme Rocher
 */
public class GrailsDomainException extends GrailsException {

    private static final long serialVersionUID = -3824320541041888143L;

    public GrailsDomainException() {
        super();
    }

    public GrailsDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public GrailsDomainException(String message) {
        super(message);
    }

    public GrailsDomainException(Throwable cause) {
        super(cause);
    }
}
