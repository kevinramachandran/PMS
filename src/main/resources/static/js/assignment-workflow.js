(function(window, $) {
    'use strict';
    let saved = {}, viewing = false, initialSelector = '#assignedTo';
    function select(selector, value) {
        const $field = $(selector), selected = String(value || '');
        if (selected && !$field.find('option').toArray().some(function(option) { return option.value === selected; })) {
            $field.append(new Option(selected, selected));
        }
        $field.val(selected);
    }
    function options(selector, html, value) {
        const selected = value === undefined ? $(selector).val() : value;
        $(selector).html(html);
        select(selector, selected);
    }
    function refresh() {
        $(initialSelector).prop('disabled', viewing || !!saved.id);
        [1, 2].forEach(function(stage) {
            const active = !viewing && !!saved.id && !saved['reassignedTo' + stage] && (stage === 1 || !!saved.reassignedTo1);
            const $assignee = $('#reassignedTo' + stage), $remark = $('#reassignment' + stage + 'Remark');
            $assignee.prop('disabled', !active);
            $remark.prop('disabled', !active || !$assignee.val()).prop('required', active && !!$assignee.val());
            $assignee.closest('.assignment-stage').toggleClass('assignment-stage-disabled', !active);
            $assignee.closest('.assignment-stage').find('.assignment-stage-status').text(saved['reassignedTo' + stage] ? 'Saved' : active ? 'Available' : 'Locked');
        });
        $('.assignment-workflow-help').text(viewing ? 'Assignment history is read only.' : !saved.id ? 'Choose the initial assignee. Reassignment becomes available after saving.' : saved.reassignedTo2 ? 'Both reassignment stages are saved.' : saved.reassignedTo1 ? 'Reassignment 1 is saved. You can now choose reassignee 2 and add a remark.' : 'Choose reassignee 1 and add a remark. Save before using reassignment 2.');
    }
    function setRecord(record, initial, readOnly) {
        saved = Object.assign({}, record || {});
        viewing = !!readOnly;
        initialSelector = initial || '#assignedTo';
        [1, 2].forEach(function(stage) {
            select('#reassignedTo' + stage, saved['reassignedTo' + stage]);
            $('#reassignment' + stage + 'Remark').val(saved['reassignment' + stage + 'Remark'] || '');
        });
        const key = initialSelector.slice(1);
        if (saved.id) select(initialSelector, saved[key]);
        refresh();
    }
    $(document).on('change', '#reassignedTo1,#reassignedTo2', function() {
        if (!$(this).val()) $('#reassignment' + (this.id.endsWith('1') ? '1' : '2') + 'Remark').val('');
        refresh();
    });
    window.AssignmentWorkflow = { select: select, options: options, setRecord: setRecord, refresh: refresh };
})(window, jQuery);
