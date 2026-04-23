package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ③ エラーの表示タイミング（満点: 15点）
 *
 * 採点基準:
 *   POOR (0〜4点)  : 送信後のみ（JSにリアルタイム検証なし）
 *   FAIR (5〜10点) : onblurのみ（フォーカスアウト時）
 *   GOOD (11〜15点): oninput / onchange でリアルタイム検証あり
 */
@Component
public class ErrorTimingEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 15;

    // リアルタイムバリデーション（入力中）のパターン
    private static final Pattern REALTIME_PATTERN = Pattern.compile(
        "addEventListener\\(['\"]input['\"]|" +
        "oninput\\s*=|" +
        "\\.on\\(['\"]input['\"]|" +
        "addEventListener\\(['\"]keyup['\"]|" +
        "onkeyup\\s*=|" +
        "\\.on\\(['\"]keyup['\"]|" +
        "addEventListener\\(['\"]change['\"]|" +
        "onchange\\s*=|" +
        "\\.on\\(['\"]change['\"]|" +
        // Reactスタイル
        "onChange=|onInput=|" +
        // Vue.jsスタイル
        "@input|@change|v-on:input|v-on:change",
        Pattern.CASE_INSENSITIVE
    );

    // フォーカスアウト時バリデーションのパターン
    private static final Pattern BLUR_PATTERN = Pattern.compile(
        "addEventListener\\(['\"]blur['\"]|" +
        "onblur\\s*=|" +
        "\\.on\\(['\"]blur['\"]|" +
        "onBlur=|" +
        "@blur|v-on:blur",
        Pattern.CASE_INSENSITIVE
    );

    // HTML5ネイティブバリデーション（required, pattern, minlength等）
    private static final Pattern HTML5_VALIDATION_PATTERN = Pattern.compile(
        "\\brequired\\b|" +
        "\\bpattern\\s*=|" +
        "\\bminlength\\s*=|" +
        "\\bmaxlength\\s*=|" +
        "\\bmin\\s*=|\\bmax\\s*=|" +
        "\\btype=['\"]email['\"]|" +
        "\\btype=['\"]tel['\"]|" +
        "\\btype=['\"]url['\"]|" +
        "\\btype=['\"]number['\"]",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        String script  = form.getScriptText();
        String formHtml = form.getFormHtml();

        boolean hasRealtime    = REALTIME_PATTERN.matcher(script).find()
                                 || REALTIME_PATTERN.matcher(formHtml).find();
        boolean hasBlur        = BLUR_PATTERN.matcher(script).find()
                                 || BLUR_PATTERN.matcher(formHtml).find();
        boolean hasHtml5Native = HTML5_VALIDATION_PATTERN.matcher(formHtml).find();

        int score;
        Level level;
        String detail;
        String suggestion;
        String beforeMessage;

        if (hasRealtime) {
            score = 15;
            level = Level.GOOD;
            detail = "入力中にリアルタイムでバリデーションが実行される実装が検出されました。" +
                "高齢者が「何が間違っているか」をすぐに気づける優れたUXです。";
            suggestion = "リアルタイム検証を継続してください。チェック通過時に緑色のアイコンを表示するとさらに安心感が増します。";
            beforeMessage = "現状：入力中にリアルタイムでバリデーションが実行されています。";
        } else if (hasBlur) {
            score = 10;
            level = Level.FAIR;
            detail = "フォーカスが外れたタイミング（onblur）でバリデーションが実行されます。" +
                "送信後よりは早いですが、入力中には気づけません。";
            suggestion = "フォーカスアウトに加えて、入力中（oninput）にもチェックを行うと、" +
                "高齢者が間違いに早く気づけます。";
            beforeMessage = "現状：フォーカスが外れたときだけエラーチェックが行われます。入力中は間違いに気づけません。";
        } else if (hasHtml5Native) {
            score = 7;
            level = Level.FAIR;
            detail = "HTML5のネイティブバリデーション（required, patternなど）が検出されました。" +
                "送信時にチェックされますが、ブラウザ依存でメッセージのカスタマイズが難しい場合があります。";
            suggestion = "HTML5ネイティブバリデーションに加えて、JavaScriptで独自のわかりやすいエラーメッセージを" +
                "リアルタイムに表示する仕組みを追加しましょう。";
            beforeMessage = "現状：HTML5のネイティブバリデーションのみで、送信ボタンを押すまでエラーがわかりません。";
        } else {
            score = 3;
            level = Level.POOR;
            detail = "リアルタイムバリデーションが検出されませんでした。送信ボタンを押すまでエラーに気づけない可能性があります。";
            suggestion = "「入力しながらチェック」する仕組みを追加しましょう。例えばメールアドレス欄では、" +
                "@マークを入力した時点で形式チェックを行い、すぐに結果を表示します。";
            beforeMessage = "現状：送信ボタンを押すまでエラーがわかりません。高齢者は何度も入力し直すことになります。";
        }

        CriterionResult result = CriterionResult.builder()
                .id("error_timing")
                .name("エラーの表示タイミング")
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
