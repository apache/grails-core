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
package grails.orm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.loader.criteria.CriteriaLoader;
import org.hibernate.loader.spi.AfterLoadAction;
import org.hibernate.persister.entity.OuterJoinLoadable;
import org.hibernate.transform.ResultTransformer;

import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.query.HibernateQuery;

/**
 * A result list for Criteria list calls, which is aware of the totalCount for
 * the paged result.
 *
 * @author Siegfried Puchbauer
 * @since 1.0
 * @deprecated Use {@link org.grails.orm.hibernate.query.PagedResultList} instead.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Deprecated
public class PagedResultList extends grails.gorm.PagedResultList {

    private transient GrailsHibernateTemplate hibernateTemplate;
    private final Criteria criteria;

    public PagedResultList(GrailsHibernateTemplate template, Criteria crit) {
        super(null);
        resultList = crit.list();
        criteria = crit;
        hibernateTemplate = template;
    }

    public PagedResultList(GrailsHibernateTemplate template, HibernateQuery query) {
        super(null);
        resultList = query.listForCriteria();
        criteria = query.getHibernateCriteria();
        hibernateTemplate = template;
    }

    @Override
    protected void initialize() {
        // no-op, already initialized
    }

    @Override
    public int getTotalCount() {
        if (totalCount == Integer.MIN_VALUE) {
            totalCount = hibernateTemplate.execute(new GrailsHibernateTemplate.HibernateCallback<>() {
                public Integer doInHibernate(Session session) throws HibernateException, SQLException {
                    CriteriaImpl impl = (CriteriaImpl) criteria;
                    Projection projection = impl.getProjection();
                    if (projection != null && projection.isGrouped()) {
                        return countGroups(session, impl, projection);
                    }
                    Criteria totalCriteria = totalCriteria(session, impl);
                    totalCriteria.setProjection(Projections.rowCount());
                    return ((Number) totalCriteria.uniqueResult()).intValue();
                }
            });
        }
        return totalCount;
    }

    private Criteria totalCriteria(Session session, CriteriaImpl impl) {
        Criteria totalCriteria = session.createCriteria(impl.getEntityOrClassName());
        hibernateTemplate.applySettings(totalCriteria);

        Iterator iterator = impl.iterateExpressionEntries();
        while (iterator.hasNext()) {
            CriteriaImpl.CriterionEntry entry = (CriteriaImpl.CriterionEntry) iterator.next();
            totalCriteria.add(entry.getCriterion());
        }
        Iterator subcriteriaIterator = impl.iterateSubcriteria();
        while (subcriteriaIterator.hasNext()) {
            CriteriaImpl.Subcriteria sub = (CriteriaImpl.Subcriteria) subcriteriaIterator.next();
            totalCriteria.createAlias(sub.getPath(), sub.getAlias(), sub.getJoinType(), sub.getWithClause());
        }
        return totalCriteria;
    }

    /**
     * The number of groups of a grouped criteria, counted by the database as the rows of the grouped query, so a group
     * whose values are all null is one group, whatever the number of columns of the grouped properties.
     */
    private int countGroups(Session session, CriteriaImpl impl, Projection projection) {
        CriteriaImpl grouped = (CriteriaImpl) totalCriteria(session, impl);
        grouped.setProjection(projection);
        SessionImplementor implementor = (SessionImplementor) session;
        SessionFactoryImplementor factory = implementor.getFactory();
        String rootName = grouped.getEntityOrClassName();
        OuterJoinLoadable persister = (OuterJoinLoadable) factory.getMetamodel().entityPersister(rootName);
        List<?> counted = new GroupCountLoader(persister, factory, grouped, rootName, implementor.getLoadQueryInfluencers())
                .list(implementor);
        return ((Number) counted.get(0)).intValue();
    }

    /**
     * Runs the grouped query that Hibernate builds for a criteria, inside <code>select count(*) from (...)</code>, so the
     * database counts the groups. Hibernate binds the parameters of the query as it does for
     * the query itself.
     */
    private static final class GroupCountLoader extends CriteriaLoader {

        GroupCountLoader(OuterJoinLoadable persister, SessionFactoryImplementor factory, CriteriaImpl criteria, String rootEntityName,
                         LoadQueryInfluencers loadQueryInfluencers) {
            super(persister, factory, criteria, rootEntityName, loadQueryInfluencers);
        }

        @Override
        protected String preprocessSQL(String sql, QueryParameters parameters, SessionFactoryImplementor factory,
                                       List<AfterLoadAction> afterLoadActions) {
            return "select count(*) from (" + super.preprocessSQL(sql, parameters, factory, afterLoadActions) + ") grouped_rows";
        }

        @Override
        protected Object[] getResultRow(Object[] row, ResultSet rs, SharedSessionContractImplementor session) throws SQLException {
            return new Object[] {rs.getLong(1)};
        }

        @Override
        protected Object getResultColumnOrRow(Object[] row, ResultTransformer transformer, ResultSet rs,
                                              SharedSessionContractImplementor session) throws SQLException {
            return rs.getLong(1);
        }
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
