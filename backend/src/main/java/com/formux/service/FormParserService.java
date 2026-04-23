package com.formux.service;

import com.formux.model.ParsedForm;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HTMLを取得・解析して ParsedForm を生成する
 */
@Slf4j
@Service
public class FormParserService {

    private static final int FETCH_TIMEOUT_MS = 10_000;

    /**
     * URLからHTMLを取得して解析する
     */
    public ParsedForm parseFromUrl(String url) throws IOException {
        log.debug("URLからフォームを取得します: {}", url);
        Document doc = Jsoup.connect(url)
                .timeout(FETCH_TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (FormUX-Diagnosis-Tool/1.0)")
                .get();
        return parse(doc);
    }

    /**
     * HTMLテキストを直接解析する
     */
    public ParsedForm parseFromHtml(String html) {
        log.debug("HTMLテキストを解析します（{}文字）", html.length());
        Document doc = Jsoup.parse(html);
        return parse(doc);
    }

    // ---------------------------------------------------------------

    private ParsedForm parse(Document doc) {
        // フォーム要素を取得（最初のformを対象）
        Element form = doc.selectFirst("form");
        boolean formFound = form != null;
        String formHtml = formFound ? form.outerHtml() : doc.body().outerHtml();

        // scriptタグのテキストを結合
        String scriptText = doc.select("script").stream()
                .map(Element::data)
                .collect(Collectors.joining("\n"));

        // エラー表示要素を収集
        List<String> errorMessages = collectErrorMessages(doc);

        // input/select/textareaのtype属性
        List<String> inputTypes = collectInputTypes(form != null ? form : doc.body());

        // labelテキスト
        List<String> labelTexts = doc.select("label").stream()
                .map(Element::text)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());

        // passwordフィールドの有無
        boolean hasPasswordField = !doc.select("input[type=password]").isEmpty();

        // passwordフィールド周辺テキスト（ヒント・要件テキスト）
        List<String> passwordHintTexts = collectPasswordHints(doc);

        // ARIA属性の有無
        boolean hasAriaErrorAttributes = hasAriaErrorAttributes(doc);

        return ParsedForm.builder()
                .rawHtml(doc.outerHtml())
                .formHtml(formHtml)
                .scriptText(scriptText)
                .errorMessages(errorMessages)
                .inputTypes(inputTypes)
                .labelTexts(labelTexts)
                .passwordHintTexts(passwordHintTexts)
                .hasAriaErrorAttributes(hasAriaErrorAttributes)
                .hasPasswordField(hasPasswordField)
                .formFound(formFound)
                .build();
    }

    /**
     * エラー表示と思われる要素のテキストを収集する
     *
     * 対象セレクター:
     *   - class名に error/invalid/alert/warning/danger を含む要素
     *   - role="alert" の要素
     *   - aria-live 属性を持つ要素
     *   - .help-block（Bootstrap 3 の慣例）
     */
    private List<String> collectErrorMessages(Document doc) {
        List<String> messages = new ArrayList<>();

        // class名でエラー要素を検索
        Elements byClass = doc.select(
            "[class*=error], [class*=invalid], [class*=alert], " +
            "[class*=warning], [class*=danger], [class*=help-block], " +
            "[class*=feedback], [class*=message]"
        );
        byClass.stream()
                .map(Element::text)
                .filter(t -> !t.isBlank())
                .forEach(messages::add);

        // ARIA属性でエラー要素を検索
        doc.select("[role=alert], [aria-live]").stream()
                .map(Element::text)
                .filter(t -> !t.isBlank())
                .forEach(messages::add);

        // data属性でエラー要素を検索
        doc.select("[data-error], [data-validate]").stream()
                .map(e -> {
                    String dataError = e.attr("data-error");
                    return dataError.isBlank() ? e.text() : dataError;
                })
                .filter(t -> !t.isBlank())
                .forEach(messages::add);

        return messages.stream().distinct().collect(Collectors.toList());
    }

    /**
     * フォーム内のinput/select/textareaのtype属性を収集する
     */
    private List<String> collectInputTypes(Element root) {
        List<String> types = new ArrayList<>();
        root.select("input").forEach(el -> {
            String type = el.attr("type");
            types.add(type.isBlank() ? "text" : type.toLowerCase());
        });
        root.select("select").forEach(el -> types.add("select"));
        root.select("textarea").forEach(el -> types.add("textarea"));
        return types;
    }

    /**
     * passwordフィールド周辺のテキストを収集する（要件明示の検出用）
     *
     * 収集範囲:
     *   - passwordフィールドの直前/直後の兄弟要素テキスト
     *   - passwordフィールドの親要素テキスト
     *   - passwordフィールドの aria-describedby が指す要素テキスト
     */
    private List<String> collectPasswordHints(Document doc) {
        List<String> hints = new ArrayList<>();
        Elements passwordFields = doc.select("input[type=password]");

        for (Element pw : passwordFields) {
            // 親要素のテキスト
            Element parent = pw.parent();
            if (parent != null) {
                hints.add(parent.text());
            }

            // 直前・直後の兄弟要素テキスト
            Element prev = pw.previousElementSibling();
            if (prev != null) hints.add(prev.text());
            Element next = pw.nextElementSibling();
            if (next != null) hints.add(next.text());

            // aria-describedby が指す要素のテキスト
            String describedBy = pw.attr("aria-describedby");
            if (!describedBy.isBlank()) {
                for (String id : describedBy.split("\\s+")) {
                    Element described = doc.getElementById(id);
                    if (described != null) hints.add(described.text());
                }
            }
        }

        return hints.stream()
                .filter(t -> !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * アクセシビリティ関連のARIA属性が存在するか確認する
     */
    private boolean hasAriaErrorAttributes(Document doc) {
        return !doc.select("[aria-invalid], [aria-describedby], [role=alert], [aria-live]").isEmpty();
    }
}
