package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ① バリデーションエラー文の具体性（満点: 25点）
 *
 * 採点基準:
 *   POOR (0〜8点)  : エラー要素が存在しない、または汎用的すぎるメッセージのみ
 *   FAIR (9〜17点) : フィールド名や条件の一方は示されているが不十分
 *   GOOD (18〜25点): 原因 + 対処法の両方が含まれる具体的なメッセージ
 */
@Component
public class ValidationMessageEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 25;

    // エラー要素が全く存在しないケースでのベーススコア
    private static final int SCORE_NO_ERROR_ELEMENT = 0;
    // エラー要素はあるが汎用メッセージのみ
    private static final int SCORE_GENERIC_MESSAGE   = 6;
    // フィールド名または条件の一方が明示されている
    private static final int SCORE_PARTIAL_SPECIFIC  = 14;
    // 原因＋対処法が含まれる具体的なメッセージ
    private static final int SCORE_FULLY_SPECIFIC    = 25;

    // 汎用的なエラーメッセージを検出するパターン
    private static final List<Pattern> GENERIC_PATTERNS = List.of(
        Pattern.compile("入力.*(エラー|誤り|不正|間違い)"),
        Pattern.compile("エラーが.*あります"),
        Pattern.compile("入力内容を.*確認"),
        Pattern.compile("正しく.*入力"),
        Pattern.compile("必須.*項目"),
        Pattern.compile("入力してください$"),
        Pattern.compile("invalid input", Pattern.CASE_INSENSITIVE),
        Pattern.compile("required field", Pattern.CASE_INSENSITIVE),
        Pattern.compile("please (check|fix|correct)", Pattern.CASE_INSENSITIVE)
    );

    // 具体的な原因を示すパターン（フィールド名・条件・数値など）
    private static final List<Pattern> CAUSE_PATTERNS = List.of(
        Pattern.compile("[0-9]+文字"),                          // 文字数指定
        Pattern.compile("@|メールアドレス|mail.*アドレス"),     // メール形式
        Pattern.compile("半角|全角|英数字|ひらがな|カタカナ"),   // 文字種
        Pattern.compile("数字のみ|英字のみ"),
        Pattern.compile("パスワード.*[0-9]+"),                  // パスワード条件
        Pattern.compile("(大文字|小文字|記号|特殊文字).*含"),   // 文字要件
        Pattern.compile("(電話番号|郵便番号|生年月日).*形式"),   // フォーマット指定
        Pattern.compile("すでに.*登録|重複"),                   // 重複エラー
        Pattern.compile("有効期限"),
        Pattern.compile("[0-9]〜[0-9]|[0-9]-[0-9]")           // 範囲指定
    );

    // 対処法を示すパターン
    private static final List<Pattern> ACTION_PATTERNS = List.of(
        Pattern.compile("(入力|変更|修正)してください"),
        Pattern.compile("お試しください"),
        Pattern.compile("確認の上.*入力"),
        Pattern.compile("形式で.*入力"),
        Pattern.compile("例:.*|例）.*"),                        // 入力例の提示
        Pattern.compile("例えば|たとえば"),
        Pattern.compile("〜のように|ように.*入力"),
        Pattern.compile("別の.*を|他の.*を")
    );

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        List<String> messages = form.getErrorMessages();

        if (messages.isEmpty()) {
            return buildResult(SCORE_NO_ERROR_ELEMENT, Level.POOR,
                "エラーを表示する要素が検出できませんでした。エラー時の表示が実装されているか確認してください。",
                "「入力したメールアドレスの形式が正しくありません。@マークとドメイン（例: sample@example.com）を含めてください」のように、" +
                "原因と正しい入力例を一緒に表示しましょう。",
                "現状：エラーメッセージの表示要素が見つかりません。エラーが起きても何も表示されない可能性があります。");
        }

        // 全メッセージに対してスコアリング
        int bestScore = SCORE_GENERIC_MESSAGE; // エラー要素があれば最低でもこのスコア
        boolean hasCause  = false;
        boolean hasAction = false;

        for (String msg : messages) {
            boolean msgHasCause  = CAUSE_PATTERNS .stream().anyMatch(p -> p.matcher(msg).find());
            boolean msgHasAction = ACTION_PATTERNS.stream().anyMatch(p -> p.matcher(msg).find());
            boolean msgIsGeneric = GENERIC_PATTERNS.stream().anyMatch(p -> p.matcher(msg).find())
                                   && !msgHasCause;

            if (msgHasCause)  hasCause  = true;
            if (msgHasAction) hasAction = true;

            if (msgIsGeneric && bestScore < SCORE_GENERIC_MESSAGE) {
                bestScore = SCORE_GENERIC_MESSAGE;
            }
        }

        int score;
        Level level;
        String detail;
        String suggestion;
        String beforeMessage;

        if (hasCause && hasAction) {
            score = SCORE_FULLY_SPECIFIC;
            level = Level.GOOD;
            detail = "エラーの原因と対処法が具体的に示されています。高齢者・初心者にも理解しやすいメッセージです。";
            suggestion = "現状の具体的なメッセージを継続してください。入力例（例: sample@example.com）を追加するとさらに親切です。";
            beforeMessage = "現状：エラーの原因と対処法が両方含まれた具体的なメッセージが実装されています。";
        } else if (hasCause || hasAction) {
            score = SCORE_PARTIAL_SPECIFIC;
            level = Level.FAIR;
            detail = hasCause
                ? "エラーの原因は示されていますが、「どう直せばよいか」の対処法が不足しています。"
                : "対処法は示されていますが、「何が問題か」の原因が不足しています。";
            suggestion = "エラーメッセージには「何が間違っているか（原因）」と「どう直すか（対処法）」の両方を書きましょう。" +
                "例：「パスワードは8文字以上で入力してください。現在〇文字です」";
            beforeMessage = hasCause
                ? "現状：エラーの原因は書かれていますが「どう直せばよいか」の対処法が書かれていません。"
                : "現状：対処法は書かれていますが「何が問題か」の原因が書かれていません。";
        } else {
            score = SCORE_GENERIC_MESSAGE;
            level = Level.POOR;
            detail = "「入力エラー」「確認してください」など、原因も対処法もわからない汎用メッセージのみ検出されました。";
            suggestion = "「メールアドレスの@マークが見つかりません。正しい形式（例: name@example.com）で入力してください」" +
                "のように、何が問題でどう直すかを具体的に書きましょう。";
            beforeMessage = "現状：「入力内容に誤りがあります」など、原因も対処法もわからない汎用メッセージのみ表示されています。";
        }

        return buildResult(score, level, detail, suggestion, beforeMessage);
    }

    private CriterionResult buildResult(int score, Level level, String detail, String suggestion, String beforeMessage) {
        CriterionResult result = CriterionResult.builder()
                .id("validation_message")
                .name("バリデーションエラー文の具体性")
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
