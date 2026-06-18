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
package org.grails.orm.hibernate;

import org.hibernate.FlushMode;

import org.grails.orm.hibernate.GrailsHibernateTemplate.HibernateCallback;

/**
 * Workaround for VerifyErrors in Groovy when using a HibernateCallback.
 *
 * @author Burt Beckwith
 */
public class InstanceApiHelper {

    protected GrailsHibernateTemplate hibernateTemplate;

    public InstanceApiHelper(final GrailsHibernateTemplate hibernateTemplate) {
        this.hibernateTemplate = hibernateTemplate;
    }

    public void delete(final Object obj, final boolean flush) {
        hibernateTemplate.execute((HibernateCallback<Void>) session -> {
            session.delete(obj);
            if (flush) {
                session.flush();
            }
            return null;
        });
    }

    public void setFlushModeManual() {
        hibernateTemplate.execute((HibernateCallback<Void>) session -> {
            session.setHibernateFlushMode(FlushMode.MANUAL);
            return null;
        });
    }
}
