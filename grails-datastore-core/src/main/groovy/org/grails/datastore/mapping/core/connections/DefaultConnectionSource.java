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
