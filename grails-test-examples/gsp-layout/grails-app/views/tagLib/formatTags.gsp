<!DOCTYPE html>
<html>
<head>
    <title>Format Tags Test</title>
</head>
<body>
    <h1>Format Tags Test</h1>
    <p id="date-display">Date: <g:formatDate date="${dateValue}" format="yyyy-MM-dd"/></p>
    <p id="number-display">Number: <g:formatNumber number="${numberValue}" format="#,##0.00"/></p>
    <p id="boolean-display">Boolean: <g:formatBoolean boolean="${booleanValue}" true="Yes" false="No"/></p>
</body>
</html>
