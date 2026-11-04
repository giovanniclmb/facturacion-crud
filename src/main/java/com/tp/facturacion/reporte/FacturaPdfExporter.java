package com.tp.facturacion.reporte;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import com.tp.facturacion.dto.FacturaReporteDto;

@Component
public class FacturaPdfExporter {

    private static final String[] ENCABEZADOS = {
        "N° local", "Fecha", "Cliente", "Condición IVA",
        "Punto de venta", "Importe total", "Ítems", "N° fiscal", "Estado fiscal"
    };

    public byte[] exportar(List<FacturaReporteDto> facturas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        Document documento = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(documento, salida);
        documento.open();

        Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font fuenteTexto = FontFactory.getFont(FontFactory.HELVETICA, 9);

        // Título y fecha de generación
        Paragraph titulo = new Paragraph("Reporte ejecutivo de ventas", fuenteTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        documento.add(titulo);

        Paragraph generado = new Paragraph(
                "Generado el " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()),
                fuenteTexto);
        generado.setAlignment(Element.ALIGN_CENTER);
        generado.setSpacingAfter(12);
        documento.add(generado);

        // Tabla
        PdfPTable tabla = new PdfPTable(ENCABEZADOS.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] {0.8f, 1.1f, 2f, 1.5f, 1.4f, 1.3f, 0.6f, 2f, 1.5f});
        tabla.setHeaderRows(1); // repite el encabezado si la tabla sigue en otra página

        for (String encabezado : ENCABEZADOS) {
            PdfPCell celda = celda(encabezado, fuenteEncabezado, Element.ALIGN_CENTER);
            celda.setBackgroundColor(new Color(220, 220, 220));
            tabla.addCell(celda);
        }

        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));
        SimpleDateFormat formatoFecha = new SimpleDateFormat("dd/MM/yyyy");

        double totalImporte = 0;
        long totalItems = 0;

        for (FacturaReporteDto f : facturas) {
            tabla.addCell(celda(String.valueOf(f.getNumeroFactura()), fuenteTexto, Element.ALIGN_CENTER));
            tabla.addCell(celda(formatoFecha.format(f.getFechaEmision()), fuenteTexto, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.getClienteDenominacion(), fuenteTexto, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.getCondicionIva(), fuenteTexto, Element.ALIGN_LEFT));
            tabla.addCell(celda(f.getPuntoVentaDescripcion(), fuenteTexto, Element.ALIGN_LEFT));
            tabla.addCell(celda(moneda.format(f.getImporteTotal()), fuenteTexto, Element.ALIGN_RIGHT));
            tabla.addCell(celda(String.valueOf(f.getCantidadItems()), fuenteTexto, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.numeroFiscalFormateado(), fuenteTexto, Element.ALIGN_CENTER));
            tabla.addCell(celda(f.estadoFiscalTexto(), fuenteTexto, Element.ALIGN_CENTER));

            totalImporte += f.getImporteTotal();
            totalItems += f.getCantidadItems();
        }

        // Fila de totales: la etiqueta ocupa las 5 primeras columnas
        PdfPCell etiqueta = celda("Total (" + facturas.size() + " facturas)",
                fuenteEncabezado, Element.ALIGN_RIGHT);
        etiqueta.setColspan(5);
        tabla.addCell(etiqueta);
        tabla.addCell(celda(moneda.format(totalImporte), fuenteEncabezado, Element.ALIGN_RIGHT));
        tabla.addCell(celda(String.valueOf(totalItems), fuenteEncabezado, Element.ALIGN_CENTER));
        tabla.addCell(celda("", fuenteTexto, Element.ALIGN_LEFT));
        tabla.addCell(celda("", fuenteTexto, Element.ALIGN_LEFT));

        documento.add(tabla);
        documento.close();

        return salida.toByteArray();
    }

    private PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(5);
        return celda;
    }
}