package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ④ 複数エラーの同時表示（満点: 15点）
 *
 * 採点基準:
 *   POOR (0〜4点)  : 1件ずつしか表示しない（early return / breakあり）
 *   FAIR (5〜10点) : 複数エラーを表示できるが、サマリーがない
 *   GOOD (11〜15点): 複数エラーをまとめて表示 + エラーサマリーあり
 */
@Component
public class MultipleErrorEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 15;

    // エラーを1件だけ表示して処理を打ち切るパターン（早期return/break）
    private static final Pattern SINGLE_ERROR_PATTERN = Pattern.compile(
        "if\\s*\\(.*\\bvalid\\b.*\\)\\s*\\{[^}]*\\breturn\\b|" +
        "if\\s*\\(.*error.*\\)\\s*\\{[^}]*\\breturn\\b[^;]*;\\s*\\}|" +
        "errors\\.length\\s*>\\s*0\\s*\\{[^}]*\\breturn\\b|" +
        "hasError\\s*&&\\s*return",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // エラーサマリー（エラー一覧をまとめて表示）のパターン
    private static final Pattern ERROR_SUMMARY_PATTERN = Pattern.compile(
        "error.?summary|" +
        "errors.?list|" +
        "error.?list|" +
        "alert-danger|" +
        "errors?\\s*\\.\\s*(push|add|append)|" +  // errors配列に追加するパターン
        "\\[\\s*\\.\\.\\.errors|" +               // スプレッド演算子でエラー配列結合
        "errors\\.forEach|" +
        "エラー一覧|エラーリスト|" +
        "以下.*エラー|" +
        "[0-9]+件.*エラー|" +
        "\\$\\.each.*error",
        Pattern.CASE_INSENSITIVE
    );

    // エラー要素が複数あるかを示すパターン（querySelectorAllなど）
    private static final Pattern MULTI_DISPLAY_PATTERN = Pattern.compile(
        "querySelectorAll|" +
        "getElementsByClassName|" +
        "\\.errors\\b|" +                 // 複数形のプロパティ
        "forEach.*error|" +
        "map.*error|" +
        "errors\\s*\\.\\s*length|" +
        "setErrors|setFieldError",        // React Hook Form, Formikなど
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        String script = form.getScriptText();
        String formHtml = form.getFormHtml();
        int errorMessageCount = form.getErrorMessages().size();

        boolean hasSingleErrorPattern = SINGLE_ERROR_PATTERN.matcher(script).find();
        boolean hasErrorSummary       = ERROR_SUMMARY_PATTERN.matcher(script).find()
                                        || ERROR_SUMMARY_PATTERN.matcher(formHtml).find();
        boolean hasMultiDisplay       = MULTI_DISPLAY_PATTERN.matcher(script).find();

        // 実際に複数のエラーメッセージ要素がHTMLに存在するか
        boolean multipleErrorsVisible = errorMessageCount >= 2;

        int score;
        Level level;
        String detail;
        String suggestion;
        String beforeMessage;

        if (hasErrorSummary || (multipleErrorsVisible && hasMultiDisplay)) {
            score = 15;
            level = Level.GOOD;
            detail = "複数のエラーをまとめて表示するエラーサマリーの実装が検出されました。" +
                "高齢者が一度ですべての問題点を確認できます。";
            suggestion = "エラーサマリーを継続してください。エラー件数を「3件の入力エラーがあります」と" +
                "冒頭に表示するとさらにわかりやすくなります。";
            beforeMessage = "現状：複数のエラーをまとめて表示するエラーサマリーが実装されています。";
        } else if (multipleErrorsVisible || hasMultiDisplay) {
            score = 10;
            level = Level.FAIR;
            detail = "複数のエラーを表示できる実装が確認されましたが、エラーサマリー（まとめ表示）は検出されませんでした。";
            suggestion = "ページ上部に「〇件のエラーがあります」とまとめて表示するエラーサマリーを追加しましょう。" +
                "スクロールしなくても全エラーを把握できます。";
            beforeMessage = "現状：複数のエラーを表示できますが、ページ上部にまとめるエラーサマリーがありません。";
        } else if (hasSingleErrorPattern) {
            score = 3;
            level = Level.POOR;
            detail = "エラーが1件発生した時点で処理を打ち切る実装が検出されました。" +
                "高齢者は修正のたびに送信→エラー確認を繰り返すことになります。";
            suggestion = "すべての入力項目を一度に検証し、エラーをまとめて表示する方式に変更しましょう。" +
                "「名前・メールアドレス・パスワードの3か所に入力が必要です」のように示します。";
            beforeMessage = "現状：エラーが1件出た時点で処理が止まり、他の入力欄のエラーが見えません。";
        } else {
            score = 5;
            level = Level.FAIR;
            detail = "複数エラーの表示方式を判定できませんでした（JSが外部ファイルの可能性があります）。";
            suggestion = "エラーが複数ある場合は、送信後にすべてのエラーを一画面で確認できるように実装しましょう。";
            beforeMessage = "現状：複数エラーの表示方式を判定できませんでした（JSが外部ファイルの可能性があります）。";
        }

        CriterionResult result = CriterionResult.builder()
                .id("multiple_error")
                .name("複数エラーの同時表示")
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
