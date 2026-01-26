<%--
    Custom wrapper template for Employee.biography field
    Tests that custom templates override default rendering
--%>
<div class="form-group custom-biography-wrapper" data-field="biography">
    <label for="${property}" class="custom-label">
        ${label} <span class="custom-indicator">(Custom)</span>
    </label>
    <div class="custom-widget-container">
        ${raw(widget)}
    </div>
    <g:if test="${errors}">
        <div class="custom-errors">
            <g:each var="error" in="${errors}">
                <span class="custom-error">${error}</span>
            </g:each>
        </div>
    </g:if>
    <small class="form-text text-muted">Enter detailed biography (max 5000 characters)</small>
</div>
