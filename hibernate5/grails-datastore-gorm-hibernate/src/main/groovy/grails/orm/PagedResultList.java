/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.orm;

import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.internal.CriteriaImpl;

import java.sql.SQLException;
import java.util.Iterator;

/**
 * A result list for Criteria list calls, which is aware of the totalCount for
 * the paged result.
 *
 * @author Siegfried Puchbauer
 * @since 1.0
 * @deprecated Use {@link org.grails.orm.hibernate.query.PagedResultList} instead.
 */
@SuppressWarnings({"unchecked","rawtypes"})
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
            totalCount = hibernateTemplate.execute(new GrailsHibernateTemplate.HibernateCallback<Integer>() {
                public Integer doInHibernate(Session session) throws HibernateException, SQLException {
                    CriteriaImpl impl = (CriteriaImpl) criteria;
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
                    totalCriteria.setProjection(impl.getProjection());
                    totalCriteria.setProjection(Projections.rowCount());
                    return ((Number)totalCriteria.uniqueResult()).intValue();
                }
            });
        }
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
