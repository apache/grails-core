package grails.plugin.scaffolding

import grails.artefact.Artefact
import grails.gorm.transactions.ReadOnly
import grails.rest.RestfulController
import grails.util.Holders
import org.grails.datastore.gorm.GormEntityApi

@Artefact("Controller")
@ReadOnly
class RestfulServiceController<T> extends RestfulController<T> {

    RestfulServiceController(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }

    protected def getService() {
        Holders.grailsApplication.getMainContext().getBean(resourceName + 'Service')
    }

    protected T queryForResource(Serializable id) {
        getService().get(id)
    }

    protected List<T> listAllResources(Map params) {
        getService().list(params)
    }

    protected Integer countResources() {
        getService().count()
    }

    protected T saveResource(T resource) {
        getService().save(resource)
    }

    protected T updateResource(T resource) {
        getService().save(resource)
    }

    protected void deleteResource(T resource) {
        getService().delete(((GormEntityApi) resource).ident())
    }
}