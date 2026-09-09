(function () {
    function escape(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character];
        });
    }

    function formatDate(value) {
        if (!value) return '';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
    }

    window.formatAssignmentHistory = function (entries) {
        if (!Array.isArray(entries) || !entries.length) return 'No assignment history';
        return entries.map(function (entry) {
            const actor = entry.assignedBy || 'User';
            const from = entry.fromAssignee ? ' from "' + entry.fromAssignee + '"' : '';
            const target = entry.toAssignee || 'Unassigned';
            const date = formatDate(entry.assignedAt);
            const reason = entry.remarks ? ' Reason: ' + entry.remarks : '';
            return escape(actor + ' assigned' + from + ' to "' + target + '"' + (date ? ' on ' + date + '.' : '.') + reason);
        }).join('<br>');
    };

    window.loadAssignmentHistoryCells = function (selector, endpointBuilder) {
        $(selector).each(function () {
            const cell = $(this);
            $.getJSON(endpointBuilder(cell.data('record-id')), function (entries) {
                cell.html(window.formatAssignmentHistory(entries));
            }).fail(function () {
                cell.text('History unavailable');
            });
        });
    };
}());
