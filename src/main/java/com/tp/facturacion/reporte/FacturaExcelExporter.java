package com.tp.facturacion.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.tp.facturacion.dto.FacturaReporteDto;

@Component
public class FacturaExcelExporter {

    private static final String[] ENCABEZADOS = {
        "N° Factura", "Fecha", "Cliente", "Condición IVA",
        "Punto de venta", "Importe total", "Ítems"
    };

    public byte[] exportar(List<FacturaReporteDto> facturas) {
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            Sheet hoja = libro.createSheet("Facturas");

            // Estilos
            Font negrita = libro.createFont();
            negrita.setBold(true);

            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            estiloEncabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formato = libro.createDataFormat();

            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(formato.getFormat("dd/mm/yyyy"));

            CellStyle estiloImporte = libro.createCellStyle();
            estiloImporte.setDataFormat(formato.getFormat("#,##0.00"));

            CellStyle estiloTotal = libro.createCellStyle();
            estiloTotal.setFont(negrita);
            estiloTotal.setDataFormat(formato.getFormat("#,##0.00"));

            // Encabezado
            Row filaEncabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell celda = filaEncabezado.createCell(i);
                celda.setCellValue(ENCABEZADOS[i]);
                celda.setCellStyle(estiloEncabezado);
            }

            // Datos
            int numeroFila = 1;
            for (FacturaReporteDto f : facturas) {
                Row fila = hoja.createRow(numeroFila++);

                if (f.getNumeroFactura() != null) {
                    fila.createCell(0).setCellValue(f.getNumeroFactura());
                }

                Cell fecha = fila.createCell(1);
                fecha.setCellValue(f.getFechaEmision());
                fecha.setCellStyle(estiloFecha);

                fila.createCell(2).setCellValue(f.getClienteDenominacion());
                fila.createCell(3).setCellValue(f.getCondicionIva());
                fila.createCell(4).setCellValue(f.getPuntoVentaDescripcion());

                Cell importe = fila.createCell(5);
                importe.setCellValue(f.getImporteTotal());
                importe.setCellStyle(estiloImporte);

                fila.createCell(6).setCellValue(f.getCantidadItems());
            }

            // Totales como fórmulas: Excel los recalcula si se edita una celda
            if (!facturas.isEmpty()) {
                Row filaTotal = hoja.createRow(numeroFila);

                Cell etiqueta = filaTotal.createCell(4);
                etiqueta.setCellValue("Total");
                etiqueta.setCellStyle(estiloEncabezado);

                Cell totalImporte = filaTotal.createCell(5);
                totalImporte.setCellFormula("SUM(F2:F" + numeroFila + ")");
                totalImporte.setCellStyle(estiloTotal);

                Cell totalItems = filaTotal.createCell(6);
                totalItems.setCellFormula("SUM(G2:G" + numeroFila + ")");
                totalItems.setCellStyle(estiloEncabezado);
            }

            hoja.createFreezePane(0, 1); // el encabezado queda fijo al hacer scroll
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                hoja.autoSizeColumn(i);
            }
            libro.setForceFormulaRecalculation(true);

            libro.write(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }
}