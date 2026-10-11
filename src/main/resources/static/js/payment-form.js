document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('paymentForm');
    if (!form) return;

    const loan = document.getElementById('loanId');
    const amount = document.getElementById('amount');
    const methods = Array.from(document.querySelectorAll('input[name="method"]'));
    const mobileProviderSection = document.getElementById('mobileProviderSection');
    const providerChoices = Array.from(document.querySelectorAll('input[name="mobileProviderChoice"]'));
    const destinationSection = document.getElementById('destinationSection');
    const destinations = Array.from(document.querySelectorAll('.payment-destination'));
    const noDestination = document.getElementById('noDestination');
    const destinationDetail = document.getElementById('destinationDetail');
    const destinationDetailTitle = document.getElementById('destinationDetailTitle');
    const destinationDetailBody = document.getElementById('destinationDetailBody');
    const digitalDemoNotice = document.getElementById('digitalDemoNotice');
    const holdBtn = document.getElementById('holdPayBtn');
    const holdProgress = document.getElementById('holdProgress');
    const holdLabel = document.getElementById('holdLabel');
    const holdConfirmed = document.getElementById('holdConfirmed');
    const feePercent = Number(form.dataset.feePercent || 4);
    const bankSandboxAvailable = form.dataset.bankAvailable === 'true';
    const bankConstructionNotice = document.getElementById('bankConstructionNotice');

    let timer = null;
    let startedAt = 0;

    function selectedMethod() {
        return methods.find(r => r.checked)?.value || 'CASH';
    }

    function selectedProvider() {
        return providerChoices.find(r => r.checked)?.value || '';
    }

    function updateMethodUI() {
        const method = selectedMethod();

        if (method === 'MOBILE_BANKING') {
            mobileProviderSection.classList.remove('is-hidden');
        } else {
            mobileProviderSection.classList.add('is-hidden');

            providerChoices.forEach(p => {
                p.checked = false;
            });
        }
        if (method === 'CASH' || !method) {
            destinationSection.hidden = true;
        } else {
            destinationSection.hidden = false;
        }
        if (bankConstructionNotice) bankConstructionNotice.hidden = !(method === 'BANK' && !bankSandboxAvailable);
        if (holdBtn) holdBtn.disabled = (method === 'BANK' && !bankSandboxAvailable);
        updateDestinations();
    }

    function updateDestinations() {
        const option = loan.options[loan.selectedIndex];
        const owner = option ? option.dataset.lenderUserId : '';
        const method = selectedMethod();
        const provider = selectedProvider();
        const neededType = method === 'BANK' ? 'BANK' : method === 'MOBILE_BANKING' ? 'MOBILE' : null;
        const visibleItems = [];

        destinations.forEach(function (item) {
            const providerMatch = neededType !== 'MOBILE' || (!!provider && item.dataset.provider === provider);
            const show = !!neededType && item.dataset.owner === owner && item.dataset.accountType === neededType && providerMatch;
            item.hidden = !show;
            const input = item.querySelector('input');
            if (!show && input) input.checked = false;
            if (show) visibleItems.push(item);
        });

        if (visibleItems.length > 0 && !visibleItems.some(item => item.querySelector('input')?.checked)) {
            const firstInput = visibleItems[0].querySelector('input');
            if (firstInput) firstInput.checked = true;
        }

        if (neededType === 'MOBILE' && !provider) {
            noDestination.textContent = 'Choose bKash, Nagad or Rocket to see the lender\'s saved number.';
            noDestination.hidden = false;
        } else if (neededType && visibleItems.length === 0) {
            noDestination.textContent = 'This lender has not saved a destination for the selected payment method.';
            noDestination.hidden = false;
        } else {
            noDestination.hidden = true;
        }

        updateDestinationDetail();
        updateSummary();
    }

    function updateDestinationDetail() {
        const selected = document.querySelector('input[name="paymentAccountId"]:checked');
        if (!selected) {
            destinationDetail.hidden = true;
            destinationDetailBody.textContent = '';
            return;
        }

        const item = selected.closest('.payment-destination');
        if (!item) return;

        const type = item.dataset.accountType;
        destinationDetail.hidden = false;
        destinationDetailTitle.textContent = type === 'BANK' ? 'Lender bank details' : 'Lender mobile payment details';

        const rows = [];
        if (type === 'BANK') {
            rows.push(['Bank', item.dataset.bank || '—']);
            rows.push(['Account holder', item.dataset.holder || '—']);
            rows.push(['Account number', item.dataset.number || '—']);
            if (item.dataset.branch) rows.push(['Branch', item.dataset.branch]);
            if (item.dataset.routing) rows.push(['Routing number', item.dataset.routing]);
        } else {
            rows.push(['Provider', item.dataset.provider || 'Mobile']);
            rows.push(['Lender number', item.dataset.number || '—']);
        }

        destinationDetailBody.innerHTML = rows.map(function (row) {
            return '<div class="destination-detail__row"><span>' + escapePaymentHtml(row[0]) + '</span><strong>' + escapePaymentHtml(row[1]) + '</strong></div>';
        }).join('');
    }

    function updateSummary() {
        const principal = Math.max(0, Number(amount.value || 0));
        const option = loan.options[loan.selectedIndex];
        const penalty = Math.max(0, Number(option?.dataset.penalty || 0));
        const fee = principal * feePercent / 100;

        setMoney('summaryPrincipal', principal);
        setMoney('summaryFee', fee);
        setMoney('summaryPenalty', penalty);
        setMoney('summaryTotal', principal + fee + penalty);
    }

    function setMoney(id, value) {
        document.getElementById(id).textContent = '৳' + value.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function startHold(event) {
        event.preventDefault();
        if (!form.reportValidity()) return;

        const method = selectedMethod();
        if (method === 'BANK' && !bankSandboxAvailable) return;
        if (method === 'MOBILE_BANKING' && !selectedProvider()) {
            noDestination.textContent = 'Choose a mobile provider first.';
            noDestination.hidden = false;
            return;
        }

        if (method !== 'CASH' && !document.querySelector('input[name="paymentAccountId"]:checked')) {
            noDestination.hidden = false;
            return;
        }

        cancelHold();
        startedAt = Date.now();
        holdLabel.textContent = 'Keep holding...';

        timer = setInterval(function () {
            const elapsed = Date.now() - startedAt;
            const percent = Math.min(100, elapsed / 3000 * 100);
            holdProgress.style.width = percent + '%';
            holdLabel.textContent = elapsed >= 2000 ? 'Almost there...' : 'Keep holding...';

            if (elapsed >= 3000) {
                clearInterval(timer);
                timer = null;
                holdConfirmed.value = 'true';
                holdLabel.textContent = method === 'CASH' ? 'Sending cash request...' : 'Opening test gateway...';
                showButtonLoading(holdBtn, method === 'CASH' ? 'Sending...' : 'Preparing demo...');
                form.requestSubmit();
            }
        }, 40);
    }

    function cancelHold() {
        if (timer) clearInterval(timer);
        timer = null;
        if (holdConfirmed.value !== 'true') {
            holdProgress.style.width = '0%';
            holdLabel.textContent = 'Hold 3 seconds to pay';
        }
    }

    function escapePaymentHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    loan.addEventListener('change', updateDestinations);
    amount.addEventListener('input', updateSummary);
    methods.forEach(r => r.addEventListener('change', updateMethodUI));
    providerChoices.forEach(r => r.addEventListener('change', updateDestinations));
    destinations.forEach(item => {
        const radio = item.querySelector('input');
        if (radio) radio.addEventListener('change', updateDestinationDetail);
    });

    holdBtn.addEventListener('pointerdown', startHold);
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(eventName => {
        holdBtn.addEventListener(eventName, cancelHold);
    });

    form.addEventListener('submit', function (event) {
        if (holdConfirmed.value !== 'true') event.preventDefault();
    });

    updateMethodUI();
});
