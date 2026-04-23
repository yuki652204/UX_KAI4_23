package com.formux.controller;

import com.formux.model.DiagnosisRequest;
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
import java.util.Map;

/**
 * 診断APIエンドポイント
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // 開発時のフロントエンド接続用
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final PdfReportService pdfReportService;

    /**
     * POST /api/diagnose
     * URLまたはHTMLを受け取って診断を実行する
     */
    @PostMapping("/diagnose")
    public ResponseEntity<?> diagnose(@RequestBody DiagnosisRequest request) {
        if (!request.isValid()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "url または html のどちらかを指定してください。"));
        }

        try {
            DiagnosisResult result = diagnosisService.diagnose(request);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("URL取得に失敗しました: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "URLにアクセスできませんでした。URLを確認してください。"));
        } catch (Exception e) {
            log.error("診断中にエラーが発生しました", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "診断処理中にエラーが発生しました。しばらく待ってから再度お試しください。"));
        }
    }

    /**
     * POST /api/diagnose/pdf
     * URLまたはHTMLを受け取って診断を実行し、PDFレポートを直接返す
     */
    @PostMapping("/diagnose/pdf")
    public ResponseEntity<?> diagnosePdf(@RequestBody DiagnosisRequest request) {
        if (!request.isValid()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "url または html のどちらかを指定してください。"));
        }

        try {
            DiagnosisResult result = diagnosisService.diagnose(request);
            byte[] pdf = pdfReportService.generate(result);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"formux-report-" + result.getReportId() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IOException e) {
            log.error("PDF診断に失敗しました: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "URLにアクセスできませんでした。URLを確認してください。"));
        } catch (Exception e) {
            log.error("PDF生成中にエラーが発生しました", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "PDF生成中にエラーが発生しました。"));
        }
    }

    /**
     * GET /api/health
     * ヘルスチェック用
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
