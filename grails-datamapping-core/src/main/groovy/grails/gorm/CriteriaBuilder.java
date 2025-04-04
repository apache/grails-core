/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.gorm;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.grails.datastore.gorm.query.criteria.AbstractCriteriaBuilder;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.QueryCreator;
import org.grails.datastore.mapping.query.api.BuildableCriteria;
import org.grails.datastore.mapping.query.api.Criteria;
import org.grails.datastore.mapping.query.api.ProjectionList;

import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.grails.datastore.gorm.finders.DynamicFinder.populateArgumentsForCriteria;

/**
 * Criteria builder implementation that operates against DataStore abstraction.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class CriteriaBuilder<T> extends AbstractCriteriaBuilder implements BuildableCriteria, ProjectionList {

    public static final String ORDER_DESCENDING = "desc";
    public static final String ORDER_ASCENDING = "asc";

    protected final Session session;

    public CriteriaBuilder(final Class targetClass, QueryCreator queryCreator, final MappingContext mappingContext) {
        super(targetClass, queryCreator, mappingContext);
        this.session = null;
    }

    public CriteriaBuilder(final Class targetClass, final Session session) {
        super(targetClass, session, session.getMappingContext());
        this.session = session;

    }

    public CriteriaBuilder(final Class targetClass, final Session session, final Query query) {
        this(targetClass, session);
        this.query = query;
    }

    @Override
    public BuildableCriteria cache(boolean cache) {
        query.cache(cache);
        return this;
    }

    @Override
    public BuildableCriteria readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    @Override
    public BuildableCriteria join(String property) {
        query.join(property);
        return this;
    }

    @Override
    public BuildableCriteria join(String property, JoinType joinType) {
        query.join(property, joinType);
        return this;
    }

    @Override
    public BuildableCriteria select(String property) {
        query.select(property);
        return this;
    }

    /**
     * Defines and executes a list query in a single call. Example: Foo.createCriteria.list { }
     * @param callable The closure to execute
     *
     * @return The result list
     */
    public List list(Closure callable) {
        ensureQueryIsInitialized();
        invokeClosureNode(callable);

        return query.list();
    }

    /**
     * Defines and executes a get query (a single result) in a single call. Example: Foo.createCriteria.get { }
     *
     *
     * @param callable The closure to execute
     *
     * @return A single result
     */
    public Object get(Closure callable) {
        ensureQueryIsInitialized();
        invokeClosureNode(callable);

        uniqueResult = true;
        return query.singleResult();
    }

    /**
     * Defines and executes a list distinct query in a single call. Example: Foo.createCriteria.listDistinct { }
     * @param callable The closure to execute
     *
     * @return The result list
     */
    public List listDistinct(Closure callable) {
        ensureQueryIsInitialized();
        invokeClosureNode(callable);

        query.projections().distinct();
        return query.list();
    }

    public List list(Map paginateParams, Closure callable) {
        ensureQueryIsInitialized();

        paginationEnabledList = true;
        orderEntries = new ArrayList<>();
        invokeClosureNode(callable);
        populateArgumentsForCriteria(targetClass, query, paginateParams);
        for (Query.Order orderEntry : orderEntries) {
            query.order(orderEntry);
        }
        return new PagedResultList(query);
    }

    /**
     * Defines and executes a count query in a single call. Example: Foo.createCriteria.count { }
     * @param callable The closure to execute
     *
     * @return The result count
     */
    public Number count(Closure callable) {
        ensureQueryIsInitialized();
        invokeClosureNode(callable);
        uniqueResult = true;
        query.projections().count();
        return (Number) query.singleResult();
    }
    
    @Override
    public Object scroll(@DelegatesTo(Criteria.class) Closure c) {
        return invokeMethod(SCROLL_CALL, new Object[]{c});
    }


}
