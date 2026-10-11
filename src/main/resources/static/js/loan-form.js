

document.addEventListener('DOMContentLoaded', function () {
    initInstallmentToggle();
    initDueDateCheck();
});

function initInstallmentToggle() {
    const radios = document.querySelectorAll('input[name="paymentType"]');
    const installmentFields = document.getElementById('installmentFields');
    const countInput = document.getElementById('installmentCount');
    const amountInput = document.getElementById('expectedInstallmentAmount');
    if (!radios.length || !installmentFields) return;

    function sync() {
        const selected = document.querySelector('input[name="paymentType"]:checked');
        const isInstallment = selected && selected.value === 'INSTALLMENT';
        installmentFields.style.display = isInstallment ? 'grid' : 'none';
        if (countInput) countInput.required = isInstallment;
        if (amountInput) amountInput.required = isInstallment;
    }

    radios.forEach(function (radio) {
        radio.addEventListener('change', sync);
    });
    sync(); // run on load to respect pre-selected / edit values
}

function initDueDateCheck() {
    const borrowDate = document.getElementById('borrowDate');
    const dueDate = document.getElementById('dueDate');
    const errorEl = document.getElementById('dueDateError');
    if (!borrowDate || !dueDate) return;

    function validate() {
        if (borrowDate.value && dueDate.value && dueDate.value < borrowDate.value) {
            dueDate.setCustomValidity('Due date cannot be before borrow date');
            if (errorEl) errorEl.classList.remove('is-hidden');
            dueDate.closest('.form-group').classList.add('form-group--invalid');
        } else {
            dueDate.setCustomValidity('');
            if (errorEl) errorEl.classList.add('is-hidden');
        }
    }

    borrowDate.addEventListener('change', validate);
    dueDate.addEventListener('change', validate);
}
