package org.grails.datastore.gorm.neo4j.proxy;

import javassist.util.proxy.MethodHandler;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.JavassistProxyFactory;

import java.io.Serializable;

/**
 * extends {@link org.grails.datastore.mapping.proxy.JavassistProxyFactory} to capture method calls `hashCode` and `equals`
 * without expanding the proxy
 */
public class Neo4jProxyFactory extends JavassistProxyFactory {

    protected <K extends Serializable, T> MethodHandler createMethodHandler(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        return new Neo4jAssociationQueryProxyHandler(session, executor, associationKey);
    }
}
