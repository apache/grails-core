<html>
<head>
    <title>Doubled: <g:layoutTitle default="Modal Demo"/></title>
    <g:layoutHead/>
</head>
<body>
<g:layoutBody/>
<!-- Modal -->
<div class="modal fade" id="exampleModal" tabindex="-1" aria-labelledby="exampleModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h1 class="modal-title fs-5" id="exampleModalLabel">Decorator Chaining</h1>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                This Modal was loaded in a separate decorator that only contains a modal.
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                <button type="button" class="btn btn-primary" data-bs-dismiss="modal">Ok</button>
            </div>
        </div>
    </div>
</div>
<script type="application/javascript">
    new bootstrap.Modal('#exampleModal').show()
</script>
</body>
</html>