const API_BASE = '/api';

export async function fetchPoints(country) {
  const url = country
    ? `${API_BASE}/points?country=${country}`
    : `${API_BASE}/points`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to fetch points: ${res.status}`);
  return res.json();
}

export async function fetchPointStats() {
  const res = await fetch(`${API_BASE}/points/stats`);
  if (!res.ok) throw new Error(`Failed to fetch stats: ${res.status}`);
  return res.json();
}

export async function fetchPointsInBbox(minLat, maxLat, minLng, maxLng) {
  const res = await fetch(
    `${API_BASE}/points/bbox?minLat=${minLat}&maxLat=${maxLat}&minLng=${minLng}&maxLng=${maxLng}`
  );
  if (!res.ok) throw new Error(`Failed to fetch bbox: ${res.status}`);
  return res.json();
}

export async function ingestPoints() {
  const res = await fetch(`${API_BASE}/points/ingest`, { method: 'POST' });
  if (!res.ok) throw new Error(`Ingestion failed: ${res.status}`);
  return res.json();
}

export async function fetchPointDetails(name) {
  const res = await fetch(`https://api-shipx-pl.easypack24.net/v1/points/${name}`);
  if (!res.ok) throw new Error('Failed to fetch point details');
  return res.json();
}

export async function fetchCompetitors(bounds) {
  if (!bounds) return [];
  const minLat = bounds.getSouth();
  const maxLat = bounds.getNorth();
  const minLng = bounds.getWest();
  const maxLng = bounds.getEast();
  
  const res = await fetch(`/api/site-selection/competitors?minLat=${minLat}&maxLat=${maxLat}&minLng=${minLng}&maxLng=${maxLng}`);
  if (!res.ok) throw new Error('Failed to fetch competitors');
  return res.json();
}

export async function analyzeSiteSelection(bbox, settings) {
  const payload = {
    minLat: bbox.minLat,
    maxLat: bbox.maxLat,
    minLng: bbox.minLng,
    maxLng: bbox.maxLng,
    greenfieldBonus: settings?.greenfieldBonus,
    access247Bonus: settings?.access247Bonus,
    greenfieldRadiusKm: settings?.greenfieldRadiusKm
  };

  const res = await fetch('/api/site-selection/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('Site selection analysis failed');
  return res.json();
}
