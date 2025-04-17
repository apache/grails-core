<ol class="${pageScope['class']?:'property-list'} ${domainClass.decapitalizedName}">
    <g:each in="${domainProperties}" var="p">
        <li class="${listItemClass?:'fieldcontain'}">
            <span id="${p.name}-label" class="${labelClass?:'property-label'}"><g:message code="${domainClass.decapitalizedName}.${p.name}.label" default="${p.defaultLabel}" /></span>
            <div class="${valueClass?:'property-value'}" aria-labelledby="${p.name}-label">${body(p)}</div>
        </li>
    </g:each>
</ol>