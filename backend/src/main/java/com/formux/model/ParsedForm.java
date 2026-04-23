package com.formux.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Jsoupがフォームから抽出した情報
 * 各Evaluatorはこのオブジェクトを受け取って評価する
 */
@Getter
@Builder
public class ParsedForm {

    /** ページ全体のHTMLテキスト */
    private final String rawHtml;

    /** &lt;form&gt;要素内のHTMLテキスト */
    private final String formHtml;

    /** 全scriptタグのテキスト（JS解析用） */
    private final String scriptText;

    /** エラー表示要素のテキスト一覧 */
    private final List<String> errorMessages;

    /** input/select/textareaのtype属性一覧 */
    private final List<String> inputTypes;

    /** label要素のテキスト一覧 */
    private final List<String> labelTexts;

    /** passwordフィールド周辺のテキスト一覧 */
    private final List<String> passwordHintTexts;

    /** aria-describedby, aria-live, role=alert等のARIA属性が存在するか */
    private final boolean hasAriaErrorAttributes;

    /** パスワードフィールドが存在するか */
    private final boolean hasPasswordField;

    /** フォームが存在するか（解析成功か） */
    private final boolean formFound;
}
