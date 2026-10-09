# Permission audit — 8 October 2026

The application grants View and Edit by page (there are no separate Add/Delete grants). Edit authorizes mutation actions; View retains reading, filters, history and permitted exports. Admin retains full access.

| Surface | Audit result |
| --- | --- |
| KPI dashboard / user dashboard | Navigation uses view grants; KPI direct access also checks dashboard entitlement. |
| PMS priorities, weekly priorities, daily performance, daily section | Save, Add, Remove, Delete and reset actions require Edit. Dynamic fields inherit the same rule. |
| Production metrics / legacy add metrics | Section tabs follow View; section inputs and Save follow Edit. Bulk writes require the full production grant. Custom definition management requires full production or KPI configuration Edit. |
| Issue Board and configuration | Row editing, deletion, adding and save actions require Edit; history, search and exports remain available. |
| Gemba Walk schedule / scheduler | Direct route is checked; scheduling mutations require Edit. |
| Gemba Walk reporting / configuration | New Walk link checks destination Edit. Dynamic edit/delete and form actions require Edit; authorized configuration readers can read records/options. |
| Gemba Kaizen / configuration | New Kaizen, row edit/delete and form actions require Edit. |
| Abnormality reports / configuration / tracker | New report, edit/delete, upload and save actions require Edit. |
| CarlEX reports / configuration | New Record, Add Confirmation, observation changes, edit/delete and save require Edit; View/history remain. |
| Leadership / safety tracker | Direct route checked; configuration actions require Edit. |
| Training schedule / scheduler | Direct route checked; add/delete/save require Edit; search and date browsing remain. |
| Meeting agenda | Direct route checked; save requires Edit. |
| H&S Cross / LSR tracking | Save requires Edit; date browsing and CSV export remain available. |
| Plant, designation, abnormality, Gemba, process masters | Add/update/edit/delete/save require Edit; search, View and plant/department browsing remain. |
| KPI configuration | Create, rename, delete, visibility and alert-color changes require Edit. |
| Info Portal | Configuration requires its own grant; viewing the portal does not grant configuration access. |
| User Management | View navigation restored; account mutation actions require Edit; name search, area popup and resizing remain. |
| Email scheduler / SMTP | Save and Test Connection require Edit. |
| License management | License update/save require Edit. |
| Cloud Sync | Restricted to the existing sync-authorized accounts; other accounts do not load sync controls. |
| Login / logout | Authentication controls are outside page-edit gating. |

## Implementation

Shared permission-ui CSS hides marked static and generated actions before interaction. The script also blocks unauthorized click/submit events and applies read-only state after asynchronous rendering. Search, date browsing and explicit read controls remain usable. Backend checks remain authoritative.

The interceptor refreshes database-user permissions on every authenticated request; deleted/inactive users lose access and demoted sessions no longer use stale admin grants. Previously unmapped reporting routes now resolve to their page permissions. Production save requests validate each submitted section, including requests made without the UI.

## Verification

- PermissionRegressionTest: 22 page/right combinations across no access, viewer, editor and admin; mutation APIs; revoked/inactive sessions; production section restrictions; read API compatibility.
- LocalPagesTest: server-side template rendering and navigation with an active administrator.
- permission-ui.test.cjs: hidden/disabled actions, capture-phase rejection, asynchronous actions, section overrides, preserved filters and template action markers.
- Full Java and existing JavaScript regressions run.
- Live browser visual testing was unavailable: no browser was connected. No production deployment or real user permissions were changed.
