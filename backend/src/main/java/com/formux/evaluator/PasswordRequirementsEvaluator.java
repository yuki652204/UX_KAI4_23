package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ⑤ パスワード要件の事前明示（満点: 15点）
 *
 * 採点基準:
 *   フォームにパスワード欄がない場合: 満点（対象外）
 *   POOR (0〜4点)  : 要件の表示なし
 *   FAIR (5〜10点) : 要件の一部のみ表示
 *   GOOD (11〜15点): 要件が網羅的に事前表示されている
 */
@Component
public class PasswordRequirementsEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 15;

    // パスワード要件を示すキーワードパターン
    private static final List<Pattern> REQUIREMENT_PATTERNS = List.of(
        Pattern.compile("[0-9]+文字(以上|以内|〜)"),           // 文字数
        Pattern.compile("(英字|英語|アルファベット).*(必要|含)"),   // 英字要件
        Pattern.compile("(数字|数値).*(必要|含)"),              // 数字要件
        Pattern.compile("(大文字).*(必要|含)"),                // 大文字要件
        Pattern.compile("(記号|特殊文字).*(必要|含|使用)"),     // 記号要件
        Pattern.compile("\\d+文字以上.*英|英.*\\d+文字以上"),   // 英字+文字数組み合わせ
        Pattern.compile("半角英数"),                          // 半角英数
        Pattern.compile("(パスワード|password).*(条件|要件|ルール|制限)"),
        Pattern.compile("(使用できる|利用可能な).*(文字|記号)"),
        Pattern.compile("[A-Za-z].*[0-9]|[0-9].*[A-Za-z]") // 英字+数字パターン例
    );

    // パスワード強度インジケーターのパターン
    private static final Pattern STRENGTH_INDICATOR_PATTERN = Pattern.compile(
        "password.?strength|" +
        "strength.?indicator|" +
        "strength.?meter|" +
        "パスワード強度|" +
        "強度.*インジケーター|" +
        "zxcvbn|" +        // 有名なパスワード強度ライブラリ
        "meter.*password",
        Pattern.CASE_INSENSITIVE
    );

    // 禁止パスワードパターンの注意書きを検出する（フォームの近くに表示されているか）
    private static final Pattern WEAK_PASSWORD_WARNING_PATTERN = Pattern.compile(
        "(生年月日|誕生日).*(使用|利用).*(しない|できない|禁止)|" +
        "(電話番号).*(使用|利用).*(しない|できない|禁止)|" +
        "(連続|同じ|繰り返し).*(数字|番号).*(使用|利用).*(しない|できない|禁止)|" +
        "推測.*(されやすい|しやすい).*(使用|利用).*(しない|できない)|" +
        "(123|abc|password).*(使用|利用).*(しない|できない|禁止)|" +
        "簡単.*(パスワード|番号).*(避|使用しない)|" +
        "暗証番号.*(使用|利用).*(しない|できない|禁止)",
        Pattern.CASE_INSENSITIVE
    );

    // 弱いパスワードの典型パターン（バリデーションや警告のJS内で検出）
    private static final Pattern WEAK_PASSWORD_CHECK_PATTERN = Pattern.compile(
        // 生年月日形式（19xx, 20xx など）
        "19[0-9]{2}|20[0-9]{2}|" +
        "birthday|birthdate|birth_date|生年月日|" +
        // 連続数字
        "123|234|345|456|567|678|789|890|012|" +
        // 繰り返し数字
        "000|111|222|333|444|555|666|777|888|999|" +
        // 電話番号的パターン
        "090|080|070|0120|" +
        // よく使われる弱いパスワードチェック
        "weak.?password|sequential|consecutive",
        Pattern.CASE_INSENSITIVE
    );

    private static final String WEAK_PASSWORD_SUGGESTION =
        "生年月日・電話番号・連続数字（123など）は推測されやすいため使用しないでください。";

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        // パスワードフィールドがなければ対象外（満点）
        if (!form.isHasPasswordField()) {
            CriterionResult naResult = CriterionResult.builder()
                    .id("password_requirements")
                    .name("パスワード要件の事前明示")
                    .score(MAX_SCORE)
                    .maxScore(MAX_SCORE)
                    .level(Level.GOOD)
                    .detail("このフォームにはパスワード入力欄がないため、この項目は対象外です。")
                    .suggestion("対象外です。")
                    .build();
            naResult.setBeforeMessage("現状：パスワード入力欄がないため、この項目は対象外です。");
            return naResult;
        }

        List<String> hintTexts = form.getPasswordHintTexts();
        String allHints = String.join(" ", hintTexts);
        String script   = form.getScriptText();
        String formHtml = form.getFormHtml();

        // 要件パターンに一致する件数を数える
        long matchedRequirements = REQUIREMENT_PATTERNS.stream()
                .filter(p -> p.matcher(allHints).find())
                .count();

        boolean hasStrengthIndicator = STRENGTH_INDICATOR_PATTERN.matcher(script).find()
                                       || STRENGTH_INDICATOR_PATTERN.matcher(formHtml).find();

        // 弱いパスワード禁止の注意書きが表示されているか
        boolean hasWeakPasswordWarning = WEAK_PASSWORD_WARNING_PATTERN.matcher(allHints).find()
                                         || WEAK_PASSWORD_WARNING_PATTERN.matcher(formHtml).find();

        // 弱いパスワードチェックがJSに実装されているか
        boolean hasWeakPasswordCheck = WEAK_PASSWORD_CHECK_PATTERN.matcher(script).find();

        // 注意書きがない場合は最大3点減点
        int weakPasswordPenalty = (hasWeakPasswordWarning || hasWeakPasswordCheck) ? 0 : 3;

        int score;
        Level level;
        String detail;
        String suggestion;
        String beforeMessage;

        if (matchedRequirements >= 3 || (matchedRequirements >= 2 && hasStrengthIndicator)) {
            score = Math.max(0, 15 - weakPasswordPenalty);
            level = score >= 12 ? Level.GOOD : Level.FAIR;
            detail = "パスワードの要件（文字数・文字種など）がパスワード欄の近くに事前表示されています。" +
                "高齢者が迷わず入力できます。" +
                (weakPasswordPenalty > 0 ? "ただし、推測されやすいパスワードへの注意書きが見当たりません。" : "");
            suggestion = "現状を維持してください。入力中にリアルタイムで要件チェック結果を表示（✓ 8文字以上）すると、さらに安心感が増します。\n" +
                WEAK_PASSWORD_SUGGESTION;
            beforeMessage = weakPasswordPenalty > 0
                ? "現状：要件は表示されていますが、推測されやすい番号（生年月日・連続数字など）の禁止が明示されていない。"
                : "現状：パスワードの文字数・文字種の要件と、推測されやすいパスワードへの注意が表示されています。";
        } else if (matchedRequirements >= 1 || hasStrengthIndicator) {
            score = Math.max(0, 8 - weakPasswordPenalty);
            level = Level.FAIR;
            detail = "一部の要件は表示されていますが、すべての条件が明示されているわけではありません。" +
                (weakPasswordPenalty > 0 ? "また、推測されやすいパスワードへの注意書きも不足しています。" : "");
            suggestion = "パスワード欄の上か下に「パスワードの条件：8文字以上、英字と数字をそれぞれ1文字以上含むこと」" +
                "のようにすべての条件を箇条書きで示しましょう。\n" +
                WEAK_PASSWORD_SUGGESTION;
            beforeMessage = weakPasswordPenalty > 0
                ? "現状：パスワード要件の一部のみ表示されており、推測されやすい番号（生年月日・連続数字など）の禁止が明示されていない。"
                : "現状：パスワード要件の一部のみ表示されており、すべての条件が明示されていません。";
        } else {
            score = 0;
            level = Level.POOR;
            detail = "パスワードの要件がパスワード欄の近くに表示されていません。" +
                "高齢者は何度も入力し直すことになります。";
            suggestion = "パスワード入力欄のすぐ上か下に、条件を箇条書きで表示しましょう。\n" +
                "例：\n" +
                "・8文字以上で入力してください\n" +
                "・英字（abcなど）と数字（123など）を必ず含めてください\n" +
                "・記号（!@#など）は使えます\n" +
                WEAK_PASSWORD_SUGGESTION;
            beforeMessage = "現状：パスワード欄の近くに要件が表示されておらず、推測されやすい番号（生年月日・連続数字など）の禁止も明示されていません。";
        }

        CriterionResult result = CriterionResult.builder()
                .id("password_requirements")
                .name("パスワード要件の事前明示")
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
