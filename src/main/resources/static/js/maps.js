import { request } from './api.js';

let loading;

/** Loads the Google Maps script once; later calls reuse the same promise. */
export function loadMaps() {
    if (!loading) {
        loading = doLoad().catch((error) => {
            loading = undefined; // allow a retry after a failure
            throw error;
        });
    }
    return loading;
}

async function doLoad() {
    const response = await request('/api/config');
    if (!response.ok) throw new Error('Could not load the map configuration.');
    const { mapsApiKey } = await response.json();

    await new Promise((resolve, reject) => {
        window.__mapsReady = resolve;
        const script = document.createElement('script');
        script.src = 'https://maps.googleapis.com/maps/api/js'
            + `?key=${encodeURIComponent(mapsApiKey)}&callback=__mapsReady&v=weekly`;
        script.async = true;
        script.onerror = () => reject(new Error('Could not load Google Maps.'));
        document.head.append(script);
    });
}