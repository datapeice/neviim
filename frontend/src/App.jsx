import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { MapContainer, TileLayer, CircleMarker, Popup, useMap, ZoomControl, GeoJSON, Rectangle, useMapEvents, Circle, Pane, Marker } from 'react-leaflet';
import L from 'leaflet';
import * as api from './api';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import 'leaflet/dist/leaflet.css';

const GEOJSON_URL = 'https://raw.githubusercontent.com/datasets/geo-countries/master/data/countries.geojson';

function LockerPopup({ point, onDestroy }) {
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const realCompetitor = point.type === 'competitor';

  useEffect(() => {
    if (point.type !== 'competitor') {
      setLoading(true);
      api.fetchPointDetails(point.name)
        .then(setDetails)
        .catch(e => console.warn('Could not fetch photo for point', e))
        .finally(() => setLoading(false));
    }
  }, [point]);

  return (
    <div style={{ fontFamily: 'Inter', fontSize: '13px', width: '240px', display: 'flex', flexDirection: 'column' }}>
      <strong style={{ fontSize: '14px', color: '#333' }}>{point.name}</strong>
      <div>{point.city ? `${point.city} · ` : ''}{point.type}</div>

      <div style={{ minHeight: '60px', marginTop: '6px' }}>
        {loading ? (
          <div style={{ color: '#888', fontStyle: 'italic' }}>Loading live API data...</div>
        ) : details ? (
          <div style={{ color: '#444' }}>
            {details.opening_hours && <div><strong>Hours:</strong> {details.opening_hours}</div>}
            {details.location_description && <div style={{ marginTop: '4px' }}>{details.location_description}</div>}
          </div>
        ) : (
          <div style={{ color: '#666' }}>{point.address || point.locationDescription}</div>
        )}
      </div>

      {point.type !== 'competitor' && (
        <div style={{ height: '160px', width: '100%', marginTop: '8px', backgroundColor: '#f0f0f0', borderRadius: '6px', overflow: 'hidden' }}>
          {details?.image_url ? (
            <img
              src={details.image_url}
              alt="Location"
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : loading ? (
            <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#aaa' }}>
              Loading photo...
            </div>
          ) : (
            <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#aaa' }}>
              No photo available
            </div>
          )}
        </div>
      )}
      {realCompetitor && (
        <button className="btn-destroy" onClick={() => onDestroy(point)}>
          DESTROY!
        </button>
      )}
    </div>
  );
}

function formatNumber(n) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
  return String(n);
}

function MapUpdater({ center, zoom }) {
  const map = useMap();
  useEffect(() => {
    if (center) map.setView(center, zoom);
  }, [center, zoom, map]);
  return null;
}

function MapViewportTracker({ setVisibleBounds, minZoom = 11 }) {
  const map = useMapEvents({
    moveend() {
      if (map.getZoom() >= minZoom) setVisibleBounds(map.getBounds());
      else setVisibleBounds(null);
    },
    zoomend() {
      if (map.getZoom() >= minZoom) setVisibleBounds(map.getBounds());
      else setVisibleBounds(null);
    }
  });
  useEffect(() => {
    if (map.getZoom() >= minZoom) setVisibleBounds(map.getBounds());
  }, [map, minZoom, setVisibleBounds]);
  return null;
}

function MapInstanceCapture({ setMap }) {
  const map = useMap();
  useEffect(() => { if (map) setMap(map); }, [map, setMap]);
  return null;
}

function RectangleDrawer({ onRectangleDrawn, enabled }) {
  const map = useMap();
  const drawingRef = useRef(false);
  const startRef = useRef(null);
  const rectRef = useRef(null);

  useEffect(() => {
    if (!enabled) {
      map.dragging.enable();
      return;
    }

    const onMouseDown = (e) => {
      if (!enabled) return;
      drawingRef.current = true;
      startRef.current = e.latlng;
      map.dragging.disable();

      if (rectRef.current) {
        map.removeLayer(rectRef.current);
        rectRef.current = null;
      }
    };

    const onMouseMove = (e) => {
      if (!drawingRef.current || !startRef.current) return;

      const bounds = [
        [startRef.current.lat, startRef.current.lng],
        [e.latlng.lat, e.latlng.lng]
      ];

      if (rectRef.current) {
        rectRef.current.setBounds(bounds);
      } else {
        rectRef.current = L.rectangle(bounds, {
          color: '#00E5FF',
          weight: 2,
          fillOpacity: 0.15,
          fillColor: '#00E5FF',
          dashArray: '6, 4'
        }).addTo(map);
      }
    };

    const onMouseUp = (e) => {
      if (!drawingRef.current || !startRef.current) return;
      drawingRef.current = false;
      map.dragging.enable();

      const start = startRef.current;
      const end = e.latlng;
      startRef.current = null;

      const latDiff = Math.abs(start.lat - end.lat);
      const lngDiff = Math.abs(start.lng - end.lng);
      if (latDiff < 0.001 || lngDiff < 0.001) {
        if (rectRef.current) {
          map.removeLayer(rectRef.current);
          rectRef.current = null;
        }
        return;
      }

      const bbox = {
        minLat: Math.min(start.lat, end.lat),
        maxLat: Math.max(start.lat, end.lat),
        minLng: Math.min(start.lng, end.lng),
        maxLng: Math.max(start.lng, end.lng),
      };

      onRectangleDrawn(bbox);
    };

    map.on('mousedown', onMouseDown);
    map.on('mousemove', onMouseMove);
    map.on('mouseup', onMouseUp);

    return () => {
      map.off('mousedown', onMouseDown);
      map.off('mousemove', onMouseMove);
      map.off('mouseup', onMouseUp);
      map.dragging.enable();
      if (rectRef.current) {
        map.removeLayer(rectRef.current);
        rectRef.current = null;
      }
    };
  }, [map, enabled, onRectangleDrawn]);

  return null;
}

function getCandidateColor(score) {
  if (score >= 200) return '#00E5FF';
  if (score >= 100) return '#7C4DFF';
  return '#B0BEC5';
}

function getScoreLabel(score) {
  if (score >= 200) return 'Excellent';
  if (score >= 150) return 'Good';
  if (score >= 100) return 'Moderate';
  if (score >= 50) return 'Low';
  return 'Poor';
}

export default function App() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);
  const [toast, setToast] = useState('');
  const [geoData, setGeoData] = useState(null);

  const [mapCenter, setMapCenter] = useState([52.0, 19.0]);
  const [mapZoom, setMapZoom] = useState(6);
  const [visibleBounds, setVisibleBounds] = useState(null);
  const [allPoints, setAllPoints] = useState([]);
  const [mapInstance, setMapInstance] = useState(null);
  const [drawingMode, setDrawingMode] = useState(false);
  const [siteSelectionResult, setSiteSelectionResult] = useState(null);
  const [siteSelectionLoading, setSiteSelectionLoading] = useState(false);
  const [siteSelectionBbox, setSiteSelectionBbox] = useState(null);
  const [analysisProgress, setAnalysisProgress] = useState(0);
  const [explodingPoints, setExplodingPoints] = useState(new Set());
  const [dronePoints, setDronePoints] = useState(new Set());
  const [droneDivePoints, setDroneDivePoints] = useState(new Set());
  const [firePoints, setFirePoints] = useState(new Set());

  const handleDestroy = (point) => {
    if (mapInstance) mapInstance.closePopup();
    const pointId = point.id || `${point.latitude}-${point.longitude}`;

    const audio = new Audio('/boom.mp3');
    audio.play().catch(e => console.warn('Audio play failed', e));

    setDronePoints(prev => new Set(prev).add(pointId));

    setTimeout(() => {
      setDroneDivePoints(prev => new Set(prev).add(pointId));
      setExplodingPoints(prev => new Set(prev).add(pointId));
    }, 4000);

    setTimeout(() => {
      setDronePoints(prev => {
        const next = new Set(prev);
        next.delete(pointId);
        return next;
      });
      setDroneDivePoints(prev => {
        const next = new Set(prev);
        next.delete(pointId);
        return next;
      });
      setFirePoints(prev => new Set(prev).add(pointId));
    }, 4500);

    setTimeout(() => {
      setAllPoints(prev => prev.filter(p => (p.id || `${p.latitude}-${p.longitude}`) !== pointId));
      setSiteSelectionResult(prev => {
        if (!prev) return null;
        return {
          ...prev,
          competitorLockers: prev.competitorLockers?.filter(c => (c.id || `${c.latitude}-${c.longitude}`) !== pointId)
        };
      });
      setFirePoints(prev => {
        const next = new Set(prev);
        next.delete(pointId);
        return next;
      });
      setExplodingPoints(prev => {
        const next = new Set(prev);
        next.delete(pointId);
        return next;
      });
    }, 6000);
  };

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      onConnect: () => {
        client.subscribe('/topic/analysis-progress', (message) => {
          const data = JSON.parse(message.body);
          if (data.type === 'PARTIAL_RESULTS') {
            setSiteSelectionResult(prev => ({
              ...prev,
              candidates: data.candidates,
              totalCandidates: data.candidates.length
            }));
            setAnalysisProgress(data.progress);
          }
        });
      },
      debug: (str) => { }
    });

    client.activate();
    return () => { client.deactivate(); };
  }, []);

  const [gfBonus, setGfBonus] = useState(50);
  const [accessBonus, setAccessBonus] = useState(25);
  const [gfRadius, setGfRadius] = useState(5.0);
  const [compWeight, setCompWeight] = useState(0.5);

  useEffect(() => {
    fetch(GEOJSON_URL)
      .then(res => res.json())
      .then(data => setGeoData(data))
      .catch(err => console.error('Failed to load borders:', err));
  }, []);

  const showToast = useCallback((msg) => {
    setToast(msg);
    setTimeout(() => setToast(''), 4000);
  }, []);

  useEffect(() => {
    loadStats();
    api.fetchPoints().then(setAllPoints).catch(console.error);
  }, []);



  async function loadStats() {
    try {
      const s = await api.fetchPointStats();
      setStats(s);
    } catch (e) {
      console.warn('Stats not available:', e.message);
    }
  }

  async function handleIngest() {
    setLoading(true);
    showToast('Ingesting data from InPost API...');
    try {
      const result = await api.ingestPoints();
      showToast(`Imported ${formatNumber(result.imported)} points!`);
      await loadStats();
    } catch (e) {
      showToast('Ingestion failed: ' + e.message);
    } finally {
      setLoading(false);
    }
  }

  const handleRectangleDrawn = useCallback(async (bbox) => {
    const latDiff = bbox.maxLat - bbox.minLat;
    const lngDiff = bbox.maxLng - bbox.minLng;

    if (latDiff > 0.1 || lngDiff > 0.15) {
      showToast('Area too large! Please select a smaller zone (max ~10km).');
      setDrawingMode(true);
      return;
    }

    setSiteSelectionBbox(bbox);
    setSiteSelectionLoading(true);
    setSiteSelectionResult(null);
    setDrawingMode(false);
    showToast('Analyzing area — fetching data from OpenStreetMap...');

    const settings = {
      greenfieldBonus: gfBonus,
      access247Bonus: accessBonus,
      greenfieldRadiusKm: gfRadius,
      competitorWeight: compWeight
    };

    try {
      const result = await api.analyzeSiteSelection(bbox, settings);
      setSiteSelectionResult(result);
      showToast(`Found ${result.totalCandidates} commercial locations! ${result.greenfield ? 'Greenfield territory!' : ''}`);
    } catch (e) {
      showToast('Analysis failed: ' + e.message);
    } finally {
      setSiteSelectionLoading(false);
    }
  }, [showToast, gfBonus, accessBonus, gfRadius]);

  const visiblePoints = useMemo(() => {
    if (!visibleBounds || allPoints.length === 0) return [];
    return allPoints.filter(p => visibleBounds.contains([p.latitude, p.longitude]));
  }, [allPoints, visibleBounds]);

  const totalPoints = stats?.totalPoints || 0;
  const countries = stats?.countries?.length || 0;

  const getCountryStyle = (feature) => {
    const iso2 = feature.properties.iso_a2;
    const hasInPost = stats?.countries?.includes(iso2);

    return {
      fillColor: hasInPost ? '#FFD100' : 'transparent',
      weight: 1,
      opacity: 1,
      color: 'rgba(255,255,255,0.2)',
      fillOpacity: hasInPost ? 0.1 : 0,
      interactive: false
    };
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="logo">
            <div className="logo-icon">
              <img src="/favicon.png" alt="Neviim Logo" style={{ width: '70%', height: '70%', objectFit: 'contain', borderRadius: '2px' }} />
            </div>
            <h1>InPost Neviim</h1>
          </div>
          <div className="logo-subtitle">The Parcel Prophet (Auto-Distributor)</div>
        </div>

        <div className="sidebar-top-actions">
          <button
            className={`btn ${drawingMode ? 'btn-drawing' : 'btn-primary'}`}
            onClick={() => {
              setDrawingMode(!drawingMode);
              if (!drawingMode) {
                setSiteSelectionResult(null);
                setSiteSelectionBbox(null);
              }
            }}
            disabled={siteSelectionLoading}
            id="btn-draw"
          >
            {drawingMode ? 'Click & drag on map to select area' : 'Select Area on Map'}
          </button>

          <button
            className="btn btn-secondary"
            onClick={handleIngest}
            disabled={loading}
            id="btn-ingest"
          >
            {loading ? 'Fetching Data...' : 'Fetch Data ~10min'}
          </button>

          {drawingMode && (
            <p className="site-selection-hint">
              Click and drag on the map to draw a rectangle.
            </p>
          )}

          {siteSelectionLoading && (
            <div className="analysis-progress-container" style={{ margin: 0, marginTop: '8px' }}>
              <div className="progress-bar-bg">
                <div className="progress-bar-fill" style={{ width: `${analysisProgress}%` }}></div>
              </div>
              <span className="progress-text">Analyzing: {analysisProgress}%</span>
            </div>
          )}
        </div>

        <div className="sidebar-scroll-area">

          <div className="stats-grid">
            <div className="stat-card yellow">
              <div className="stat-label">Network Size</div>
              <div className="stat-value yellow">{formatNumber(totalPoints)}</div>
            </div>
            <div className="stat-card blue">
              <div className="stat-label">Countries</div>
              <div className="stat-value blue">{countries}</div>
            </div>
            <div className="stat-card green">
              <div className="stat-label">POIs Found</div>
              <div className="stat-value green">
                {siteSelectionResult ? formatNumber(siteSelectionResult.totalCandidates) : '—'}
              </div>
            </div>
            <div className="stat-card red">
              <div className="stat-label">Existing Lockers</div>
              <div className="stat-value red">
                {siteSelectionResult ? siteSelectionResult.existingLockers : '—'}
              </div>
            </div>
          </div>


          <div className="sidebar-section">
            <div className="section-title"><span className="dot" />Site Selection Settings</div>

            <div className="control-group">
              <label className="control-label"><span>Greenfield Bonus</span><span>{gfBonus}</span></label>
              <p className="setting-description">Extra points for territory without existing InPost lockers.</p>
              <input type="range" className="slider" min="0" max="100" step="5" value={gfBonus} onChange={(e) => setGfBonus(parseInt(e.target.value))} />
            </div>

            <div className="control-group">
              <label className="control-label"><span>24/7 Access Bonus</span><span>{accessBonus}</span></label>
              <p className="setting-description">Priority for 24/7 locations (fuel stations, kiosks).</p>
              <input type="range" className="slider" min="0" max="100" step="5" value={accessBonus} onChange={(e) => setAccessBonus(parseInt(e.target.value))} />
            </div>

            <div className="control-group">
              <label className="control-label"><span>Greenfield Radius</span><span>{gfRadius} km</span></label>
              <p className="setting-description">Radius for calculating locker-free territory score.</p>
              <input type="range" className="slider" min="1" max="20" step="1" value={gfRadius} onChange={(e) => setGfRadius(parseFloat(e.target.value))} />
            </div>

            <div className="control-group">
              <div className="control-label">
                <span>Competitor Weight</span>
                <span className="setting-value">{compWeight === 0 ? 'Low' : compWeight === 1 ? 'High' : 'Normal'}</span>
              </div>
              <input
                type="range"
                className="slider"
                min="0"
                max="1"
                step="0.5"
                value={compWeight}
                onChange={(e) => setCompWeight(parseFloat(e.target.value))}
              />
              <p className="setting-description">How strongly to penalize proximity to DHL/DPD machines.</p>
            </div>
          </div>

          <div className="sidebar-section">

            {siteSelectionResult && (
              <div className="site-selection-results">
                <div className="ssr-header">
                  <div className="ssr-area-name">{siteSelectionResult.areaName}</div>
                  {siteSelectionResult.greenfield && (
                    <div className="greenfield-badge">Greenfield</div>
                  )}
                </div>

                <div className="ssr-stats">
                  <div className="ssr-stat">
                    <span className="ssr-stat-value">{siteSelectionResult.totalCandidates}</span>
                    <span className="ssr-stat-label">POIs Found</span>
                  </div>
                  <div className="ssr-stat">
                    <span className="ssr-stat-value">{siteSelectionResult.existingLockers}</span>
                    <span className="ssr-stat-label">Existing Lockers</span>
                  </div>
                  {siteSelectionResult.ruralMode && (
                    <div className="ssr-stat" style={{ borderLeft: '1px solid var(--border)', paddingLeft: '12px' }}>
                      <span className="ssr-stat-value" style={{ color: '#448AFF', fontSize: '11px' }}>
                        {siteSelectionResult.optimalLat ? `${siteSelectionResult.optimalLat.toFixed(5)}, ${siteSelectionResult.optimalLng.toFixed(5)}` : 'Calculating...'}
                      </span>
                      <span className="ssr-stat-label">Geometric Center (Rural)</span>
                    </div>
                  )}
                </div>

                {siteSelectionResult?.candidates?.length > 0 && (
                  <>
                    <div className="ssr-candidates-title">Top Candidates</div>
                    <ul className="candidate-list">
                      {siteSelectionResult.candidates.map((c, i) => (
                        <li
                          key={i}
                          className="candidate-item"
                          onClick={() => { setMapCenter([c.latitude, c.longitude]); setMapZoom(17); }}
                        >
                          <div className="candidate-rank" style={{ background: getCandidateColor(c.totalScore) }}>
                            {c.rank}
                          </div>
                          <div className="candidate-info">
                            <div className="candidate-name">{c.name || 'Unknown'}</div>
                            <div className="candidate-type">{c.brand ? `${c.brand} · ` : ''}{c.type}</div>
                            <div className="candidate-reason">{c.reason}</div>
                          </div>
                          <div className="candidate-score">
                            <div className="score-value">{c.totalScore}</div>
                            <div className="score-label" style={{ color: getCandidateColor(c.totalScore) }}>{getScoreLabel(c.totalScore)}</div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </>
                )}

                {(!siteSelectionResult?.candidates || siteSelectionResult.candidates.length === 0) && (
                  <div className="ssr-empty">No commercial POIs or suitable buildings found in this area. Try a larger zone.</div>
                )}

              </div>
            )}
          </div>
        </div>

      </aside>

      <main className="map-container">
        <MapContainer
          center={[52.0, 19.0]}
          zoom={6}
          style={{ height: '100%', width: '100%' }}
          zoomControl={false}
        >
          <ZoomControl position="topleft" />
          <MapUpdater center={mapCenter} zoom={mapZoom} />
          <MapViewportTracker setVisibleBounds={setVisibleBounds} minZoom={11} />
          <MapInstanceCapture setMap={setMapInstance} />
          <RectangleDrawer onRectangleDrawn={handleRectangleDrawn} enabled={drawingMode} />

          <TileLayer
            attribution='&copy; Esri'
            url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          />
          <TileLayer
            attribution='&copy; Esri'
            url="https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
            pane="overlayPane"
          />
          {geoData && (
            <GeoJSON data={geoData} style={getCountryStyle} />
          )}

          <div className="map-legend-overlay">
            <div className="legend-item"><span className="dot yellow"></span> Existing InPost Locker</div>
            <div className="legend-item"><span className="dot failure"></span> Competitor Locker</div>
            <div className="legend-item"><span className="dot" style={{ backgroundColor: '#00E5FF' }}></span> Excellent Site (200+)</div>
            <div className="legend-item"><span className="dot" style={{ backgroundColor: '#7C4DFF' }}></span> Good Site (100-200)</div>
            <div className="legend-item"><span className="dot" style={{ backgroundColor: '#B0BEC5' }}></span> Low Priority</div>
          </div>

          {/* Drawing mode indicator */}
          {drawingMode && (
            <div className="map-drawing-indicator">
              <div className="drawing-pulse" />
              Click and drag to select area
            </div>
          )}

          {/* Selection bbox */}
          {siteSelectionBbox && (
            <Rectangle
              bounds={[
                [siteSelectionBbox.minLat, siteSelectionBbox.minLng],
                [siteSelectionBbox.maxLat, siteSelectionBbox.maxLng]
              ]}
              pathOptions={{
                color: '#00E5FF',
                weight: 2,
                fillOpacity: 0.08,
                fillColor: '#00E5FF',
                dashArray: '8, 4'
              }}
            />
          )}

          {/* Optimal Geometric Center (Only in Rural Mode) */}
          {siteSelectionResult?.ruralMode && siteSelectionResult?.optimalLat > 0 && (
            <CircleMarker
              center={[siteSelectionResult.optimalLat, siteSelectionResult.optimalLng]}
              radius={10}
              pathOptions={{
                fillColor: '#448AFF',
                fillOpacity: 0.6,
                color: '#fff',
                weight: 2,
                dashArray: '5, 5'
              }}
            >
              <Popup>
                <div style={{ textAlign: 'center' }}>
                  <strong style={{ color: '#448AFF' }}>Geometric Center</strong><br />
                  Best cluster point for rural area
                </div>
              </Popup>
            </CircleMarker>
          )}

          {/* Global visible lockers when zoomed in */}
          <Pane name="coveragePane" style={{ opacity: 0.15, pointerEvents: 'none' }}>
            {visiblePoints.map((p, i) => (
              <Circle
                key={`coverage-${p.name}`}
                center={[p.latitude, p.longitude]}
                radius={1000}
                pathOptions={{ fillColor: '#FFD100', fillOpacity: 1, color: 'transparent', weight: 0, interactive: false }}
              />
            ))}
          </Pane>

          {/* Background candidate circles (non-interactive, default pane) */}
          {siteSelectionResult?.candidates?.map((c, i) => (
            <CircleMarker
              key={`ss-candidate-bg-${i}`}
              center={[c.latitude, c.longitude]}
              radius={20}
              pathOptions={{
                fillColor: getCandidateColor(c.totalScore),
                fillOpacity: 0.2,
                color: getCandidateColor(c.totalScore),
                weight: 1,
                dashArray: '4, 4'
              }}
              interactive={false}
            />
          ))}

          {/* High-priority interactive markers pane */}
          <Pane name="markerPaneTop" className="leaflet-marker-pane-top">
            {/* Existing InPost Lockers */}
            {visiblePoints.map((p, i) => (
              <CircleMarker
                key={`vp-${p.name}`}
                center={[p.latitude, p.longitude]}
                radius={10}
                className={explodingPoints.has(p.id || `${p.latitude}-${p.longitude}`) ? 'exploding' : ''}
                pathOptions={{ fillColor: '#FFD100', fillOpacity: 0.9, color: '#FFFFFF', weight: 1.5 }}
                bubblingMouseEvents={true}
              >
                <Popup>
                  <LockerPopup point={p} onDestroy={handleDestroy} />
                </Popup>
              </CircleMarker>
            ))}

            {/* Competitor Lockers */}
            {siteSelectionResult?.competitorLockers?.map((c, i) => {
              const pid = c.id || `${c.latitude}-${c.longitude}`;
              return (
                <React.Fragment key={`comp-${i}`}>
                  {dronePoints.has(pid) && (
                    <Marker
                      position={[c.latitude, c.longitude]}
                      icon={L.divIcon({
                        className: 'drone-container',
                        html: `<div class="drone-marker ${droneDivePoints.has(pid) ? 'drone-diving' : ''}" style="transform: rotate(90deg);">
                                 <svg viewBox="0 0 24 24" fill="#FFD100">
                                   <path d="M21,11h-8l-1-7l-1,7H3v2h8l1,7l1-7h8V11z"/>
                                 </svg>
                               </div>`,
                        iconSize: [30, 30],
                        iconAnchor: [15, 15]
                      })}
                    />
                  )}
                  {firePoints.has(pid) && (
                    <Marker
                      position={[c.latitude, c.longitude]}
                      icon={L.divIcon({
                        className: 'fire-container',
                        html: `<div class="fire-explosion"></div>`,
                        iconSize: [40, 40],
                        iconAnchor: [20, 20]
                      })}
                    />
                  )}
                  <CircleMarker
                    center={[c.latitude, c.longitude]}
                    radius={10}
                    className={explodingPoints.has(pid) ? 'shaking-target' : ''}
                    pathOptions={{
                      fillColor: firePoints.has(pid) ? 'transparent' : '#FF5252',
                      fillOpacity: 0.9,
                      color: '#FFFFFF',
                      weight: 1.5
                    }}
                    bubblingMouseEvents={true}
                  >
                    <Popup>
                      <LockerPopup point={c} onDestroy={handleDestroy} />
                    </Popup>
                  </CircleMarker>
                </React.Fragment>
              );
            })}

            {/* Candidate Centers */}
            {siteSelectionResult?.candidates?.map((c, i) => (
              <CircleMarker
                key={`ss-candidate-center-${i}`}
                center={[c.latitude, c.longitude]}
                radius={10}
                pathOptions={{
                  fillColor: getCandidateColor(c.totalScore),
                  fillOpacity: 1,
                  color: '#FFFFFF',
                  weight: 2
                }}
              >
                <Popup>
                  <div style={{ fontFamily: 'Inter', fontSize: '13px', width: '220px' }}>
                    <strong style={{ fontSize: '14px' }}>#{c.rank} — {c.name}</strong><br />
                    <span style={{ color: '#666' }}>{c.type}</span><br />
                    {c.address && <div style={{ marginTop: '4px', fontWeight: 'bold', color: '#333' }}>{c.address}</div>}
                    <div style={{ marginTop: '8px' }}>
                      <strong>Score: {c.totalScore}</strong> ({getScoreLabel(c.totalScore)})
                    </div><br />
                    <span style={{ fontSize: '10px', color: '#aaa' }}>
                      Distance: {c.distanceScore} · Type: {c.typeScore} · Brand: {c.brandScore} · Access: {c.accessScore}
                      {c.greenfieldBonus > 0 ? ` · +${c.greenfieldBonus} GF` : ''}
                    </span><br />
                    {c.distanceToNearestLockerKm >= 0
                      ? <span>Nearest locker: {(c.distanceToNearestLockerKm * 1000).toFixed(0)}m</span>
                      : <span style={{ color: '#00E676' }}>No lockers nearby — virgin territory</span>
                    }<br />
                    {c.openingHours && <span>Hours: {c.openingHours}</span>}
                  </div>
                </Popup>
              </CircleMarker>
            ))}
          </Pane>
        </MapContainer>

        {(loading || siteSelectionLoading) && (
          <div className="loading-overlay">
            <div className="spinner" />
            <div className="loading-text">
              {siteSelectionLoading
                ? 'Fetching real data from OpenStreetMap...'
                : 'Ingesting from InPost API...'}
            </div>
          </div>
        )}
      </main>

      <div className={`toast ${toast ? 'visible' : ''}`}>{toast}</div>
    </div>
  );
}
