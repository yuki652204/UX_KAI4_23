package com.formux.controller;

import com.formux.model.DiagnosisResult;
import com.formux.service.DiagnosisService;
import com.formux.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

/**
 * Step 5: PDFレポートダウンロードエンドポイント
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final DiagnosisService   diagnosisService;
    private final PdfReportService   pdfReportService;

    /**
     * GET /api/report/{reportId}/pdf
     * 診断結果のPDFレポートをダウンロードする
     */
    @GetMapping("/report/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String reportId) {
        Optional<DiagnosisResult> resultOpt = diagnosisService.getResult(reportId);
        if (resultOpt.isEmpty()) {
            log.warn("レポートが見つかりません: {}", reportId);
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] pdf = pdfReportService.generate(resultOpt.get());
            log.info("PDFレポート生成完了: reportId={}, size={}bytes", reportId, pdf.length);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"formux-report-" + reportId + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (IOException e) {
            log.error("PDF生成に失敗しました: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
