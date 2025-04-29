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
package org.grails.datastore.rx.proxy;

import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.exceptions.BlockingOperationException;
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor;
import org.grails.datastore.rx.query.QueryState;
import org.grails.datastore.rx.query.RxQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.io.Serializable;

/**
 * A proxy {@link javassist.util.proxy.MethodHandler} that uses a query to resolve the target using a query
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class QueryObservableProxyMethodHandler extends AbstractObservableProxyMethodHandler {
    private static final Logger LOG = LoggerFactory.getLogger(IdQueryObservableProxyMethodHandler.class);

    private final Query query;
    protected final Observable observable;
    protected Serializable proxyKey;

    public QueryObservableProxyMethodHandler(Class proxyClass, Query query, QueryState queryState, RxDatastoreClient client) {
        super(proxyClass, query.getEntity().getJavaClass(), queryState, client);
        this.query = query;
        this.observable = resolveObservable();
    }

    @Override
    protected Observable resolveObservable() {
        query.projections().id();
        Observable queryResult = ((RxQuery) query).singleResult();

        return queryResult.map(new Func1() {
            @Override
            public Object call(Object o) {
                target = o;
                return o;
            }
        });
    }

    @Override
    protected Object resolveDelegate(Object self) {
        if(target != null) {
            return target;
        }

        if(((RxDatastoreClientImplementor)client).isAllowBlockingOperations()) {
            if(LOG.isWarnEnabled()) {
                LOG.warn("Entity of type [{}] lazy loaded using a blocking operation. Consider using ObservableProxy.subscribe(..) instead", type.getName());
            }
            this.target = observable.toBlocking().first();
        }
        else {
            throw new BlockingOperationException("Cannot initialize proxy for class ["+type+"] using a blocking operation. Use ObservableProxy.subscribe(..) instead.");
        }
        return this.target;
    }

    @Override
    protected Object getProxyKey(Object self) {
        resolveDelegate(self);
        return proxyKey;
    }
}
