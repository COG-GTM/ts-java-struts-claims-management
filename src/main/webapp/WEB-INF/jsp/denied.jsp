<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://northstar.com/tags" prefix="ns" %>
<ns:view path="/WEB-INF/jsp/denied.jsp"/>
<html><head><title><bean:message key="denied.title"/></title></head><body>
<table class="error" cellpadding="4" cellspacing="0" border="1">
<tr><td><h1><bean:message key="denied.title"/></h1></td></tr>
<tr><td><bean:message key="denied.message"/></td></tr>
<tr><td><bean:message key="denied.contact"/></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/home.do"><bean:message key="denied.homeLink"/></a></td></tr>
</table>
</body></html>
