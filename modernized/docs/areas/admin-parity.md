# Admin live HTTP parity

Both apps seeded and run with their real file databases. Login: supervisor/supervisor; legacy JDK 17 on 8080, modern JDK 21 on 8081, context /claims. Requests used curl without following redirects. Only the listening port in absolute redirect origins is normalized to PORT. Authenticated responses have no Location header. Empty arrays mean no matching ns fields/errors; not omitted assertions.

| Variant | Legacy status/view/fields | Modern status/view/fields | Match |
|---|---|---|---|
| adjusters GET | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters POST | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters form GET | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters form POST | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters dispatch GET | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters dispatch POST | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| reference GET | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| reference POST | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| reference params | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 404; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| editAdjuster GET | 500; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 500; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| editAdjuster POST | 500; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | 500; /WEB-INF/jsp/error.jsp; fields=[]; errors=[] | yes |
| adjusters GET unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| adjusters POST unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| adjusters form GET unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| adjusters form POST unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| adjusters dispatch GET unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| adjusters dispatch POST unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| reference GET unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| reference POST unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| reference params unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| editAdjuster GET unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |
| editAdjuster POST unauthenticated | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | 302; no view; fields=[]; errors=[]; Location=http://localhost:PORT/claims/login.do | yes |

## Request variants

All paths are under /claims/admin/.

* adjusters GET/POST: adjusters.do with no parameters.
* form GET/POST: fullName=Changed&region=West&active=bad&id=1&status=ACTIVE&username=x&password=y&policyId=2&claimId=3.
* dispatch GET: method=edit&dispatch=save; POST: method=save&dispatch=edit.
* reference GET/POST: reference.do with no parameters; reference params: method=edit&dispatch=save&referenceVersion=x.
* editAdjuster GET has no parameters; POST has fullName=Changed.
* Every request repeated without cookies. Both apps were logged into using the same supervisor account.

22/22 variants matched HTTP status, Location, ns:view, ns:error and f_NAME span extraction. The rendered error reference status also remains 404/500.

## Replay

```text
make: Entering directory '/home/ubuntu/repos/ts-java-struts-claims-management/modernized'
python3 parity/replay.py --area admin
usage: replay.py [-h] --area
                 {foundation,policy,intake,workbench,settlement,payment,reports,all}
replay.py: error: argument --area: invalid choice: 'admin' (choose from foundation, policy, intake, workbench, settlement, payment, reports, all)
make: *** [Makefile:15: replay] Error 2
make: Leaving directory '/home/ubuntu/repos/ts-java-struts-claims-management/modernized'
```

No harness edits: admin is not an accepted area and has no golden transcripts. Live curl comparison and the integration suite provide coverage instead. No browser screenshots or visual comparison were performed; converted, unreachable admin screen layouts are visually unverified.
