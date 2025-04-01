# GORM for Hibernate 6
This project implements [GORM](https://gorm.grails.org) for the Hibernate 6.

With the removal of Criterion API in Hibernate 6, we wanted to continue to support the DetachedCriteia in GORM as much as possible. We also wanted to encapsulate the JPA Criteria Building in one class so the following was done:
* DetachedCriteria holds almost all the state of the Query being built. It hold the target class for the query. It does not hold a session.
* AbstractHibernateQuery has a session and holds the DetachedCriteria and is a thin wrapper for it. Calling list or singleResult will internally create the Query and execute it. 
* AbstractHibernateCriteriaBuilder is a thin wrapper around AbstractHibernateQuery. Its main function is to use closures to populate the Hibernate Query and execute it at the end of the closure.
* Only the grails-datastore-gorm-hibernate6 module is being developed at the time.

For testing the following was done:
* Used testcontainers of postgres instead of h2 because h2 does not support all the Java Types correctly.
* A more opinionated and fluent HibernateGormDatastoreSpec is used for the specifications.