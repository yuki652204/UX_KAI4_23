package com.formux.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 診断結果全体
 */
@Getter
@Builder
public class DiagnosisResult {

    /** レポートID（PDF取得時に使用） */
    private final String reportId;

    /** 総合スコア（100点満点） */
    private final int overallScore;

    /** 総合グレード（A〜D） */
    private final String overallGrade;

    /** 総合評価コメント */
    private final String overallComment;

    /** 6項目の評価結果 */
    private final List<CriterionResult> criteria;

    /** 優先改善事項TOP3 */
    private final List<String> topSuggestions;

    /** 診断実施日時 */
    private final LocalDateTime diagnosedAt;

    /**
     * スコアからグレードを算出する
     */
    public static String toGrade(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 50) return "C";
        return "D";
    }

    /**
     * グレードに応じた総合コメントを返す
     */
    public static String toComment(String grade) {
        return switch (grade) {
            case "A" -> "高齢者・初心者にも使いやすいフォームです。引き続き維持しましょう。";
            case "B" -> "概ね使いやすいですが、いくつかの改善でさらに親切なフォームになります。";
            case "C" -> "高齢者や初心者が困る箇所が複数あります。優先改善事項から取り組みましょう。";
            default  -> "エラー時に利用者が困りやすい状態です。早急な改善をおすすめします。";
        };
    }
}
