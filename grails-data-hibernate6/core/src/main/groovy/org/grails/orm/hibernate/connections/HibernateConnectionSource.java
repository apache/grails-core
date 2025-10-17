package org.grails.orm.hibernate.connections;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.hibernate.SessionFactory;

import javax.sql.DataSource;
import java.io.IOException;

/**
 *
 * Implements the {@link org.grails.datastore.mapping.core.connections.ConnectionSource} interface for Hibernate
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateConnectionSource extends DefaultConnectionSource<SessionFactory, HibernateConnectionSourceSettings> {

    protected final ConnectionSource<DataSource, DataSourceSettings>  dataSource;

    public HibernateConnectionSource(String name, SessionFactory sessionFactory, ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource, HibernateConnectionSourceSettings settings) {
        super(name, sessionFactory, settings);
        this.dataSource = dataSourceConnectionSource;
    }

    @Override
    public void close() throws IOException {
        super.close();
        try {
            SessionFactory sessionFactory = getSource();
            sessionFactory.close();
        } finally {
            if(dataSource != null) {
                dataSource.close();
            }
        }
    }

    /**
     * @return The underlying SQL {@link DataSource}
     */
    public DataSource getDataSource() {
        return dataSource.getSource();
    }
}
