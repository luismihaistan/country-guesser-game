import { request } from './api.js';
import { loadMaps } from './maps.js';
import { initMap, clearSelection, countryName } from './map.js';

const panoEl = document.querySelector('#pano');
const streakEl = document.querySelector('#streak');
const timerEl = document.querySelector('#timer');
const startPanel = document.querySelector('#start-panel');
const startMessage = document.querySelector('#start-message');
const startBtn = document.querySelector('#start-btn');
const guessPanel = document.querySelector('#guess-panel');
const mapEl = document.querySelector('#map');
const guessBtn = document.querySelector('#guess-btn');

// Created once and reused with setPano(): Dynamic Street View is billed per instantiation.
let panorama = null;
let selected = null; // { iso, name } or null
let timerId = null;
let roundDeadlineMs = 0;
let currentRoundStreak = 0;
let timeoutInFlight = false;

// Google calls this global when it rejects the key (wrong referrer, API not enabled...).
window.gm_authFailure = () =>
    showStart('Google rejected the browser key. Check its referrer and API restrictions.');

function formatTime(totalSeconds) {
    const clamped = Math.max(0, Math.ceil(totalSeconds));
    const minutes = Math.floor(clamped / 60);
    const seconds = clamped % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function stopTimer({ hide = true } = {}) {
    if (timerId !== null) {
        clearInterval(timerId);
        timerId = null;
    }
    roundDeadlineMs = 0;
    if (hide) {
        timerEl.hidden = true;
        timerEl.classList.remove('urgent');
    }
}

function renderTimer(remainingSeconds) {
    timerEl.textContent = formatTime(remainingSeconds);
    timerEl.classList.toggle('urgent', remainingSeconds <= 60);
    timerEl.hidden = false;
}

function startTimer(secondsRemaining) {
    stopTimer({ hide: false });
    const initial = Math.max(0, Math.ceil(secondsRemaining));
    roundDeadlineMs = Date.now() + initial * 1000;
    renderTimer(initial);

    if (initial <= 0) {
        handleTimeout();
        return;
    }
    timerId = setInterval(tickTimer, 250);
}

function tickTimer() {
    if (!roundDeadlineMs) return;
    const remaining = Math.max(0, Math.ceil((roundDeadlineMs - Date.now()) / 1000));
    renderTimer(remaining);
    if (remaining <= 0) {
        handleTimeout();
    }
}

async function handleTimeout() {
    if (timeoutInFlight) return;
    timeoutInFlight = true;
    stopTimer({ hide: false });
    guessBtn.disabled = true;
    guessPanel.hidden = true;

    try {
        const response = await request('/api/game/timeout', { method: 'POST' });
        if (!response.ok) {
            streakEl.textContent = '0';
            showStart("Time's up! Your streak has ended.", 'Play again');
            return;
        }

        const result = await response.json();
        if (!result.expired && result.secondsRemaining > 0) {
            guessPanel.hidden = false;
            updateGuessButton();
            startTimer(result.secondsRemaining);
            return;
        }

        const endedStreak = result.streak ?? currentRoundStreak;
        streakEl.textContent = '0';
        const message = result.actualCountryCode
            ? `Time's up! It was ${countryName(result.actualCountryCode)}. Your streak ended at ${endedStreak}.`
            : `Time's up! Your streak ended at ${endedStreak}.`;
        showStart(message, 'Play again');
    } catch {
        streakEl.textContent = '0';
        showStart("Time's up! Your streak has ended.", 'Play again');
    } finally {
        timeoutInFlight = false;
    }
}

document.addEventListener('visibilitychange', () => {
    if (!document.hidden && timerId !== null) {
        tickTimer();
    }
});

/** The overlay is used for the start screen, the result screen and errors. */
function showStart(message, buttonLabel = 'Get a location') {
    stopTimer();
    startMessage.textContent = message;
    startBtn.textContent = buttonLabel;
    startBtn.disabled = false;
    guessPanel.hidden = true;
    startPanel.hidden = false;
}

function updateGuessButton() {
    guessBtn.disabled = !selected;
    guessBtn.textContent = selected ? `Guess ${selected.name}` : 'Pick a country';
}

function onCountrySelected(iso, name) {
    selected = { iso, name };
    updateGuessButton();
}

async function showRound(round) {
    const receivedAt = Date.now();
    await loadMaps();

    if (!panorama) {
        const { StreetViewPanorama } = await google.maps.importLibrary('streetView');
        panorama = new StreetViewPanorama(panoEl, {
            pano: round.panoId,
            addressControl: false,      // would print the street address
            showRoadLabels: false,      // would print street names
            fullscreenControl: false,
            enableCloseButton: false,
            motionTracking: false,
            motionTrackingControl: false,
        });
    } else {
        panorama.setPano(round.panoId);
        panorama.setPov({ heading: 0, pitch: 0 });
        panorama.setVisible(true);
        google.maps.event.trigger(panorama, 'resize');
    }

    currentRoundStreak = round.streak;
    streakEl.textContent = round.streak;
    startPanel.hidden = true;

    selected = null;
    updateGuessButton();
    guessPanel.hidden = false; // the map container must be visible before it is built
    await initMap(mapEl, onCountrySelected);
    clearSelection();
    const elapsedSeconds = (Date.now() - receivedAt) / 1000;
    startTimer(Math.max(0, round.secondsRemaining - elapsedSeconds));
}

function showResult(result) {
    streakEl.textContent = result.streak;
    if (result.correct) {
        showStart(`Correct! Streak: ${result.streak}.`, 'Next location');
    } else {
        showStart(
            `Not quite. It was ${countryName(result.actualCountryCode)}. Your streak ended at ${result.streak}.`,
            'Play again',
        );
    }
}

async function fetchNextRound() {
    const next = await request('/api/game/next');
    if (next.ok) return { round: await next.json() };

    if (next.status === 409) { // a round is already running, resume it
        const current = await request('/api/game/current');
        if (current.ok) return { round: await current.json() };
    }
    if (next.status === 503) {
        return { message: 'No locations are ready yet. Try again in a few seconds.' };
    }
    return { message: 'Could not start a round.' };
}

function errorMessage(error) {
    return error instanceof TypeError ? 'Cannot reach the server.' : error.message;
}

startBtn.addEventListener('click', async () => {
    startBtn.disabled = true;
    try {
        const { round, message } = await fetchNextRound();
        if (round) await showRound(round);
        else showStart(message);
    } catch (error) {
        showStart(errorMessage(error));
    }
});

guessBtn.addEventListener('click', async () => {
    if (!selected) return;
    stopTimer({ hide: false });
    guessBtn.disabled = true;
    try {
        const response = await request('/api/game/guess', {
            method: 'POST',
            body: { countryCode: selected.iso },
        });
        if (response.status === 410) {
            streakEl.textContent = '0';
            showStart("Time's up! Your streak has ended.", 'Play again');
            return;
        }
        if (!response.ok) throw new Error('Could not submit the guess.');
        showResult(await response.json());
    } catch (error) {
        // The round may still be running: "Get a location" resumes it.
        showStart(errorMessage(error));
    }
});

export function stopGame() {
    stopTimer();
    panorama?.setVisible(false);
}

/** Called when the game view opens: resume a running round or offer to start one. */
export async function startGame(prefetched) {
    stopTimer();
    panorama?.setVisible(false);
    streakEl.textContent = '0';
    startPanel.hidden = true;
    guessPanel.hidden = true;

    try {
        const response = prefetched ?? await request('/api/game/current');
        if (response.ok) await showRound(await response.json());
        else if (response.status === 404) showStart('Ready when you are.');
    } catch (error) {
        showStart(errorMessage(error));
    }
}