package org.grails.orm.hibernate.query;

import java.util.List;

import org.grails.datastore.mapping.query.Query;
import org.grails.orm.hibernate.GrailsHibernateTemplate;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

public class SelectHqlQuery extends Query implements HqlQueryMethods {
    protected final HqlQueryContext queryContext;
    protected final HqlQueryDelegate delegate;

    protected SelectHqlQuery(HibernateSession session, GrailsHibernatePersistentEntity entity, HqlQueryContext queryContext, HqlQueryDelegate delegate) {
        super(session, entity);
        this.queryContext = queryContext;
        this.delegate = delegate;
    }

    @Override
    public List<?> list() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(__ -> {
            applyQuerySettings(delegate);
            return delegate.list();
        });
    }

    @Override
    public Object singleResult() {
        GrailsHibernateTemplate template = (GrailsHibernateTemplate) getHibernateTemplate();
        return template.execute(__ -> {
            applyQuerySettings(delegate);
            List<?> results = delegate.list();
            return results.isEmpty() ? null : results.getFirst();
        });
    }

    protected void applyQuerySettings(HqlQueryDelegate d) {
        if (max != null && max > -1) {
            d.setMaxResults(max);
        }
        if (offset != null && offset > -1) {
            d.setFirstResult(offset);
        }
        populateQuerySettings(d, queryContext.querySettings());
        populateHints(d, queryContext.hints());
        HqlQueryMethods.populateParameters(d, queryContext);
    }

    public int executeUpdate() {
        throw new UnsupportedOperationException("SELECT query cannot be used for executeUpdate(); use a MutationHqlQuery instead");
    }

    protected IHibernateTemplate getHibernateTemplate() {
        HibernateSession hibernateSession = (HibernateSession) getSession();
        return (IHibernateTemplate) hibernateSession.getNativeInterface();
    }

    @Override
    protected List<?> executeQuery(org.grails.datastore.mapping.model.PersistentEntity entity, Junction criteria) {
        return list();
    }

    public void setReadOnly(Boolean ignoredReadOnly) {
        // Compatibility method
    }

    public org.hibernate.query.Query<?> selectQuery() {
        return delegate.selectQuery();
    }
}
