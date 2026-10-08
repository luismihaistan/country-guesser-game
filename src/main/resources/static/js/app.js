import { request, session } from './api.js';
import { startGame } from './game.js';

const authView = document.querySelector('#auth-view');
const gameView = document.querySelector('#game-view');
const form = document.querySelector('#auth-form');
const submitBtn = document.querySelector('#auth-submit');
const switchBtn = document.querySelector('#auth-switch');
const switchText = document.querySelector('#auth-switch-text');
const passwordHint = document.querySelector('#password-hint');
const errorBox = document.querySelector('#auth-error');
const who = document.querySelector('#who');
const logoutBtn = document.querySelector('#logout');

let mode = 'login'; // 'login' | 'register'

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = !message;
}

function showView(name) {
    authView.hidden = name !== 'auth';
    gameView.hidden = name !== 'game';
}

function setMode(next) {
    mode = next;
    const isLogin = mode === 'login';
    submitBtn.textContent = isLogin ? 'Sign in' : 'Create account';
    switchText.textContent = isLogin ? 'New here?' : 'Already have an account?';
    switchBtn.textContent = isLogin ? 'Create an account' : 'Sign in';
    form.password.autocomplete = isLogin ? 'current-password' : 'new-password';
    form.password.minLength = isLogin ? 0 : 8;
    passwordHint.hidden = isLogin;
    showError('');
}

async function readMessage(response, fallback) {
    if (response.status === 401 || response.status === 409) {
        const text = await response.text();
        if (text) return text;
    }
    if (response.status === 400) {
        return 'Check the email address and use a password of at least 8 characters.';
    }
    return fallback;
}

async function register(email, password) {
    const response = await request('/api/auth/register', {
        method: 'POST', body: { email, password }, auth: false,
    });
    if (!response.ok) throw new Error(await readMessage(response, 'Could not create the account.'));
}

async function login(email, password) {
    const response = await request('/api/auth/login', {
        method: 'POST', body: { email, password }, auth: false,
    });
    if (!response.ok) throw new Error(await readMessage(response, 'Could not sign in.'));
    const { token } = await response.json();
    session.save(token, email);
}

function enterGame() {
    who.textContent = session.email ?? '';
    form.reset();
    showView('game');
    startGame(); // after showView: the panorama needs a visible, sized container
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const email = form.email.value.trim();
    const password = form.password.value;

    submitBtn.disabled = true;
    showError('');
    try {
        if (mode === 'register') await register(email, password);
        await login(email, password);
        enterGame();
    } catch (error) {
        showError(error instanceof TypeError ? 'Cannot reach the server.' : error.message);
    } finally {
        submitBtn.disabled = false;
    }
});

switchBtn.addEventListener('click', () => setMode(mode === 'login' ? 'register' : 'login'));

logoutBtn.addEventListener('click', () => {
    session.clear();
    setMode('login');
    showView('auth');
});

window.addEventListener('session-expired', () => {
    setMode('login');
    showView('auth');
    showError('Your session ended. Sign in again.');
});

async function init() {
    setMode('login');
    if (!session.token) {
        showView('auth');
        return;
    }
    try {
        // Harmless read; a 401/403 clears the stale token and fires 'session-expired'.
        await request('/api/game/current');
    } catch {
        showView('auth');
        showError('Cannot reach the server.');
        return;
    }
    if (session.token) enterGame();
}

init();