

document.addEventListener('DOMContentLoaded', function () {
    initLiveSearch();
    initDeleteConfirmModal();
});

/** Filters visible table rows instantly as the user types, without a server round-trip. */
function initLiveSearch() {
    const input = document.getElementById('liveSearch');
    const table = document.querySelector('.data-table');
    if (!input || !table) return;

    const rows = Array.from(table.querySelectorAll('tbody tr'));

    input.addEventListener('input', function () {
        const term = input.value.trim().toLowerCase();
        let visibleCount = 0;
        rows.forEach(function (row) {
            const text = row.textContent.toLowerCase();
            const match = term === '' || text.includes(term);
            row.style.display = match ? '' : 'none';
            if (match) visibleCount++;
        });
    });
}

/** Intercepts delete-form submits and shows a styled confirmation modal instead of a native confirm(). */
function initDeleteConfirmModal() {
    const modal = document.getElementById('confirmModal');
    if (!modal) return;

    const cancelBtn = document.getElementById('modalCancel');
    const confirmBtn = document.getElementById('modalConfirm');
    let pendingForm = null;

    document.querySelectorAll('.confirm-delete-form').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            pendingForm = form;
            modal.classList.add('modal-overlay--open');
        });
    });

    function closeModal() {
        modal.classList.remove('modal-overlay--open');
        pendingForm = null;
    }

    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', function (e) {
        if (e.target === modal) closeModal();
    });
    if (confirmBtn) {
        confirmBtn.addEventListener('click', function () {
            if (pendingForm) {
                confirmBtn.disabled = true;
                confirmBtn.innerHTML = '<span class="spinner"></span> Deleting...';
                pendingForm.submit();
            }
        });
    }
}
