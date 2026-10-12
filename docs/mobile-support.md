# Mobile support

The shared responsive layer is loaded by all 24 page templates. Cloud and on-premises use the same templates; mobile layout does not change licensing, cloud redirects, permissions, or data APIs.

## Page inventory

| Pages | Responsive support |
| --- | --- |
| Login (`/pms-login`) | Viewport-sized login card, readable input text and touch controls |
| KPI Dashboard (`/kpi-dashboard`) and User Dashboard (`/user-dashboard`) | Stacked dashboard sections, contained wide tables and charts; desktop TV sizing retained |
| User Management (`/pms-configuration`) | Search/actions wrap, horizontal table scrolling, full-width forms/dialogs, touch/focus area tooltips |
| Top/Weekly Priorities, Daily Performance, People/Quality/Service/Cost daily (`/pms/*`) | Stacked configurable forms, retained fields and readable scrolling tables |
| Production metrics (`/pms/production-metrics`, `/add-metrics`, `/add-daily-data`) | Stacked entry controls and contained data tables |
| Settings (`/settings?config=...`) | Responsive configuration navigation, master-data forms, KPI configuration, H&S Cross, LSR, training/meeting schedules, issue-board setup, Info Portal and License Management |
| Abnormality tracker/reporting/configuration | Wrapped filters, responsive chart grids and drawers, horizontally scrollable records |
| Gemba schedule/walk reporting/configuration | Wrapped filters, responsive charts and record drawers |
| Leadership Gemba tracker and Gemba Kaizen/configuration | Responsive charts, forms, table scrollports and drawers |
| Process Confirmation/configuration | Stacked filters/observations, responsive chart grids and record drawers |
| Issue Board, Training Schedule, Meeting Agenda | Contained wide schedules/tables, stacked summary/form grids |
| Info Portal (`/client-selection`) | Wrapping portal buttons and shared mobile navigation |
| Email Scheduler and SMTP Configuration | Stacked forms and wrapping actions |
| Cloud Sync Configuration | Stacked sync fields and touch controls |

Legacy configuration aliases resolve to these same templates. Existing role restrictions and intentionally hidden navigation remain in place.

## Behavior

- Responsive overrides apply below 1024 CSS pixels; desktop/TV styles remain authoritative at larger widths.
- Mobile navigation opens from the existing menu control, closes by the close button, outside tap or Escape, traps keyboard focus while open, and restores the desktop collapsed state on resize.
- Tables retain every column. Existing scrollports are enhanced; bare tables receive wrappers only in mobile mode, which are removed when returning to desktop. Dynamically inserted tables are included.
- Forms use 16px input text and controls have 44px minimum touch height where practical. Drawers/dialogs fit dynamic viewport height; internal contents scroll. Hidden/permission-controlled elements are never made visible by the mobile layer.
- The existing area tooltip supports tap, keyboard focus, outside dismissal and Escape. Mobile rules constrain its width and height to the viewport.

## Validation

- `node --check src/main/resources/static/js/mobile-responsive.js`
- `node src/test/js/mobile-responsive.test.cjs`: navigation, focus wrap, outside/Escape/Enter behavior, closed-menu focus exclusion, generated/existing/dynamic tables, desktop restoration and preservation of permission-hidden controls.
- `LocalPagesTest` covers rendered mobile asset/viewport inclusion for 43 page routes and settings variants.
- Browser automation was unavailable in this environment. No screenshot, real-device or visual-overflow verification is claimed. Manual follow-up: verify 360px, 390px and 768px widths with populated tables, long area lists, an open edit drawer, validation errors and the software keyboard; verify desktop KPI TV layout and column resizing at 1280px or larger.

## Files

`static/css/mobile-responsive.css` supplies the scoped layout overrides; `static/js/mobile-responsive.js` supplies navigation accessibility and table enhancement. Each page loads these assets after its existing head styles.
