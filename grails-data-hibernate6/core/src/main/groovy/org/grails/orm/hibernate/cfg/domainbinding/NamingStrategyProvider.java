package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;

import java.util.concurrent.ConcurrentHashMap;

public class NamingStrategyProvider {
    private final ConcurrentHashMap<String, PhysicalNamingStrategy> physicalProviderMap;

    public NamingStrategyProvider() {
        physicalProviderMap = new ConcurrentHashMap<>();
        physicalProviderMap.put(ConnectionSource.DEFAULT, new CamelCaseToUnderscoresNamingStrategy());
    }

    /**
     * Configures the naming strategy for a given datasource.
     *
     * @param datasourceName the datasource name
     * @param strategy the naming strategy (instance, Class, or class name)
     * @throws ClassNotFoundException when the strategy class cannot be found
     * @throws IllegalAccessException when the strategy class cannot be accessed
     * @throws InstantiationException when the strategy class cannot be instantiated
     */
    public void configureNamingStrategy(final String datasourceName, final Object strategy)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {

        if (strategy == null) {
            throw new IllegalArgumentException("Naming strategy cannot be null");
        }

        var strategyClass = getStrategyClass(strategy);
        var strategyInstance = getStrategyInstance(strategy, strategyClass);

        if (strategyInstance instanceof PhysicalNamingStrategy physicalStrategy) {
            physicalProviderMap.put(datasourceName, physicalStrategy);
        } else {
           physicalProviderMap.put(datasourceName, new CamelCaseToUnderscoresNamingStrategy());
        }
    }

    private Class<?> getStrategyClass(Object strategy) throws ClassNotFoundException {
        if (strategy instanceof Class<?>) {
            return (Class<?>) strategy;
        }
        if (strategy instanceof CharSequence) {
            return Thread.currentThread().getContextClassLoader().loadClass(strategy.toString());
        }
        return strategy.getClass();
    }

    private Object getStrategyInstance(Object strategy, Class<?> strategyClass)
            throws InstantiationException, IllegalAccessException {
        if (strategy instanceof PhysicalNamingStrategy) {
            return strategy;
        }
        return strategyClass.newInstance();
    }

    public PhysicalNamingStrategy getPhysicalNamingStrategy(String sessionFactoryBeanName) {
        String key = getKey(sessionFactoryBeanName);
        physicalProviderMap.putIfAbsent(key, new CamelCaseToUnderscoresNamingStrategy());
        return physicalProviderMap.get(key);
    }


    private static String getKey(String sessionFactoryBeanName) {
        String key = "sessionFactory".equals(sessionFactoryBeanName) ?
                ConnectionSource.DEFAULT :
                sessionFactoryBeanName.substring("sessionFactory_".length());
        return key;
    }

}
