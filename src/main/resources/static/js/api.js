const TOKEN_KEY = 'cg.token';
const EMAIL_KEY = 'cg.email';

export const session = {
    get token() { return localStorage.getItem(TOKEN_KEY); },
    get email() { return localStorage.getItem(EMAIL_KEY); },
    save(token, email) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(EMAIL_KEY, email);
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(EMAIL_KEY);
    },
};

/**
 * fetch wrapper: adds the JWT, serialises the body as JSON, and treats
 * 401/403 on an authenticated call as an ended session.
 * Returns the raw Response so each caller decides how to read it.
 */
export async function request(path, { method = 'GET', body, auth = true } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (auth && session.token) headers['Authorization'] = `Bearer ${session.token}`;

    const response = await fetch(path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    if (auth && (response.status === 401 || response.status === 403)) {
        session.clear();
        window.dispatchEvent(new Event('session-expired'));
    }

    return response;
}