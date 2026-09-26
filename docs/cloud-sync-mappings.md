# Cloud sync mapping behavior

Cloud sync preserves source field values. All mappings registered in
`MasterReferenceService.links` follow the same policy, including nested walk observations.

| Dataset | Master references |
| --- | --- |
| Plant masters | Parent plant and parent department |
| Users | Plant, department, areas, designation |
| Gemba walk | Plant scope, department, process area |
| Walk observations | Gemba category, life saver rule |
| Gemba kaizen | Plant scope, department, process area, kaizen classification |
| Abnormality | Plant scope, department, process area, tag type, defect type |
| Process confirmation | Plant scope, departments, process area |
| Kaizen, abnormality, walk and process masters | No outgoing master references |

- An explicitly blank reference ID stays blank. Source text is retained and a
  warning identifies the record and unresolved field. No local name match is substituted.
- Older exports without reference columns use exact, unique, scoped name matching.
  Missing or ambiguous matches retain the text with an empty link and a warning.
- Valid explicit IDs are checked against the master category and plant/department
  scope. Historical source labels are retained even when the master has been renamed.
- Unknown, malformed, duplicate or out-of-scope explicit IDs still fail the file.
  Sync does not guess replacement IDs or silently discard parts of a multiple selection.
- Other fields continue through their existing serializers and validators. Process
  confirmation observation descriptions are source text, not master ID mappings.
  Invalid JSON, dates, required values, account conflicts and child ownership errors
  are not converted into warnings.

Normal manual CSV imports retain their existing validation and edit restrictions.
Warnings appear in the sync report; an unresolved link is not a repaired assignment.
Correct the assignment in the source application to establish the intended link.

The application receiving the cloud data must be rebuilt and deployed for these
rules to take effect. Tests cover every registered reference and nested walk mappings;
they do not guarantee that every future data or schema error can be imported.
