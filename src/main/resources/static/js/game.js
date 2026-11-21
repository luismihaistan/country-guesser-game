import { request } from './api.js';
import { loadMaps } from './maps.js';
import { initMap, clearSelection, countryName } from './map.js';

const panoEl = document.querySelector('#pano');
const streakEl = document.querySelector('#streak');
const startPanel = document.querySelector('#start-panel');
const startMessage = document.querySelector('#start-message');
const startBtn = document.querySelector('#start-btn');
const guessPanel = document.querySelector('#guess-panel');
const mapEl = document.querySelector('#map');
const guessBtn = document.querySelector('#guess-btn');

// Created once and reused with setPano(): Dynamic Street View is billed per instantiation.
let panorama = null;
let selected = null; // { iso, name } or null

// Google calls this global when it rejects the key (wrong referrer, API not enabled...).
window.gm_authFailure = () =>
    showStart('Google rejected the browser key. Check its referrer and API restrictions.');

/** The overlay is used for the start screen, the result screen and errors. */
function showStart(message, buttonLabel = 'Get a location') {
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
    }

    streakEl.textContent = round.streak;
    startPanel.hidden = true;

    selected = null;
    updateGuessButton();
    guessPanel.hidden = false; // the map container must be visible before it is built
    await initMap(mapEl, onCountrySelected);
    clearSelection();
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
    guessBtn.disabled = true;
    try {
        const response = await request('/api/game/guess', {
            method: 'POST',
            body: { countryCode: selected.iso },
        });
        if (response.status === 410) {
            showStart('That round is over. Start a new one.');
            return;
        }
        if (!response.ok) throw new Error('Could not submit the guess.');
        showResult(await response.json());
    } catch (error) {
        // The round may still be running: "Get a location" resumes it.
        showStart(errorMessage(error));
    }
});

/** Called when the game view opens: resume a running round or offer to start one. */
export async function startGame(prefetched) {
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