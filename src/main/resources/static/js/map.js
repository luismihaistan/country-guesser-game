import { request } from './api.js';

const STYLE = { weight: 0.6, color: '#7d97a5', fillColor: '#eef3f5', fillOpacity: 1 };
const HOVER = { fillColor: '#d7e6ec' };
const SELECTED = { weight: 1.5, color: '#12303f', fillColor: '#f0b429' };

const layers = new Map(); // iso -> Leaflet layer
const names = new Map();  // iso -> country name

let map = null;
let ready = null;
let selectedIso = null;
let onSelect = () => {};

/** Builds the map once (the container must be visible); later calls reuse it. */
export function initMap(container, selectHandler) {
    onSelect = selectHandler;
    if (!ready) {
        ready = build(container).catch((error) => {
            ready = null; // allow a retry
            throw error;
        });
    }
    return ready;
}

async function build(container) {
    const response = await request('/api/countries');
    if (!response.ok) throw new Error('Could not load the country map.');
    const geojson = await response.json();

    map = L.map(container, {
        preferCanvas: true,
        minZoom: 1,
        zoomSnap: 0.25,
        zoomControl: false,
        attributionControl: false,
        maxBounds: [[-85, -180], [85, 180]],
        maxBoundsViscosity: 1,
    });

    L.geoJSON(geojson, {
        style: () => STYLE,
        onEachFeature: (feature, layer) => {
            const { iso, name } = feature.properties;
            layers.set(iso, layer);
            names.set(iso, name);
            layer.bindTooltip(name, { sticky: true });
            layer.on({
                mouseover: () => { if (iso !== selectedIso) layer.setStyle(HOVER); },
                mouseout: () => { if (iso !== selectedIso) layer.setStyle(STYLE); },
                click: () => select(iso),
            });
        },
    }).addTo(map);

    map.invalidateSize();
    map.fitBounds([[-58, -170], [80, 170]]);
    // The panel grows on hover; keep Leaflet's idea of the size in sync.
    new ResizeObserver(() => map.invalidateSize()).observe(container);
}

function select(iso) {
    clearSelection();
    selectedIso = iso;
    const layer = layers.get(iso);
    layer.setStyle(SELECTED);
    layer.bringToFront();
    onSelect(iso, names.get(iso));
}

export function clearSelection() {
    if (selectedIso && layers.has(selectedIso)) layers.get(selectedIso).setStyle(STYLE);
    selectedIso = null;
}

export function countryName(iso) {
    return names.get(iso) ?? iso;
}