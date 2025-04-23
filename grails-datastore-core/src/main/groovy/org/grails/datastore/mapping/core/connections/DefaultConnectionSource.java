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

package org.grails.datastore.mapping.core.connections;

import java.io.Closeable;
import java.io.IOException;

/**
 * Abstract implementation of the {@link ConnectionSource} interface
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class DefaultConnectionSource<T, S extends ConnectionSourceSettings> implements ConnectionSource<T, S> {

    protected final String name;
    protected final T source;
    protected final S settings;
    protected boolean closed = false;

    public DefaultConnectionSource(String name, T source, S settings) {
        this.name = name;
        this.source = source;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public T getSource() {
        return this.source;
    }

    @Override
    public S getSettings() {
        return this.settings;
    }

    @Override
    public void close() throws IOException {
        if(source instanceof Closeable) {
            try {
                ((Closeable)source).close();
            } finally {
                this.closed = true;
            }
        }
        else if(source instanceof AutoCloseable) {
            try {
                ((AutoCloseable)source).close();
            } catch (Exception e) {
                throw new IOException("Error closing connection source ["+name+"]:" + e.getMessage(), e);
            }
            finally {
                this.closed = true;
            }
        }
    }
}
