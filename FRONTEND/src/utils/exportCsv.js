/**
 * Utility function to export data to CSV format with UTF-8 BOM (supporting Vietnamese characters in Excel)
 */
export function exportToCsv(filename, headers, rows) {
  if (!rows || !rows.length) {
    alert('Không có dữ liệu để xuất file');
    return;
  }

  // UTF-8 BOM (\uFEFF) ensures Excel displays Vietnamese correctly
  const BOM = '\uFEFF';
  const csvContent = [
    headers.map(h => `"${(h || '').toString().replace(/"/g, '""')}"`).join(','),
    ...rows.map(row =>
      row.map(cell => `"${(cell !== null && cell !== undefined ? cell : '').toString().replace(/"/g, '""')}"`).join(',')
    )
  ].join('\r\n');

  const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `${filename}_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
