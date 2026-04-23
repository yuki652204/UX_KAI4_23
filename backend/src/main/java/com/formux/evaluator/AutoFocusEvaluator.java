package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.CriterionResult.Level;
import com.formux.model.ParsedForm;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ⑥ エラー箇所へのフォーカス自動移動（満点: 10点）
 *
 * 採点基準:
 *   POOR (0〜3点)  : フォーカス移動の実装なし
 *   FAIR (4〜7点)  : スクロールのみ、またはARIA属性のみ
 *   GOOD (8〜10点) : フォーカス移動 + ARIA属性両方あり
 */
@Component
public class AutoFocusEvaluator implements CriterionEvaluator {

    private static final int MAX_SCORE = 10;

    // フォーカスを移動するJSパターン
    private static final Pattern FOCUS_PATTERN = Pattern.compile(
        "\\.focus\\(\\)|" +
        "focus\\s*\\(\\s*\\)|" +
        "\\.setFocus\\(|" +
        "focusField|" +
        "focusError|" +
        "autoFocus|" +         // React属性
        "autofocus",           // HTML属性
        Pattern.CASE_INSENSITIVE
    );

    // スクロールして移動するパターン
    private static final Pattern SCROLL_PATTERN = Pattern.compile(
        "scrollIntoView|" +
        "scrollTo\\s*\\(|" +
        "scroll\\s*\\(|" +
        "window\\.scrollTo|" +
        "smoothscroll|" +
        "scrollTop",
        Pattern.CASE_INSENSITIVE
    );

    // ARIAアクセシビリティ属性パターン（支援技術でのフォーカス管理）
    private static final Pattern ARIA_PATTERN = Pattern.compile(
        "aria-invalid|" +
        "aria-describedby|" +
        "aria-errormessage|" +
        "role=['\"]alert['\"]|" +
        "aria-live=['\"]|" +
        "aria-atomic",
        Pattern.CASE_INSENSITIVE
    );

    // エラーサマリーへのリンク（#エラー箇所へのアンカーリンク）
    private static final Pattern ERROR_LINK_PATTERN = Pattern.compile(
        "href=['\"]#[^'\"]+['\"].*error|" +
        "href=['\"]#[^'\"]+['\"].*invalid|" +
        "<a[^>]+href=['\"]#",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public CriterionResult evaluate(ParsedForm form) {
        String script  = form.getScriptText();
        String formHtml = form.getFormHtml();
        String rawHtml  = form.getRawHtml();

        boolean hasFocus      = FOCUS_PATTERN .matcher(script).find()
                                || FOCUS_PATTERN.matcher(formHtml).find();
        boolean hasScroll     = SCROLL_PATTERN.matcher(script).find();
        boolean hasAria       = ARIA_PATTERN  .matcher(formHtml).find()
                                || form.isHasAriaErrorAttributes();
        boolean hasErrorLinks = ERROR_LINK_PATTERN.matcher(rawHtml).find();

        int score;
        Level level;
        String detail;
        String suggestion;
        String beforeMessage;

        if ((hasFocus || hasScroll) && hasAria) {
            score = 10;
            level = Level.GOOD;
            detail = "エラー箇所へのフォーカス移動またはスクロール移動と、スクリーンリーダー対応のARIA属性の両方が実装されています。";
            suggestion = "現状を維持してください。エラー要素にフォーカスが当たった際にアニメーションや色変化を加えると視覚的にも分かりやすくなります。";
            beforeMessage = "現状：エラー箇所へのフォーカス移動とARIA属性の両方が実装されています。";
        } else if (hasFocus || hasScroll) {
            score = 7;
            level = Level.FAIR;
            detail = "エラー箇所への移動（フォーカスまたはスクロール）は実装されていますが、" +
                "スクリーンリーダー向けのARIA属性（aria-invalid, role=alertなど）が不足しています。";
            suggestion = "フォーカス移動に加えて、エラーが発生した入力欄に aria-invalid=\"true\" を、" +
                "エラーメッセージ要素に role=\"alert\" または aria-live=\"polite\" を追加しましょう。";
            beforeMessage = "現状：フォーカス・スクロール移動はありますが、スクリーンリーダー向けのARIA属性が不足しています。";
        } else if (hasAria) {
            score = 5;
            level = Level.FAIR;
            detail = "ARIA属性によるアクセシビリティ対応は確認されましたが、視覚的なフォーカス移動が検出されませんでした。";
            suggestion = "エラー送信後にJavaScriptで `.focus()` を使って最初のエラー欄に自動的にカーソルを移動させましょう。" +
                "高齢者はどこを直せばよいか一目でわかります。";
            beforeMessage = "現状：ARIA属性はありますが、視覚的なフォーカス自動移動が実装されていません。";
        } else if (hasErrorLinks) {
            score = 4;
            level = Level.FAIR;
            detail = "エラー箇所へのリンク（アンカーリンク）が確認されました。フォーカス自動移動の代替手段として機能しています。";
            suggestion = "リンクによる移動に加えて、JavaScriptで `.focus()` を使った自動フォーカス移動を実装しましょう。";
            beforeMessage = "現状：エラー箇所へのアンカーリンクがありますが、JavaScriptによる自動フォーカス移動がありません。";
        } else {
            score = 0;
            level = Level.POOR;
            detail = "エラー発生時に入力欄への自動フォーカス移動が検出されませんでした。" +
                "高齢者はどの欄を直せばよいか自分で探さなければなりません。";
            suggestion = "送信後のバリデーションエラー時に、最初のエラー入力欄に自動的にフォーカスを移動させましょう。\n" +
                "JavaScript例: document.querySelector('.error-field').focus()\n" +
                "同時に aria-invalid=\"true\" と aria-describedby でエラーメッセージと紐づけましょう。";
            beforeMessage = "現状：エラー発生時に、どこを修正すればよいか視覚的・技術的なガイドが何もありません。";
        }

        CriterionResult result = CriterionResult.builder()
                .id("auto_focus")
                .name("エラー箇所へのフォーカス自動移動")
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
