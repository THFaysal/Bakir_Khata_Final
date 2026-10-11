

document.addEventListener('DOMContentLoaded', function () {
    initPasswordToggles();
    initLiveFieldValidation();
});

/** Eye icon toggles a password field between hidden and visible text. */
function initPasswordToggles() {
    document.querySelectorAll('[data-toggle-password]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const targetId = btn.getAttribute('data-toggle-password');
            const input = document.getElementById(targetId);
            if (!input) return;
            const icon = btn.querySelector('i');
            if (input.type === 'password') {
                input.type = 'text';
                icon.classList.remove('bi-eye');
                icon.classList.add('bi-eye-slash');
            } else {
                input.type = 'password';
                icon.classList.remove('bi-eye-slash');
                icon.classList.add('bi-eye');
            }
        });
    });
}

/** Adds a green/red border as soon as a required field becomes valid/invalid on blur. */
function initLiveFieldValidation() {
    document.querySelectorAll('.form-control[required]').forEach(function (field) {
        field.addEventListener('blur', function () {
            const group = field.closest('.form-group');
            if (!group) return;
            if (field.checkValidity() && field.value.trim() !== '') {
                group.classList.remove('form-group--invalid');
                group.classList.add('form-group--valid');
            } else if (field.value.trim() !== '') {
                group.classList.add('form-group--invalid');
                group.classList.remove('form-group--valid');
            }
        });
        field.addEventListener('input', function () {
            const group = field.closest('.form-group');
            if (group && group.classList.contains('form-group--invalid') && field.checkValidity()) {
                group.classList.remove('form-group--invalid');
                group.classList.add('form-group--valid');
            }
        });
    });

    // Confirm-password match check on the registration form
    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');
    if (password && confirm) {
        function checkMatch() {
            const group = confirm.closest('.form-group');
            if (!confirm.value) return;
            if (confirm.value !== password.value) {
                confirm.setCustomValidity('Passwords do not match');
                if (group) { group.classList.add('form-group--invalid'); group.classList.remove('form-group--valid'); }
            } else {
                confirm.setCustomValidity('');
                if (group) { group.classList.remove('form-group--invalid'); group.classList.add('form-group--valid'); }
            }
        }
        password.addEventListener('input', checkMatch);
        confirm.addEventListener('input', checkMatch);
    }

    // Optional PIN match check on the registration form
    const pin = document.getElementById('pin');
    const confirmPin = document.getElementById('confirmPin');
    if (pin && confirmPin) {
        function checkPinMatch() {
            const group = confirmPin.closest('.form-group');
            if (!pin.value && !confirmPin.value) {
                confirmPin.setCustomValidity('');
                return;
            }
            if (pin.value !== confirmPin.value) {
                confirmPin.setCustomValidity('PIN does not match');
                if (group) { group.classList.add('form-group--invalid'); group.classList.remove('form-group--valid'); }
            } else {
                confirmPin.setCustomValidity('');
                if (group) { group.classList.remove('form-group--invalid'); group.classList.add('form-group--valid'); }
            }
        }
        pin.addEventListener('input', checkPinMatch);
        confirmPin.addEventListener('input', checkPinMatch);
    }
}
