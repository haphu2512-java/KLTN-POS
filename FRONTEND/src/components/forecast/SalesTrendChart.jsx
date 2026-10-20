import { useState } from 'react';
import './SalesTrendChart.css';

/**
 * Crisp, lightweight SVG Trend & Demand Forecast Chart
 */
export default function SalesTrendChart({
  actualData = [],     // number[] (e.g. 14 days)
  forecastData = [],   // number[] (e.g. next 7 days)
  actualLabels = [],   // string[] (e.g. ['03/09', '04/09', ...])
  forecastLabels = [], // string[] (e.g. ['17/09', '18/09', ...])
  unit = 'món',
  title = 'Xu Hướng Bán Hàng & Dự Báo Nhu Cầu'
}) {
  const [hoveredPoint, setHoveredPoint] = useState(null);

  const totalPoints = actualData.length + forecastData.length;
  if (totalPoints === 0) return null;

  // Coordinate math
  const width = 800;
  const height = 200;
  const paddingLeft = 45;
  const paddingRight = 30;
  const paddingTop = 20;
  const paddingBottom = 35;

  const chartWidth = width - paddingLeft - paddingRight;
  const chartHeight = height - paddingTop - paddingBottom;

  const allValues = [...actualData, ...forecastData];
  const maxVal = Math.max(...allValues, 10) * 1.15;

  const getX = (index) => {
    return paddingLeft + (index / (totalPoints - 1)) * chartWidth;
  };

  const getY = (val) => {
    return height - paddingBottom - (val / maxVal) * chartHeight;
  };

  // Build Actual Path
  const actualPath = actualData
    .map((val, idx) => `${idx === 0 ? 'M' : 'L'} ${getX(idx)} ${getY(val)}`)
    .join(' ');

  // Build Forecast Path (connects from last actual point to forecast points)
  const lastActualIndex = actualData.length - 1;
  const forecastPathPoints = [
    { x: getX(lastActualIndex), y: getY(actualData[lastActualIndex]) },
    ...forecastData.map((val, idx) => ({
      x: getX(actualData.length + idx),
      y: getY(val)
    }))
  ];
  const forecastPath = forecastPathPoints
    .map((pt, idx) => `${idx === 0 ? 'M' : 'L'} ${pt.x} ${pt.y}`)
    .join(' ');

  const allLabels = [...actualLabels, ...forecastLabels];

  return (
    <div className="chart-wrapper">
      <div className="chart-header">
        <span className="chart-title">{title}</span>
        <div className="chart-legend">
          <div className="legend-item">
            <span className="legend-color actual" />
            <span>Thực tế ({actualData.length} ngày qua)</span>
          </div>
          <div className="legend-item">
            <span className="legend-color forecast" />
            <span>AI Dự báo ({forecastData.length} ngày tới)</span>
          </div>
        </div>
      </div>

      <div className="chart-svg-container">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="chart-svg"
          preserveAspectRatio="none"
        >
          {/* Y Axis Grid Lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((pct, i) => {
            const y = paddingTop + pct * chartHeight;
            const val = Math.round(maxVal * (1 - pct));
            return (
              <g key={i}>
                <line
                  x1={paddingLeft}
                  y1={y}
                  x2={width - paddingRight}
                  y2={y}
                  className="chart-grid-line"
                />
                <text
                  x={paddingLeft - 8}
                  y={y + 4}
                  className="chart-axis-text"
                  textAnchor="end"
                >
                  {val}
                </text>
              </g>
            );
          })}

          {/* Actual Sales Line */}
          <path d={actualPath} className="chart-actual-line" />

          {/* Forecast Line */}
          <path d={forecastPath} className="chart-forecast-line" />

          {/* Actual Points */}
          {actualData.map((val, idx) => (
            <circle
              key={`act-${idx}`}
              cx={getX(idx)}
              cy={getY(val)}
              r={3}
              className="chart-point actual"
              onMouseEnter={() =>
                setHoveredPoint({
                  x: getX(idx),
                  y: getY(val),
                  label: actualLabels[idx],
                  value: val,
                  type: 'Thực tế'
                })
              }
              onMouseLeave={() => setHoveredPoint(null)}
            />
          ))}

          {/* Forecast Points */}
          {forecastData.map((val, idx) => {
            const pointIndex = actualData.length + idx;
            return (
              <circle
                key={`fore-${idx}`}
                cx={getX(pointIndex)}
                cy={getY(val)}
                r={3.5}
                className="chart-point forecast"
                onMouseEnter={() =>
                  setHoveredPoint({
                    x: getX(pointIndex),
                    y: getY(val),
                    label: forecastLabels[idx],
                    value: val,
                    type: 'Dự báo'
                  })
                }
                onMouseLeave={() => setHoveredPoint(null)}
              />
            );
          })}

          {/* X Axis Labels (Every 2-3 points to prevent clutter) */}
          {allLabels.map((lbl, idx) => {
            if (idx % 2 !== 0 && idx !== allLabels.length - 1) return null;
            return (
              <text
                key={`x-${idx}`}
                x={getX(idx)}
                y={height - 10}
                className="chart-axis-text"
                textAnchor="middle"
                style={idx >= actualData.length ? { fill: 'var(--success-700)', fontWeight: 600 } : {}}
              >
                {lbl}
              </text>
            );
          })}

          {/* Hover Tooltip in SVG */}
          {hoveredPoint && (
            <g transform={`translate(${Math.min(hoveredPoint.x, width - 110)}, ${Math.max(hoveredPoint.y - 45, 10)})`}>
              <rect
                x={-5}
                y={-5}
                width={105}
                height={40}
                rx={4}
                fill="var(--brand-900)"
                opacity="0.92"
              />
              <text x={8} y={12} fill="var(--text-muted)" fontSize="10px" fontFamily="var(--font-sans)">
                {hoveredPoint.label} ({hoveredPoint.type})
              </text>
              <text x={8} y={28} fill="#ffffff" fontSize="13px" fontWeight="bold" fontFamily="var(--font-mono)">
                {hoveredPoint.value} {unit}
              </text>
            </g>
          )}
        </svg>
      </div>
    </div>
  );
}
