import { request } from './api.js';
import { loadMaps } from './maps.js';

const panoEl = document.querySelector('#pano');
const streakEl = document.querySelector('#streak');
const startPanel = document.querySelector('#start-panel');
const startMessage = document.querySelector('#start-message');
const startBtn = document.querySelector('#start-btn');

// Created once and reused with setPano(): Dynamic Street View is billed per instantiation.
let panorama = null;

// Google calls this global when it rejects the key (wrong referrer, API not enabled...).
window.gm_authFailure = () =>
    showStart('Google rejected the browser key. Check its referrer and API restrictions.');

function showStart(message) {
    startMessage.textContent = message;
    startBtn.disabled = false;
    startPanel.hidden = false;
}

async function showRound(round) {
    await loadMaps();

    if (!panorama) {
        panorama = new google.maps.StreetViewPanorama(panoEl, {
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

startBtn.addEventListener('click', async () => {
    startBtn.disabled = true;
    try {
        const { round, message } = await fetchNextRound();
        if (round) await showRound(round);
        else showStart(message);
    } catch (error) {
        showStart(error instanceof TypeError ? 'Cannot reach the server.' : error.message);
    }
});

/** Called when the game view opens: resume a running round or offer to start one. */
export async function startGame() {
    panorama?.setVisible(false);
    streakEl.textContent = '0';
    startPanel.hidden = true;

    try {
        const response = await request('/api/game/current');
        if (response.ok) await showRound(await response.json());
        else if (response.status === 404) showStart('Ready when you are.');
    } catch (error) {
        showStart(error instanceof TypeError ? 'Cannot reach the server.' : error.message);
    }
}