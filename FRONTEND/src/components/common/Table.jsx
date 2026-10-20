import './Table.css';

/**
 * Compact Data Table with sticky header and column alignments
 */
export default function Table({
  columns = [], // [{ key, title, align: 'left'|'right'|'center', width, render }]
  data = [],
  rowKey = 'id',
  emptyText = 'Không có dữ liệu',
  onRowClick,
  className = '',
  maxHeight
}) {
  return (
    <div
      className={`table-container ${className}`}
      style={maxHeight ? { maxHeight } : undefined}
    >
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                className={`align-${col.align || 'left'}`}
                style={col.width ? { width: col.width } : undefined}
              >
                {col.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                {emptyText}
              </td>
            </tr>
          ) : (
            data.map((row, idx) => (
              <tr
                key={row[rowKey] || idx}
                onClick={() => onRowClick && onRowClick(row, idx)}
                style={{ cursor: onRowClick ? 'pointer' : 'default' }}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={`align-${col.align || 'left'}`}
                  >
                    {col.render
                      ? col.render(row[col.key], row, idx)
                      : row[col.key] ?? '—'}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
