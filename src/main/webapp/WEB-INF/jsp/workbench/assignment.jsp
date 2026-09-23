<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://northstar.com/tags" prefix="ns" %>
<ns:view path="/WEB-INF/jsp/workbench/assignment.jsp"/>
<%@ include file="../header.jsp" %>
<%@ include file="../nav.jsp" %>
<h2><bean:message key="jsp.assignment.review"/></h2>
<table class="detail" cellpadding="3" cellspacing="0" border="1">
<tr>
<th><bean:message key="jsp.claim"/></th><td><ns:field name="assignmentClaim" value="${claim.claimId}" type="integer"/></td></tr>
<tr>
<th><bean:message key="jsp.claim.number"/></th><td><bean:write name="claim" property="claimNumber"/></td></tr>
<tr>
<th><bean:message key="jsp.claimant"/></th><td><bean:write name="claim" property="claimantName"/></td></tr>
<tr>
<th><bean:message key="jsp.loss.date"/></th><td><ns:field name="assignmentLossDate" value="${claim.lossDate}" type="date"/></td></tr>
<tr>
<th><bean:message key="jsp.current.adjuster"/></th><td><ns:field name="assignmentCurrent" value="${claim.assignedAdjuster}" type="text"/></td></tr>
<tr>
<th><bean:message key="jsp.current.status"/></th><td><ns:field name="assignmentStatus" value="${claim.status}" type="text"/></td></tr>
</table>
<form method="post" action="assign.do">
<jsp:include page="/WEB-INF/jsp/csrfField.jsp"/>
<input type="hidden" name="claimId" value="<bean:write name="claim" property="claimId"/>"/>
<table class="form" cellpadding="3" cellspacing="0" border="0">
<tr>
<th><bean:message key="jsp.assign.to"/></th><td><input name="adjuster" value="adjuster2"/></td></tr>
<tr><td>&nbsp;</td>
<td><button type="submit"><bean:message key="jsp.assign.claim"/></button></td></tr>
</table>
</form>
<table class="screen-audit" cellpadding="3" cellspacing="0" border="1">
<tr>
<th><bean:message key="jsp.screen.context"/></th>
<td><bean:message key="jsp.screen.readOnly"/></td>
</tr>
<tr>
<th><bean:message key="jsp.screen.operator"/></th>
<td><bean:write name="user"/></td>
</tr>
<tr>
<th><bean:message key="jsp.screen.asOf"/></th>
<td><%= com.northstar.claims.util.ClaimsConfig.get("report.asof.date") %></td>
</tr>
</table>
<%@ include file="../footer.jsp" %>
