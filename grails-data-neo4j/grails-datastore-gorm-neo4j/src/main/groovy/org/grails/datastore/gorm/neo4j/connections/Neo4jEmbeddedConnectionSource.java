package org.grails.datastore.gorm.neo4j.connections;

import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;
import org.neo4j.driver.Driver;
import org.neo4j.harness.ServerControls;

import java.io.IOException;

/**
 * A {@link org.grails.datastore.mapping.core.connections.ConnectionSource} for embedded Neo4j
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class Neo4jEmbeddedConnectionSource extends DefaultConnectionSource<Driver, Neo4jConnectionSourceSettings> {
    protected final ServerControls serverControls;

    Neo4jEmbeddedConnectionSource(String name, Driver source, Neo4jConnectionSourceSettings settings, ServerControls serverControls) {
        super(name, source, settings);
        this.serverControls = serverControls;
    }

    public ServerControls getServerControls() {
        return serverControls;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            serverControls.close();
        }
    }
}
