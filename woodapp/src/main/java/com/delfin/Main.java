package com.delfin;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Image; 
import com.lowagie.text.pdf.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Vector;

public class Main extends JFrame {
    private JTable table;
    private DefaultTableModel model;
    private JLabel lblToplam, lblKalan;
    private JTextField txtGelenHavale;
    private boolean isAdjusting = false;

    private DecimalFormat dfDesi = new DecimalFormat("0.000", new DecimalFormatSymbols(Locale.US));
    private DecimalFormat dfPara = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("tr", "TR")));

    public Main() {
        setTitle("Çalışır Ahşap V5.0 - Kurumsal Sipariş Sistemi");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- 1. TABLO YAPISI (En/Milim: cm, Boy: m, Çıktı: Desi) ---
        String[] columns = {"Sıra", "Ürün İsmi", "m²", "Adet", "En (cm)", "Milim (cm)", "Boy (m)", "Desi", "Fiyat (₺/Desi)", "Toplam (₺)"};
        model = new DefaultTableModel(columns, 35) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col != 0 && col != 7 && col != 9;
            }
        };
        table = new JTable(model);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        updateSequenceNumbers();

        // --- 2. ÜST MENÜ ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JButton btnSave = new JButton("Projeyi Yedekle");
        styleButton(btnSave, new Color(52, 152, 219));
        btnSave.addActionListener(e -> saveProject());

        JButton btnOpen = new JButton("Yedek Aç");
        styleButton(btnOpen, new Color(155, 89, 182));
        btnOpen.addActionListener(e -> loadProject());

        JButton btnDelete = new JButton("Seçili Satırı Sil");
        styleButton(btnDelete, new Color(231, 76, 60));
        btnDelete.addActionListener(e -> deleteSelectedRow());

        topPanel.add(btnSave); topPanel.add(btnOpen); topPanel.add(btnDelete);

        // --- 3. OTOMATİK HESAPLAMA MOTORU ---
        model.addTableModelListener(e -> {
            if (isAdjusting) return;
            if (e.getType() == TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int col = e.getColumn();
                // Adet, En, Milim, Boy veya Fiyat değiştiğinde hesapla
                if ((col >= 2 && col <= 6) || col == 8) {
                    runAutoCalculate(row);
                }
                updateGrandTotals();
            }
        });

        // --- 4. ALT MUHASEBE PANELİ ---
        JPanel bottomContainer = new JPanel(new GridLayout(1, 2));
        bottomContainer.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputPanel.setBorder(new TitledBorder("Ödeme Bilgisi"));
        txtGelenHavale = new JTextField(12);
        txtGelenHavale.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        txtGelenHavale.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateGrandTotals(); }
            public void removeUpdate(DocumentEvent e) { updateGrandTotals(); }
            public void changedUpdate(DocumentEvent e) { updateGrandTotals(); }
        });
        inputPanel.add(new JLabel("GELEN HAVALE (TL): "));
        inputPanel.add(txtGelenHavale);

        JPanel resultPanel = new JPanel(new GridLayout(3, 1));
        lblToplam = new JLabel("TOPLAM TUTAR: 0,00 TL", SwingConstants.RIGHT);
        lblToplam.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        lblKalan = new JLabel("KALAN BAKİYE: 0,00 TL", SwingConstants.RIGHT);
        lblKalan.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 22));
        lblKalan.setForeground(new Color(192, 57, 43));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnPdf = new JButton("PDF RAPORU OLUŞTUR");
        styleButton(btnPdf, new Color(39, 174, 96));
        btnPdf.addActionListener(e -> generatePDF());
        btnPanel.add(btnPdf);

        resultPanel.add(lblToplam); resultPanel.add(lblKalan); resultPanel.add(btnPanel);
        bottomContainer.add(inputPanel); bottomContainer.add(resultPanel);

        applyCustomStyling();
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void runAutoCalculate(int row) {
        isAdjusting = true;
        try {
            double m2 = parse(model.getValueAt(row, 2));
            double adet = parse(model.getValueAt(row, 3));
            double en = parse(model.getValueAt(row, 4));
            double milim = parse(model.getValueAt(row, 5));
            double boy = parse(model.getValueAt(row, 6));
            double fiyat = parse(model.getValueAt(row, 8));

            double satirToplam = 0;

            // 1. Durum: m2 alanına değer girilmişse desi hesaplanmasın
            if (m2 > 0) {
                model.setValueAt("", row, 7); // Desi hücresini boş bırak
                
                if (fiyat > 0) {
                    satirToplam = m2 * fiyat;
                }
            } 
            // 2. Durum: m2 boşsa ve En, Milim, Boy doldurulmuşsa desi hesaplansın
            else if (en > 0 && milim > 0 && boy > 0) {
                double gecerliAdet = adet > 0 ? adet : 1.0; // Adet girilmemişse 1 kabul et
                
                // En(cm) * Milim(cm) * Boy(m) hesaplamasının dm³ (desi) karşılığı için 10'a bölüyoruz.
                double desi = (gecerliAdet * en * milim * boy) / 10.0;
                model.setValueAt(dfDesi.format(desi), row, 7);
                
                if (fiyat > 0) {
                    satirToplam = desi * fiyat;
                }
            } 
            // 3. Durum: Değerler eksikse desi boş kalsın
            else {
                model.setValueAt("", row, 7);
            }

            // Satırın toplam fiyatını güncelle veya temizle
            if (satirToplam > 0) {
                model.setValueAt(dfPara.format(satirToplam), row, 9);
            } else {
                model.setValueAt("", row, 9);
            }
        } catch (Exception ignored) {}
        isAdjusting = false;
    }

    private void generatePDF() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("PDF Dosyasını Kaydet");
        fileChooser.setSelectedFile(new File("Calisir_Ahsap_Bilgilendirme_Formu.pdf")); // İsim değiştirildi

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".pdf")) path += ".pdf";

            Document doc = new Document(PageSize.A4.rotate());
            try {
                PdfWriter.getInstance(doc, new FileOutputStream(path));
                doc.open();

                String fontPath = System.getProperty("os.name").toLowerCase().contains("win") 
                    ? "C:/Windows/Fonts/arial.ttf" : "/System/Library/Fonts/Supplemental/Arial.ttf";
                BaseFont bf = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                Font boldFont = new Font(bf, 12, Font.BOLD);
                Font normalFont = new Font(bf, 10, Font.NORMAL);

                // --- LOGO EKLEME ---
                try {
                    String logoPath = "logo.png"; 
                    File logoFile = new File(logoPath);
                    if(logoFile.exists()) {
                        Image img = Image.getInstance(logoPath);
                        img.setAlignment(Image.ALIGN_CENTER);
                        img.scaleToFit(200f, 100f);
                        img.setSpacingAfter(15f);
                        doc.add(img);
                    }
                } catch (Exception ignored) {}

                // Başlık Çalışır Ahşap olarak değiştirildi
                doc.add(new Paragraph("ÇALIŞIR AHŞAP BİLGİLENDİRME FORMU", new Font(bf, 16, Font.BOLD)));
                doc.add(new Paragraph("Tarih: " + java.time.LocalDate.now() + "\n\n", normalFont));

                PdfPTable pdfTable = new PdfPTable(10);
                pdfTable.setWidthPercentage(100);
                pdfTable.setWidths(new float[]{0.8f, 3, 1, 1, 1, 1, 1, 1.5f, 1.5f, 2});

                // PDF Başlıkları cm/m bazında uyarlandı
                String[] headers = {"Sira", "Ürun Ismi", "m²", "Adet", "En (cm)", "Milim (cm)", "Boy (m)", "Desi", "Fiyat", "Toplam"};
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, boldFont));
                    cell.setBackgroundColor(new Color(230, 230, 230));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    pdfTable.addCell(cell);
                }

                for (int r = 0; r < model.getRowCount(); r++) {
                    if (parse(model.getValueAt(r, 3)) > 0 || parse(model.getValueAt(r, 2)) > 0 || (model.getValueAt(r, 1) != null && !model.getValueAt(r, 1).toString().isEmpty())) {
                        for (int c = 0; c < 10; c++) {
                            Object v = model.getValueAt(r, c);
                            PdfPCell cCell = new PdfPCell(new Phrase(v == null ? "" : v.toString(), normalFont));
                            cCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                            pdfTable.addCell(cCell);
                        }
                    }
                }
                doc.add(pdfTable);
                
                PdfPTable summaryTable = new PdfPTable(1);
                summaryTable.setWidthPercentage(50);
                PdfPCell cell1 = new PdfPCell(new Phrase(lblToplam.getText(), boldFont));
                cell1.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell1.setBackgroundColor(new Color(240, 240, 240));
                
                PdfPCell cell2 = new PdfPCell(new Phrase("GELEN ÖDEME: " + txtGelenHavale.getText() + " TL", boldFont));
                cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell2.setBackgroundColor(new Color(220, 240, 220));
                
                PdfPCell cell3 = new PdfPCell(new Phrase(lblKalan.getText(), boldFont));
                cell3.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell3.setBackgroundColor(new Color(255, 220, 220));
                
                summaryTable.addCell(cell1);
                summaryTable.addCell(cell2);
                summaryTable.addCell(cell3);
                doc.add(new Paragraph("\n"));
                doc.add(summaryTable);
                doc.close();
                Desktop.getDesktop().open(new File(path));
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "PDF Hatasi!"); }
        }
    }

    private double parse(Object obj) {
        if (obj == null || obj.toString().trim().isEmpty()) return 0;
        try { return Double.parseDouble(obj.toString().replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private void updateGrandTotals() {
        double genelToplam = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            Object val = model.getValueAt(i, 9);
            if (val != null && !val.toString().trim().isEmpty()) {
                try {
                    String s = val.toString().replace(".", "").replace(",", ".");
                    genelToplam += Double.parseDouble(s);
                } catch (Exception ignored) {}
            }
        }
        double gelen = parse(txtGelenHavale.getText());
        lblToplam.setText("TOPLAM TUTAR: " + dfPara.format(genelToplam) + " TL");
        lblKalan.setText("KALAN BAKIYE: " + dfPara.format(genelToplam - gelen) + " TL");
    }

    private void deleteSelectedRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            isAdjusting = true;
            model.removeRow(selectedRow);
            if (model.getRowCount() < 10) model.addRow(new Object[10]);
            updateSequenceNumbers();
            isAdjusting = false;
            updateGrandTotals();
        }
    }

    private void updateSequenceNumbers() {
        for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(i + 1, i, 0);
    }

    private void saveProject() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if(!f.getName().endsWith(".delfin")) f = new File(f.getAbsolutePath() + ".delfin");
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
                out.writeObject(model.getDataVector());
                out.writeObject(txtGelenHavale.getText());
                JOptionPane.showMessageDialog(this, "Proje Yedeklendi!");
            } catch (Exception ignored) { }
        }
    }

    private void loadProject() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(fc.getSelectedFile()))) {
                Vector data = (Vector) in.readObject();
                String havale = (String) in.readObject();
                // Proje yuklenirken sütun başlıkları cm/m olarak uyarlandı
                String[] cols = {"Sira", "Ürun Ismi", "m²", "Adet", "En (cm)", "Milim (cm)", "Boy (m)", "Desi", "Fiyat (₺/Desi)", "Toplam (₺)"};
                model.setDataVector(data, new Vector<>(java.util.Arrays.asList(cols)));
                txtGelenHavale.setText(havale);
                updateSequenceNumbers();
                updateGrandTotals();
                applyCustomStyling();
                table.getColumnModel().getColumn(0).setMaxWidth(50);
                table.getColumnModel().getColumn(1).setPreferredWidth(250);
            } catch (Exception ignored) { }
        }
    }

    private void styleButton(JButton btn, Color c) {
        btn.setPreferredSize(new Dimension(160, 35));
        btn.setBackground(c); btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setOpaque(true);
        btn.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
    }

    private void applyCustomStyling() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int i=0; i<10; i++) if(i!=1) table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(7).setCellRenderer(new ColumnColorRenderer(new Color(240, 255, 240), new Color(0, 100, 0)));
        table.getColumnModel().getColumn(9).setCellRenderer(new ColumnColorRenderer(new Color(255, 240, 240), new Color(150, 0, 0)));
    }

    static class ColumnColorRenderer extends DefaultTableCellRenderer {
        private Color bg, fg;
        public ColumnColorRenderer(Color bg, Color fg) { this.bg = bg; this.fg = fg; this.setHorizontalAlignment(JLabel.CENTER); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isS, boolean hasF, int r, int c) {
            Component comp = super.getTableCellRendererComponent(table, value, isS, hasF, r, c);
            if (!isS) { comp.setBackground(bg); comp.setForeground(fg); }
            return comp;
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}