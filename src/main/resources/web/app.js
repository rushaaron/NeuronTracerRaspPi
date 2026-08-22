'use strict';

const POLL_INTERVAL_MS = 2000;
const GIVE_UP_AFTER_MS = 20 * 60 * 1000;

const form = document.getElementById('trace-form');
const fileInput = document.getElementById('image');
const fileName = document.getElementById('file-name');
const submitButton = document.getElementById('submit-button');
const statusLine = document.getElementById('status');
const preview = document.getElementById('preview');
const stats = document.getElementById('stats');
const downloadLink = document.getElementById('download');

const statFields = {
    branches: document.getElementById('stat-branches'),
    pixels: document.getElementById('stat-pixels'),
    microns: document.getElementById('stat-microns'),
    average: document.getElementById('stat-average'),
};

// Held so the object URL from the previous run can be released before a new one replaces it.
let archiveUrl = null;

// Bumped on every submit so a poll left over from an earlier run quietly stands down.
let currentRun = 0;

form.addEventListener('submit', onSubmit);
form.addEventListener('reset', onReset);
fileInput.addEventListener('change', onFileChosen);

function onFileChosen() {
    const file = fileInput.files[0];
    fileName.textContent = file ? file.name : 'No file chosen yet.';
}

function onReset() {
    currentRun++;
    // The reset happens after this handler, so let the browser clear the fields first.
    window.setTimeout(() => {
        clearResult();
        setWorking(false);
        fileName.textContent = 'No file chosen yet.';
        setStatus('Upload an image to get started.');
    }, 0);
}

async function onSubmit(event) {
    event.preventDefault();

    const file = fileInput.files[0];
    if (!file) {
        setStatus('Choose an image first.', true);
        return;
    }

    const run = ++currentRun;
    clearResult();
    setWorking(true);
    setStatus('Uploading ' + file.name + '.');

    try {
        const submitted = await postImage(file);
        if (run !== currentRun) {
            return;
        }

        setStatus('Tracing ' + file.name + '. Large images can take a few minutes.');
        const result = await waitForResult(submitted.statusUrl, run);
        if (run !== currentRun) {
            return;
        }

        showResult(result);
    } catch (error) {
        if (run !== currentRun) {
            return;
        }
        console.error('Tracing failed', error);
        setStatus(error.message || 'Could not reach the tracer. Check your connection and try again.', true);
    } finally {
        if (run === currentRun) {
            setWorking(false);
        }
    }
}

async function postImage(file) {
    const response = await fetch('/api/trace?' + buildQuery(file), {
        method: 'POST',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
    });

    const payload = await readJson(response);
    if (response.status !== 202 || !payload || !payload.statusUrl) {
        throw new Error(messageFor(payload, response, 'Could not start the trace'));
    }
    return payload;
}

async function waitForResult(statusUrl, run) {
    const giveUpAt = Date.now() + GIVE_UP_AFTER_MS;

    while (Date.now() < giveUpAt) {
        await sleep(POLL_INTERVAL_MS);
        if (run !== currentRun) {
            return null;
        }

        const response = await fetch(statusUrl);
        const payload = await readJson(response);

        if (response.status === 200) {
            return payload;
        }

        if (response.status === 202) {
            const elapsed = payload && payload.elapsedSeconds ? payload.elapsedSeconds : 0;
            setStatus('Tracing in progress, ' + formatElapsed(elapsed) + ' so far.');
            continue;
        }

        throw new Error(messageFor(payload, response, 'Tracing failed'));
    }

    throw new Error('Gave up waiting for this trace to finish.');
}

function buildQuery(file) {
    const params = new URLSearchParams();
    for (const id of ['branchShade', 'cellBranchShade', 'xOffset', 'yOffset']) {
        const field = document.getElementById(id);
        params.set(id, field.value === '' ? field.defaultValue : field.value);
    }
    params.set('ignore', document.getElementById('ignore').value);
    params.set('name', file.name);
    return params.toString();
}

async function readJson(response) {
    try {
        return await response.json();
    } catch (error) {
        return null;
    }
}

function messageFor(payload, response, fallback) {
    if (payload && payload.error) {
        return payload.error;
    }
    return fallback + ' (' + response.status + ').';
}

function showResult(payload) {
    preview.src = 'data:image/jpeg;base64,' + payload.preview;
    preview.hidden = false;

    const s = payload.stats;
    statFields.branches.textContent = s.branches.toLocaleString();
    statFields.pixels.textContent = s.pixels.toLocaleString();
    statFields.microns.textContent = formatMicrons(s.microns);
    statFields.average.textContent = formatMicrons(s.averageMicrons);
    stats.hidden = false;

    archiveUrl = URL.createObjectURL(base64ToBlob(payload.archive, 'application/zip'));
    downloadLink.href = archiveUrl;
    downloadLink.download = payload.filename;
    downloadLink.hidden = false;

    if (s.branches === 0) {
        setStatus('No branches were found. Try filling in the cell body with pink, or raising the shade values.', true);
    } else {
        setStatus('Traced ' + s.branches + (s.branches === 1 ? ' branch.' : ' branches.'));
    }
}

function clearResult() {
    preview.hidden = true;
    preview.removeAttribute('src');
    stats.hidden = true;
    downloadLink.hidden = true;
    downloadLink.removeAttribute('href');

    if (archiveUrl) {
        URL.revokeObjectURL(archiveUrl);
        archiveUrl = null;
    }
}

function setWorking(working) {
    submitButton.disabled = working;
    submitButton.classList.toggle('working', working);
    submitButton.textContent = working ? 'Tracing in progress' : 'Trace image';
}

function setStatus(message, isError) {
    statusLine.textContent = message;
    statusLine.classList.toggle('error', Boolean(isError));
}

function formatElapsed(seconds) {
    if (seconds < 60) {
        return seconds + 's';
    }
    return Math.floor(seconds / 60) + 'm ' + (seconds % 60) + 's';
}

function formatMicrons(value) {
    return value.toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' \u00b5m';
}

function base64ToBlob(base64, type) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return new Blob([bytes], { type: type });
}

function sleep(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
}
