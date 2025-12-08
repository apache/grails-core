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
package org.grails.orm.hibernate.exceptions;

/**
 * Thrown when no Hibernate dialect could be found for a database name.
 *
 * @author Steven Devijver
 */
public class CouldNotDetermineHibernateDialectException extends GrailsHibernateException {

    private static final long serialVersionUID = -3385252525996110909L;

    public CouldNotDetermineHibernateDialectException(String message) {
        super(message);
    }

    public CouldNotDetermineHibernateDialectException(String message, Throwable cause) {
        super(message, cause);
    }

}