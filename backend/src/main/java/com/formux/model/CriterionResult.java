package com.formux.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 診断項目1件の評価結果
 */
@Getter
@Builder
public class CriterionResult {

    public enum Level {
        GOOD("良好"),
        FAIR("要改善"),
        POOR("不十分");

        private final String label;
        Level(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** 項目ID（例: "validation_message"） */
    private final String id;

    /** 項目名（日本語） */
    private final String name;

    /** 実スコア */
    private final int score;

    /** 満点 */
    private final int maxScore;

    /** 評価レベル */
    private final Level level;

    /** 判定理由（日本語） */
    private final String detail;

    /** 改善前の現状説明（各Evaluatorが全分岐でセット） */
    private String beforeMessage;

    public void setBeforeMessage(String beforeMessage) {
        this.beforeMessage = beforeMessage;
    }

    /**
     * 改善提案（Step 4でClaude APIが生成する。
     * Step 1〜3ではルールベースの文言をセット）
     */
    private String suggestion;

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    /** スコア率（0.0〜1.0） */
    public double getScoreRate() {
        return maxScore == 0 ? 0.0 : (double) score / maxScore;
    }
}
