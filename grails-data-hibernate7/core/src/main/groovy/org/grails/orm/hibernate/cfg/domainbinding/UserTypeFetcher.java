package org.grails.orm.hibernate.cfg.domainbinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class UserTypeFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(UserTypeFetcher.class);


    public UserTypeFetcher() {
    }

    public Class<?> getUserType(PersistentProperty currentGrailsProp) {
        Class<?> userType = null;
        PropertyConfig config = ((GrailsHibernatePersistentProperty) currentGrailsProp).getMappedForm();
        Object typeObj = config.getType();
        if (typeObj instanceof Class<?>) {
            userType = (Class<?>)typeObj;
        } else if (typeObj != null) {
            String typeName = typeObj.toString();
            try {
                userType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                // only print a warning if the user type is in a package this excludes basic
                // types like string, int etc.
                if (typeName.indexOf(".")>-1) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("UserType not found ", e);
                    }
                }
            }
        }
        return userType;
    }
}
