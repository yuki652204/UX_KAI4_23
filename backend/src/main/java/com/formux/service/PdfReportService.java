package com.formux.service;

import com.formux.model.CriterionResult;
import com.formux.model.DiagnosisResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 7: PDFレポート生成サービス
 * Apache PDFBox 3.x + ヒラギノ角ゴシック（macOS）を使用
 */
@Slf4j
@Service
public class PdfReportService {

    // A4サイズ（ポイント単位）
    private static final float W  = PDRectangle.A4.getWidth();   // 595
    private static final float H  = PDRectangle.A4.getHeight();  // 842
    private static final float MG = 40f;   // マージン
    private static final float CW = W - MG * 2;  // コンテンツ幅 = 515

    // 色定義（RGB 0.0〜1.0）
    private static final float[] C_PRIMARY = {0.149f, 0.388f, 0.922f};  // #2563eb
    private static final float[] C_GOOD    = {0.086f, 0.639f, 0.290f};  // #16a34a
    private static final float[] C_FAIR    = {0.851f, 0.467f, 0.024f};  // #d97706
    private static final float[] C_POOR    = {0.863f, 0.149f, 0.149f};  // #dc2626
    private static final float[] C_WHITE   = {1f, 1f, 1f};
    private static final float[] C_LIGHT   = {0.973f, 0.980f, 0.988f};  // #f8fafc
    private static final float[] C_BORDER  = {0.886f, 0.910f, 0.941f};  // #e2e8f0
    private static final float[] C_TEXT    = {0.118f, 0.161f, 0.235f};  // #1e293b
    private static final float[] C_MUTED   = {0.392f, 0.455f, 0.545f};  // #64748b

    /**
     * DiagnosisResult を PDF バイト列に変換する
     */
    public byte[] generate(DiagnosisResult result) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont fontN = loadJapaneseFont(doc, false);  // レギュラー (W3)
            PDFont fontB = loadJapaneseFont(doc, true);   // ボールド   (W6)

            // 1ページ目
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // ページ背景
                fillRect(cs, C_LIGHT, 0, 0, W, H);

                float y = H - MG;

                // ── ヘッダー ───────────────────────────────────────
                y = drawHeader(cs, fontB, fontN, result, y);
                y -= 12;

                // ── 総合スコアカード ───────────────────────────────
                y = drawOverallCard(cs, fontB, fontN, result, y);
                y -= 12;

                // ── 項目別スコア ───────────────────────────────────
                y = drawSectionTitle(cs, fontB, "項目別スコア", y);
                y -= 6;
                for (CriterionResult c : result.getCriteria()) {
                    if (y < 120) { // ページ下部余白チェック（簡易）
                        break;
                    }
                    y = drawCriterionRow(cs, fontB, fontN, c, y);
                    y -= 6;
                }

                // ── 優先改善事項 ───────────────────────────────────
                if (!result.getTopSuggestions().isEmpty() && y > 100) {
                    y -= 4;
                    y = drawSectionTitle(cs, fontB, "優先改善事項 TOP 3", y);
                    y -= 6;
                    y = drawSuggestions(cs, fontB, fontN, result.getTopSuggestions(), y);
                }

                // ── フッター ───────────────────────────────────────
                drawFooter(cs, fontN, result);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // =====================================================================
    // ヘッダー
    // =====================================================================
    private float drawHeader(PDPageContentStream cs, PDFont fb, PDFont fn,
                             DiagnosisResult result, float y) throws IOException {
        float boxH = 56f;
        float top  = y;
        float bot  = top - boxH;

        fillRect(cs, C_PRIMARY, MG, bot, CW, boxH);

        // タイトル
        drawText(cs, fb, C_WHITE, "フォームUXスコア診断レポート", MG + 14, bot + 32, 14f);

        // 診断日時
        String dateStr = result.getDiagnosedAt()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日  HH:mm"));
        drawText(cs, fn, C_WHITE, "診断日時: " + dateStr, MG + 14, bot + 12, 9f);

        // レポートID
        String idStr = "Report ID: " + result.getReportId();
        float idWidth = getTextWidth(fn, idStr, 9f);
        drawText(cs, fn, C_WHITE, idStr, MG + CW - idWidth - 10, bot + 12, 9f);

        return bot;
    }

    // =====================================================================
    // 総合スコアカード
    // =====================================================================
    private float drawOverallCard(PDPageContentStream cs, PDFont fb, PDFont fn,
                                  DiagnosisResult result, float y) throws IOException {
        float boxH  = 80f;
        float top   = y;
        float bot   = top - boxH;

        // カード背景
        fillRect(cs, C_WHITE, MG, bot, CW, boxH);
        strokeRect(cs, C_BORDER, MG, bot, CW, boxH);

        // スコア（大）
        String scoreStr = String.valueOf(result.getOverallScore());
        drawText(cs, fb, C_TEXT, scoreStr, MG + 16, bot + 50, 36f);
        drawText(cs, fn, C_MUTED, "点", MG + 16 + getTextWidth(fb, scoreStr, 36f) + 3, bot + 50, 12f);

        // グレードバッジ
        float[] gradeColor = gradeToColor(result.getOverallGrade());
        float badgeX = MG + 16;
        float badgeY = bot + 22;
        fillRect(cs, gradeColor, badgeX, badgeY, 48f, 18f);
        String gradeText = "グレード " + result.getOverallGrade();
        drawText(cs, fb, C_WHITE, gradeText, badgeX + 4, badgeY + 4, 9f);

        // コメント
        float commentX = MG + 90;
        float commentW = CW - 90 - 10;
        List<String> commentLines = wrapText(fn, result.getOverallComment(), 10f, commentW);
        float lineY = bot + boxH - 20;
        for (String line : commentLines) {
            drawText(cs, fn, C_TEXT, line, commentX, lineY, 10f);
            lineY -= 14;
        }

        return bot;
    }

    // =====================================================================
    // セクションタイトル
    // =====================================================================
    private float drawSectionTitle(PDPageContentStream cs, PDFont fb, String title, float y) throws IOException {
        // 左ボーダー + テキスト
        fillRect(cs, C_PRIMARY, MG, y - 16, 4f, 18f);
        drawText(cs, fb, C_TEXT, title, MG + 10, y - 13, 11f);
        return y - 18;
    }

    // =====================================================================
    // 項目別スコア行
    // =====================================================================
    private float drawCriterionRow(PDPageContentStream cs, PDFont fb, PDFont fn,
                                   CriterionResult c, float y) throws IOException {
        float rowH  = 52f;
        float top   = y;
        float bot   = top - rowH;

        // 行背景
        fillRect(cs, C_WHITE, MG, bot, CW, rowH);
        strokeRect(cs, C_BORDER, MG, bot, CW, rowH);

        // レベルカラーバー（左端4px）
        float[] levelColor = levelToColor(c.getLevel());
        fillRect(cs, levelColor, MG, bot, 4f, rowH);

        // 丸数字インデックス
        int idx = criterionIndex(c.getId());
        String circled = circledNumber(idx);
        drawText(cs, fb, C_TEXT, circled, MG + 10, bot + rowH - 14, 10f);

        // 項目名
        drawText(cs, fb, C_TEXT, c.getName(), MG + 26, bot + rowH - 14, 10f);

        // レベルバッジ
        String levelLabel = c.getLevel().getLabel();
        float labelW = getTextWidth(fb, levelLabel, 8f) + 10;
        float labelX = MG + CW - labelW - 8;
        float labelY = bot + rowH - 20;
        fillRect(cs, levelToLightColor(c.getLevel()), labelX, labelY, labelW, 14f);
        drawText(cs, fb, levelColor, levelLabel, labelX + 5, labelY + 3, 8f);

        // スコアテキスト
        String scoreText = c.getScore() + " / " + c.getMaxScore() + "点";
        float scoreW = getTextWidth(fn, scoreText, 9f);
        drawText(cs, fn, C_MUTED, scoreText, MG + CW - scoreW - labelW - 16, bot + rowH - 17, 9f);

        // プログレスバー（背景）
        float barY    = bot + 10;
        float barH    = 7f;
        float barMaxW = CW - 20;
        fillRect(cs, C_BORDER, MG + 10, barY, barMaxW, barH);

        // プログレスバー（値）
        float barFillW = (float) c.getScoreRate() * barMaxW;
        if (barFillW > 0) {
            fillRect(cs, levelColor, MG + 10, barY, barFillW, barH);
        }

        // 判定理由テキスト（短縮）
        String detail = c.getDetail();
        if (detail.length() > 55) detail = detail.substring(0, 55) + "…";
        drawText(cs, fn, C_MUTED, detail, MG + 10, bot + rowH - 28, 8f);

        return bot;
    }

    // =====================================================================
    // 優先改善事項
    // =====================================================================
    private float drawSuggestions(PDPageContentStream cs, PDFont fb, PDFont fn,
                                  List<String> suggestions, float y) throws IOException {
        float boxPad = 12f;
        float innerW = CW - boxPad * 2;

        for (int i = 0; i < suggestions.size(); i++) {
            // テキストを折り返して必要な高さを計算
            List<String> lines = wrapText(fn, suggestions.get(i), 9f, innerW - 24);
            float rowH = Math.max(30f, lines.size() * 13f + boxPad * 2);

            float bot = y - rowH;
            if (bot < MG + 20) break;  // ページ下部ガード

            fillRect(cs, C_WHITE, MG, bot, CW, rowH);
            strokeRect(cs, C_BORDER, MG, bot, CW, rowH);

            // 番号バッジ
            fillRect(cs, C_PRIMARY, MG + boxPad, bot + (rowH - 18) / 2, 18f, 18f);
            drawText(cs, fb, C_WHITE, String.valueOf(i + 1), MG + boxPad + 5, bot + (rowH - 18) / 2 + 4, 9f);

            // 提案テキスト
            float textX = MG + boxPad + 24;
            float lineY = bot + rowH - boxPad - 10;
            for (String line : lines) {
                drawText(cs, fn, C_TEXT, line, textX, lineY, 9f);
                lineY -= 13;
            }

            y = bot - 6;
        }

        return y;
    }

    // =====================================================================
    // フッター
    // =====================================================================
    private void drawFooter(PDPageContentStream cs, PDFont fn,
                            DiagnosisResult result) throws IOException {
        String footer = "フォームUXスコア診断ツール  |  " + result.getReportId();
        float fw = getTextWidth(fn, footer, 8f);
        drawText(cs, fn, C_MUTED, footer, (W - fw) / 2, 18f, 8f);

        // 区切り線
        fillRect(cs, C_BORDER, MG, 26f, CW, 1f);
    }

    // =====================================================================
    // テキスト描画ヘルパー
    // =====================================================================
    private void drawText(PDPageContentStream cs, PDFont font, float[] color,
                          String text, float x, float y, float size) throws IOException {
        if (text == null || text.isBlank()) return;
        cs.beginText();
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    private void fillRect(PDPageContentStream cs, float[] color,
                          float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void strokeRect(PDPageContentStream cs, float[] color,
                             float x, float y, float w, float h) throws IOException {
        cs.setStrokingColor(color[0], color[1], color[2]);
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private float getTextWidth(PDFont font, String text, float size) throws IOException {
        try {
            return font.getStringWidth(sanitize(text)) / 1000f * size;
        } catch (Exception e) {
            return text.length() * size * 0.5f; // fallback estimation
        }
    }

    /**
     * 日本語テキストの折り返し処理
     * PDFBoxはstringWidth計算でUnicode文字幅を考慮するため文字単位で処理する
     */
    private List<String> wrapText(PDFont font, String text, float size, float maxW) {
        List<String> result = new ArrayList<>();
        for (String para : text.split("\n")) {
            if (para.isBlank()) { result.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < para.length(); i++) {
                cur.append(para.charAt(i));
                float w;
                try {
                    w = font.getStringWidth(sanitize(cur.toString())) / 1000f * size;
                } catch (Exception e) {
                    w = cur.length() * size * 0.55f;
                }
                if (w > maxW && cur.length() > 1) {
                    result.add(cur.substring(0, cur.length() - 1));
                    cur = new StringBuilder(String.valueOf(para.charAt(i)));
                }
            }
            if (!cur.isEmpty()) result.add(cur.toString());
        }
        return result;
    }

    /**
     * PDFBoxで表示できない制御文字や未サポート文字を除去する
     */
    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", "")
                   .replace("\r", "");
    }

    // =====================================================================
    // フォント読み込み（クラスパス内 NotoSansJP-Regular.ttf を使用）
    // =====================================================================
    private PDFont loadJapaneseFont(PDDocument doc, boolean bold) {
        // Regular/Bold とも同一フォントを使用（Regular のみバンドル）
        String resource = "/fonts/ipaexg.ttf";
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is != null) {
                PDFont font = PDType0Font.load(doc, is, true);
                log.debug("フォントロード成功: {}", resource);
                return font;
            }
            log.warn("クラスパスにフォントが見つかりません: {}", resource);
        } catch (Exception e) {
            log.warn("フォントロード失敗: {} ({})", resource, e.getMessage());
        }

        // フォールバック: 英字のみ
        Standard14Fonts.FontName fallback = bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA;
        return new PDType1Font(fallback);
    }

    // =====================================================================
    // ユーティリティ
    // =====================================================================
    private float[] gradeToColor(String grade) {
        return switch (grade) {
            case "A"  -> C_GOOD;
            case "B"  -> C_PRIMARY;
            case "C"  -> C_FAIR;
            default   -> C_POOR;
        };
    }

    private float[] levelToColor(CriterionResult.Level level) {
        return switch (level) {
            case GOOD -> C_GOOD;
            case FAIR -> C_FAIR;
            case POOR -> C_POOR;
        };
    }

    private float[] levelToLightColor(CriterionResult.Level level) {
        return switch (level) {
            case GOOD -> new float[]{0.863f, 0.988f, 0.898f};  // #dcfce7
            case FAIR -> new float[]{0.996f, 0.953f, 0.780f};  // #fef3c7
            case POOR -> new float[]{0.996f, 0.886f, 0.886f};  // #fee2e2
        };
    }

    private int criterionIndex(String id) {
        return switch (id) {
            case "validation_message"    -> 1;
            case "language_simplicity"   -> 2;
            case "error_timing"          -> 3;
            case "multiple_error"        -> 4;
            case "password_requirements" -> 5;
            case "auto_focus"            -> 6;
            default                      -> 0;
        };
    }

    /**
     * TTF/TTC ファイルをPDFBox 3.x の API でロードする
     * .ttc（TrueType Collection）は TrueTypeCollection 経由で最初のフォントを取得
     * .ttf は InputStream で直接ロード
     */
    private String circledNumber(int n) {
        return switch (n) {
            case 1 -> "①"; case 2 -> "②"; case 3 -> "③";
            case 4 -> "④"; case 5 -> "⑤"; case 6 -> "⑥";
            default -> String.valueOf(n);
        };
    }
}
