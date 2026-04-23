package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ② 高齢者・初心者が理解できる平易な日本語か（満点: 20点）
 *
 * 採点基準:
 *   専門用語・難読語の検出数に応じて減点方式
 *   ラベル・エラーメッセージのテキストを評価対象とする
 */
@Component
public class LanguageSimplicityEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 20;

    // IT・Web専門用語（高齢者・初心者が理解しにくい語）
    private static final List<String> IT_JARGON = List.of(
        "バリデーション", "validation",
        "フォーマット", "format",
        "シンタックス", "syntax",
        "パラメータ", "parameter",
        "ログイン", "サインイン", // ←これ自体は普及しているが念のため
        "認証", "authenticate",
        "セッション", "session",
        "トークン", "token",
        "API", "エンドポイント",
        "サーバー", "クライアント",
        "null", "undefined", "NaN", "404", "500",
        "HTTP", "https", "localhost",
        "エラーコード", "ステータスコード"
    );

    // 難読語・省略語
    private static final List<String> DIFFICULT_WORDS = List.of(
        "該当", "入力値", "文字列", "半角英数字",
        "当該", "所定", "桁数",
        "禁則", "制約", "仕様",
        "form", "input", "submit" // HTML用語がそのまま表示されているケース
    );

    // 長文メッセージ（1文が50文字超えたら難読性が上がる）
    private static final int LONG_MESSAGE_THRESHOLD = 50;

    // 英字・記号が多すぎるメッセージを検出
    private static final Pattern HEAVY_ASCII_PATTERN =
        Pattern.compile("[a-zA-Z0-9]{5,}|[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{3,}");

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        List<String> allTexts = new java.util.ArrayList<>();
        allTexts.addAll(form.getErrorMessages());
        allTexts.addAll(form.getLabelTexts());

        if (allTexts.isEmpty()) {
            return buildResult(MAX_SCORE / 2, Level.FAIR,
                "評価対象のテキストが検出できませんでした。",
                "ラベルやエラーメッセージをひらがな・やさしい日本語で書くと高齢者にも伝わりやすくなります。",
                "現状：評価対象のテキストが検出できませんでした。ラベルやエラーメッセージが存在するか確認してください。");
        }

        int penaltyPoints = 0;
        int jargonCount   = 0;
        int difficultCount = 0;
        int longMsgCount  = 0;

        for (String text : allTexts) {
            // 専門用語チェック
            for (String jargon : IT_JARGON) {
                if (text.toLowerCase().contains(jargon.toLowerCase())) {
                    jargonCount++;
                    break; // 1テキストにつき1カウント
                }
            }

            // 難読語チェック
            for (String word : DIFFICULT_WORDS) {
                if (text.contains(word)) {
                    difficultCount++;
                    break;
                }
            }

            // 長文チェック
            if (text.length() > LONG_MESSAGE_THRESHOLD) {
                longMsgCount++;
            }
        }

        // 減点計算（最低0点）
        penaltyPoints += Math.min(jargonCount * 3, 9);     // 専門用語: 1件3点、最大9点減
        penaltyPoints += Math.min(difficultCount * 2, 6);  // 難読語: 1件2点、最大6点減
        penaltyPoints += Math.min(longMsgCount * 1, 4);    // 長文: 1件1点、最大4点減

        int score = Math.max(0, MAX_SCORE - penaltyPoints);
        Level level = scoreToLevel(score);

        String detail = buildDetail(jargonCount, difficultCount, longMsgCount, allTexts.size());
        String suggestion = buildSuggestion(jargonCount, difficultCount, longMsgCount);
        String beforeMessage = buildBeforeMessage(level, jargonCount, difficultCount, longMsgCount);

        return buildResult(score, level, detail, suggestion, beforeMessage);
    }

    private Level scoreToLevel(int score) {
        if (score >= 16) return Level.GOOD;
        if (score >= 10) return Level.FAIR;
        return Level.POOR;
    }

    private String buildDetail(int jargon, int difficult, int longMsg, int total) {
        if (jargon == 0 && difficult == 0 && longMsg == 0) {
            return "平易な日本語で書かれており、高齢者・初心者にも理解しやすい表現です。";
        }
        StringBuilder sb = new StringBuilder("評価対象テキスト").append(total).append("件中、");
        if (jargon > 0)    sb.append("IT専門用語が").append(jargon).append("件、");
        if (difficult > 0) sb.append("難読語・省略語が").append(difficult).append("件、");
        if (longMsg > 0)   sb.append("長文メッセージが").append(longMsg).append("件");
        sb.append("検出されました。");
        return sb.toString();
    }

    private String buildSuggestion(int jargon, int difficult, int longMsg) {
        if (jargon > 0) {
            return "「バリデーションエラー」→「入力内容に誤りがあります」、「フォーマット」→「書き方」のように" +
                "専門用語をやさしい言葉に置き換えましょう。";
        }
        if (difficult > 0) {
            return "「半角英数字」→「英語と数字を使って」のように、日常の言葉で説明しましょう。";
        }
        if (longMsg > 0) {
            return "メッセージが長いと読むのが大変です。1文を30文字以内に短くし、必要なら2文に分けましょう。";
        }
        return "現状の表現を維持してください。";
    }

    private String buildBeforeMessage(Level level, int jargon, int difficult, int longMsg) {
        return switch (level) {
            case GOOD -> "現状：専門用語や難読語がほとんどなく、わかりやすい表現が使われています。";
            case FAIR -> {
                if (jargon > 0) yield "現状：「バリデーション」「フォーマット」などのIT専門用語が " + jargon + " 件含まれており、高齢者には伝わりにくい可能性があります。";
                if (difficult > 0) yield "現状：「半角英数字」「入力値」などの難読語が " + difficult + " 件含まれており、わかりにくい表現があります。";
                yield "現状：一部のメッセージが長文になっており、読み取りにくい可能性があります。";
            }
            case POOR -> "現状：専門用語・難読語・長文メッセージが多く、高齢者や初心者には理解が困難な状態です。";
        };
    }

    private CriterionResult buildResult(int score, Level level, String detail, String suggestion, String beforeMessage) {
        CriterionResult result = CriterionResult.builder()
                .id("language_simplicity")
                .name("高齢者・初心者が理解できる平易な日本語")
                .score(score)
                .maxScore(MAX_SCORE)
                .level(level)
                .detail(detail)
                .suggestion(suggestion)
                .build();
        result.setBeforeMessage(beforeMessage);
        return result;
    }
}
