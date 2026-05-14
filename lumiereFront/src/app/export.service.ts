import { Injectable } from '@angular/core';
import * as ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';

@Injectable({ providedIn: 'root' })
export class ExportService {

    constructor() { }

    /**
     * col2Letter: converts a 1-based column index to an Excel letter (1→A, 2→B, …, 26→Z, 27→AA)
     */
    private col2Letter(col: number): string {
        let letter = '';
        while (col > 0) {
            const rem = (col - 1) % 26;
            letter = String.fromCharCode(65 + rem) + letter;
            col = Math.floor((col - 1) / 26);
        }
        return letter;
    }

    async exportExcel(data: any[], title: string, fileName: string, columns: any[]) {
        const workbook = new ExcelJS.Workbook();
        workbook.creator = 'OTFLOW Transport';
        (workbook as any).company = 'OTFLOW Logistique';
        workbook.created = new Date();

        const worksheet = workbook.addWorksheet(title, {
            pageSetup: { paperSize: 9, orientation: 'landscape', fitToPage: true }
        });

        // Total columns = 1 (# row-number col) + data columns
        const dataCols = columns.length;
        const totalCols = dataCols + 1;
        const lastCol = this.col2Letter(totalCols); // e.g. 'G' for 6 data cols

        // ── Column widths ─────────────────────────────────────────────────────────
        worksheet.getColumn(1).width = 6;  // # col
        columns.forEach((col, i) => {
            worksheet.getColumn(i + 2).width = col.width || 22;
        });

        // ── Rows 1-3: Full-width orange banner (logo centered) ───────────────
        for (let r = 1; r <= 3; r++) {
            worksheet.getRow(r).height = 26;
            for (let c = 1; c <= totalCols; c++) {  // ALL columns — full width
                worksheet.getRow(r).getCell(c).fill = {
                    type: 'pattern', pattern: 'solid',
                    fgColor: { argb: 'FFF5921E' }  // OTFLOW orange
                };
            }
        }

        // ── Logo image (centered horizontally in the banner) ─────────────────
        try {
            const res = await fetch('assets/otflow-horizontal.png');
            const ab = await (await res.blob()).arrayBuffer();
            const logoId = workbook.addImage({ buffer: ab, extension: 'png' });
            // Center: place logo at the mid-point of all columns
            const midCol = Math.floor(totalCols / 2) - 1;
            worksheet.addImage(logoId, {
                tl: { col: midCol, row: 0.15 },
                ext: { width: 130, height: 70 }
            });
        } catch (e) { }

        // ── Row 4: Dark separator under the logo block (full width) ────────────
        worksheet.mergeCells(`A4:${lastCol}4`);
        worksheet.getCell('A4').fill = {
            type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF1E293B' }
        };
        worksheet.getRow(4).height = 3;

        // ── Row 5: spacer ─────────────────────────────────────────────────────
        worksheet.getRow(5).height = 10;

        // ── Row 6: Document title (full width, centered) ───────────────────────
        worksheet.mergeCells(`A6:${lastCol}6`);
        const titleCell = worksheet.getCell('A6');
        titleCell.value = title.toUpperCase();
        titleCell.font = { name: 'Calibri', size: 20, bold: true, color: { argb: 'FF1E293B' } };
        titleCell.alignment = { horizontal: 'center', vertical: 'middle' };
        titleCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF8FAFC' } };
        worksheet.getRow(6).height = 34;

        // ── Row 7: Sub-info (date, company) ───────────────────────────────────
        worksheet.mergeCells(`A7:${lastCol}7`);
        const infoCell = worksheet.getCell('A7');
        const dateStr = new Date().toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
        infoCell.value = `OTFLOW Logistique  —  Exporté le ${dateStr}`;
        infoCell.font = { name: 'Calibri', size: 10, italic: true, color: { argb: 'FF64748B' } };
        infoCell.alignment = { horizontal: 'center', vertical: 'middle' };
        infoCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF8FAFC' } };
        worksheet.getRow(7).height = 20;

        // ── Row 8: Orange separator ────────────────────────────────────────────
        worksheet.mergeCells(`A8:${lastCol}8`);
        worksheet.getCell('A8').fill = {
            type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF5921E' }
        };
        worksheet.getRow(8).height = 4;

        // ── Row 9: spacer ─────────────────────────────────────────────────────
        worksheet.getRow(9).height = 6;

        // ── Row 10: Table headers ──────────────────────────────────────────────
        const headerRow = worksheet.getRow(10);
        headerRow.height = 26;
        headerRow.values = ['#', ...columns.map((c: any) => c.header)];

        headerRow.eachCell(cell => {
            cell.font = { name: 'Calibri', bold: true, size: 11, color: { argb: 'FFFFFFFF' } };
            cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF1E293B' } };
            cell.alignment = { horizontal: 'center', vertical: 'middle' };
            cell.border = { right: { style: 'thin', color: { argb: 'FF334155' } } };
        });

        // ── Data rows ──────────────────────────────────────────────────────────
        data.forEach((item, idx) => {
            const rowVals = [idx + 1, ...columns.map((c: any) => item[c.key] ?? '')];
            const row = worksheet.addRow(rowVals);
            row.height = 20;

            // Zebra
            row.fill = {
                type: 'pattern', pattern: 'solid',
                fgColor: { argb: idx % 2 === 0 ? 'FFFFFFFF' : 'FFF8FAFC' }
            };

            row.eachCell((cell, colNum) => {
                cell.font = { name: 'Calibri', size: 10.5, color: { argb: 'FF1E293B' } };
                cell.alignment = { vertical: 'middle', horizontal: colNum === 1 ? 'center' : 'left', indent: colNum === 1 ? 0 : 1 };
                cell.border = {
                    bottom: { style: 'thin', color: { argb: 'FFE2E8F0' } },
                    right: { style: 'thin', color: { argb: 'FFE2E8F0' } }
                };
            });
        });

        // ── Summary row ───────────────────────────────────────────────────────
        const sumRow = worksheet.addRow(['', `Total : ${data.length} enregistrement(s)`]);
        worksheet.mergeCells(`B${sumRow.number}:${lastCol}${sumRow.number}`);
        sumRow.height = 18;
        sumRow.getCell(2).font = { name: 'Calibri', size: 10, italic: true, bold: true, color: { argb: 'FF64748B' } };
        sumRow.getCell(2).alignment = { horizontal: 'right', vertical: 'middle' };

        // ── Footer separator ──────────────────────────────────────────────────
        const sepRow = worksheet.addRow([]);
        worksheet.mergeCells(`A${sepRow.number}:${lastCol}${sepRow.number}`);
        sepRow.height = 4;
        sepRow.getCell(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF5921E' } };

        const footRow = worksheet.addRow([]);
        worksheet.mergeCells(`A${footRow.number}:${lastCol}${footRow.number}`);
        footRow.height = 16;
        footRow.getCell(1).value = 'OTFLOW Logistique — Document confidentiel — Généré automatiquement';
        footRow.getCell(1).font = { name: 'Calibri', size: 9, italic: true, color: { argb: 'FF94A3B8' } };
        footRow.getCell(1).alignment = { horizontal: 'center' };

        // ── Generate file ──────────────────────────────────────────────────────
        const buffer = await workbook.xlsx.writeBuffer();
        saveAs(new Blob([buffer]), `${fileName}.xlsx`);
    }
}



