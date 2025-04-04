/*
 * Copyright 2002-2016 the original author or authors.
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
package org.grails.orm.hibernate.support;

import org.grails.datastore.mapping.core.grailsversion.GrailsVersion;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.hibernate.*;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 *
 * Methods to deal with the differences in different Hibernate versions
 *
 * @author Graeme Rocher
 * @author Juergen Hoeller
 *
 * @since 6.0
 *
 */
public class HibernateVersionSupport {


    /**
     * Get the native Hibernate FlushMode, adapting between Hibernate 5.0/5.1 and 5.2+.
     * @param session the Hibernate Session to get the flush mode from
     * @return the FlushMode (never {@code null})
     * @since 4.3
     * @deprecated Previously used for Hibernate backwards, will be removed in a future release.
     */
    @Deprecated
    public static FlushMode getFlushMode(Session session) {
        return session.getHibernateFlushMode();
    }

    /**
     * Set the native Hibernate FlushMode, adapting between Hibernate 5.0/5.1 and 5.2+.
     * @param session the Hibernate Session to get the flush mode from
     * @since 4.3
     * @deprecated Previously used for Hibernate backwards, will be removed in a future release.
     */
    @Deprecated
    public static void setFlushMode(Session session, FlushMode flushMode) {
        session.setHibernateFlushMode(flushMode);
    }

    /**
     * Check the current hibernate version
     * @param required The required version
     * @return True if it is at least the given version
     */
    public static boolean isAtLeastVersion(String required) {
        String hibernateVersion = Hibernate.class.getPackage().getImplementationVersion();
        if (hibernateVersion != null) {
            return GrailsVersion.isAtLeast(hibernateVersion, required);
        } else {
            return false;
        }
    }

    /**
     * Creates a query
     *
     * @param session The session
     * @param query The query
     * @return The created query
     * @deprecated Previously used for Hibernate backwards, will be removed in a future release.
     */
    @Deprecated
    public static Query createQuery(Session session, String query) {
        return session.createQuery(query);
    }
}
