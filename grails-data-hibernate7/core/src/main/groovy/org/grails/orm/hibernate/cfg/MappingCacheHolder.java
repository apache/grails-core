package org.grails.orm.hibernate.cfg;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Holder for the GORM mapping cache.
 */
public class MappingCacheHolder {

    private static final MappingCacheHolder INSTANCE = new MappingCacheHolder();

    private MappingCacheHolder() {}

    public static MappingCacheHolder getInstance() {
        return INSTANCE;
    }

    private final Map<Class<?>, Mapping> MAPPING_CACHE = new HashMap<>();


    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param theClass The domain class in question
     * @return A Mapping object or null
     */
    public Mapping getMapping(Class<?> theClass) {
        return theClass == null ? null : MAPPING_CACHE.get(theClass);
    }

    public void cacheMapping(Class<?> theClass, Mapping mapping) {
        MAPPING_CACHE.put(theClass, mapping);
    }

    public void clear() {
        MAPPING_CACHE.clear();
    }

    public void clear(Class<?> theClass) {
        String className = theClass.getName();
        for (Iterator<Map.Entry<Class<?>, Mapping>> it = MAPPING_CACHE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Class<?>, Mapping> entry = it.next();
            if (className.equals(entry.getKey().getName())) {
                it.remove();
            }
        }
    }
}
