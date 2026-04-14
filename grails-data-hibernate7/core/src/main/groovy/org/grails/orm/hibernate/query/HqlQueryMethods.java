package org.grails.orm.hibernate.query;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface HqlQueryMethods {

    Set<String> INTERNAL_SETTINGS = Set.of(
        HibernateQueryArgument.FLUSH_MODE.value(),
        HibernateQueryArgument.CACHE.value(),
        HibernateQueryArgument.TIMEOUT.value(),
        HibernateQueryArgument.READ_ONLY.value(),
        HibernateQueryArgument.FETCH_SIZE.value(),
        HibernateQueryArgument.MAX.value(),
        HibernateQueryArgument.OFFSET.value()
    );

    default void populateQuerySettings(HqlQueryDelegate d, Map<String, Object> args) {
        if (args == null || args.isEmpty()) return;
        if (args.containsKey(HibernateQueryArgument.FLUSH_MODE.value())) {
            d.setQueryFlushMode(GrailsQueryFlushMode.mapToHibernateQueryFlushMode(args.get(HibernateQueryArgument.FLUSH_MODE.value())));
        }
        if (args.containsKey(HibernateQueryArgument.MAX.value())) {
            d.setMaxResults((Integer) args.get(HibernateQueryArgument.MAX.value()));
        }
        if (args.containsKey(HibernateQueryArgument.OFFSET.value())) {
            d.setFirstResult((Integer) args.get(HibernateQueryArgument.OFFSET.value()));
        }
        if (args.containsKey(HibernateQueryArgument.READ_ONLY.value())) {
            d.setReadOnly((Boolean) args.get(HibernateQueryArgument.READ_ONLY.value()));
        }
    }

    static void populateParameters(HqlQueryDelegate d,HqlQueryContext queryContext) {
        if (queryContext.namedParams() != null && !queryContext.namedParams().isEmpty()) {
            queryContext.namedParams().forEach((key, value) -> {
                if (INTERNAL_SETTINGS.contains(key)) return;
                Object val = convertValue(value);
                if (val instanceof Collection) {
                    d.setParameterList(key, (Collection<?>) val);
                } else if (val != null && val.getClass().isArray()) {
                    d.setParameterList(key, (Object[]) val);
                } else {
                    d.setParameter(key, val);
                }
            });
        } else if (queryContext.positionalParams() != null && !queryContext.positionalParams().isEmpty()) {
            for (int i = 0; i < queryContext.positionalParams().size(); i++) {
                Object val = convertValue(queryContext.positionalParams().get(i));
                d.setParameter(i + 1, val);
            }
        }
    }

    static Object convertValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Collection<?> coll) {
            List<Object> newList = new ArrayList<>(coll.size());
            for (Object o : coll) {
                newList.add(convertValue(o));
            }
            return newList;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object newArray = Array.newInstance(value.getClass().getComponentType() == CharSequence.class || CharSequence.class.isAssignableFrom(value.getClass().getComponentType()) ? String.class : value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(newArray, i, convertValue(Array.get(value, i)));
            }
            return newArray;
        }
        return value;
    }
}
