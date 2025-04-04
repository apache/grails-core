package org.grails.datastore.gorm.mongo;

import java.util.Collection;
import java.util.List;

import org.grails.datastore.gorm.finders.MethodExpression;
import org.grails.datastore.mapping.mongo.query.MongoQuery;
import org.grails.datastore.mapping.query.Query.Criterion;
import org.springframework.util.Assert;

/**
 * Dynamic finder expression for within polygon queries
 *
 * @author Sergei Shushkevich
 */
public class WithinPolygon extends MethodExpression {

    public WithinPolygon(Class<?> targetClass, String propertyName) {
        super(targetClass, propertyName);
    }

    @Override
    public Criterion createCriterion() {
        return new MongoQuery.WithinPolygon(propertyName, (List<?>) arguments[0]);
    }

    @Override
    public void setArguments(Object[] arguments) {
        Assert.isTrue(arguments.length > 0 && arguments[0] instanceof List,
            "Only a list of elements is supported in a 'withinPolygon' query");

        Collection<?> argument = (Collection<?>) arguments[0];
        Assert.isTrue(argument.size() == 2,
            "A 'withinPolygon' query requires a two dimensional list of values");

        super.setArguments(arguments);
    }
}
