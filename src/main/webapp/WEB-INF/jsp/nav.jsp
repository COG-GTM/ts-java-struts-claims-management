<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<table class="menu" cellpadding="2" cellspacing="0" border="0">
<tr><td><bean:message key="nav.quickLinks"/></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/intake/new.do"><bean:message key="nav.newClaim"/></a></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/payment/history.do"><bean:message key="nav.payments"/></a></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/report/openByAdjuster.do"><bean:message key="nav.reports"/></a></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/policy/search.do"><bean:message key="nav.policySearch"/></a></td></tr>
<tr><td><a href="<%= request.getContextPath() %>/logout.do"><bean:message key="nav.logout"/></a></td></tr>
<tr><td><bean:message key="nav.operator"/>: <bean:write name="user"/></td></tr>
<tr><td><bean:message key="nav.applicationVersion"/>: <bean:message key="nav.applicationVersionValue"/></td></tr>
<tr><td><bean:message key="nav.environment"/>: <bean:message key="nav.environmentValue"/></td></tr>
<tr><td><bean:message key="nav.desk"/>: <bean:message key="nav.deskValue"/></td></tr>
<tr><td><bean:message key="nav.session"/>: <bean:message key="nav.sessionValue"/></td></tr>
</table>
