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

import java.sql.SQLException;
import java.util.Iterator;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.PropertyProjection;
import org.hibernate.criterion.Restrictions;
import org.hibernate.internal.CriteriaImpl;

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
     * The number of groups of a grouped criteria. A projection that is one grouped property is counted by the database,
     * as the distinct values of the property plus the group of its null values, which SQL groups but does not count as a
     * value. Any other grouped projection is counted from its groups.
     */
    private int countGroups(Session session, CriteriaImpl impl, Projection projection) {
        Projection only = projection;
        if (projection instanceof ProjectionList && ((ProjectionList) projection).getLength() == 1) {
            only = ((ProjectionList) projection).getProjection(0);
        }
        if (only instanceof PropertyProjection && ((PropertyProjection) only).isGrouped()) {
            String property = ((PropertyProjection) only).getPropertyName();
            Criteria distinct = totalCriteria(session, impl);
            distinct.setProjection(Projections.countDistinct(property));
            int groups = ((Number) distinct.uniqueResult()).intValue();

            Criteria nulls = totalCriteria(session, impl);
            nulls.add(Restrictions.isNull(property));
            nulls.setProjection(Projections.rowCount());
            return ((Number) nulls.uniqueResult()).longValue() > 0 ? groups + 1 : groups;
        }
        Criteria grouped = totalCriteria(session, impl);
        grouped.setProjection(projection);
        return grouped.list().size();
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
