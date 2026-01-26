<%--
    Custom default wrapper template that applies to all fields
    unless overridden by a more specific template.
    
    This tests that the default template resolution works correctly.
--%>
<div class="form-group custom-default-wrapper" data-field="${property}">
    <label for="${property}">
        <g:if test="${required}"><span class="text-danger">*</span></g:if>
        ${label}
    </label>
    ${raw(widget)}
    <g:if test="${invalid}">
        <div class="invalid-feedback d-block">
            <g:each var="error" in="${errors}">
                <span>${error}</span>
            </g:each>
        </div>
    </g:if>
</div>
