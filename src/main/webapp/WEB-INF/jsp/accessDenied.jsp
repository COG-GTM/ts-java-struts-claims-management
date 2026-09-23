<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://northstar.com/tags" prefix="ns" %>
<ns:view path="/WEB-INF/jsp/accessDenied.jsp"/>
<html><head><title><bean:message key="errors.claimAccess.title"/></title></head><body>
<table class="error" cellpadding="4" cellspacing="0" border="1">
<tr><td><h1><bean:message key="errors.claimAccess.title"/></h1></td></tr>
<tr><td><bean:message key="errors.claimAccess.denied"/></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/workbench/list.do"><bean:message key="errors.claimAccess.workbenchLink"/></a></td></tr>
<tr><td><bean:message key="errors.contact"/></td></tr>
</table>
</body></html>
