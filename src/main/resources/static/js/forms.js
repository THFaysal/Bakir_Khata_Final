

document.addEventListener('DOMContentLoaded', function () {
    wireImagePreview('profileImage', 'profileImagePreview');
    wireImagePreview('proofFile', 'proofFilePreview');
});

function wireImagePreview(inputId, previewId) {
    const input = document.getElementById(inputId);
    const preview = document.getElementById(previewId);
    if (!input || !preview) return;

    input.addEventListener('change', function () {
        const file = input.files && input.files[0];
        const label = input.closest('.file-upload');

        if (!file) {
            preview.classList.add('is-hidden');
            return;
        }

        // Update label text to show selected filename
        if (label) {
            let nameEl = label.querySelector('.file-upload__filename');
            if (!nameEl) {
                nameEl = document.createElement('span');
                nameEl.className = 'file-upload__filename';
                label.appendChild(nameEl);
            }
            nameEl.textContent = file.name;
        }

        if (file.type.startsWith('image/')) {
            const reader = new FileReader();
            reader.onload = function (e) {
                preview.src = e.target.result;
                preview.classList.remove('is-hidden');
            };
            reader.readAsDataURL(file);
        } else {
            preview.classList.add('is-hidden');
        }
    });
}
