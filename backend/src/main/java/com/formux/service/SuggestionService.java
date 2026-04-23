package com.formux.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formux.model.CriterionResult;
import com.formux.model.ParsedForm;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Claude APIを使ってFAIR/POOR項目の改善提案を強化するサービス
 *
 * APIキーが未設定の場合は、Evaluatorが生成したルールベースの提案をそのまま使用する。
 */
@Slf4j
@Service
public class SuggestionService {

    @Value("${formux.claude.api-key:}")
    private String apiKey;

    @Value("${formux.claude.model:claude-opus-4-6}")
    private String modelName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnthropicClient client;

    /** 起動時にAPIクライアントを初期化（キーが設定されている場合のみ） */
    @PostConstruct
    void init() {
        // 優先順位: Spring設定値(CLAUDE_API_KEY) → ANTHROPIC_API_KEY環境変数
        String resolvedKey = apiKey;
        if (resolvedKey == null || resolvedKey.isBlank()) {
            resolvedKey = System.getenv("ANTHROPIC_API_KEY");
        }

        if (resolvedKey != null && !resolvedKey.isBlank()) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(resolvedKey)
                    .build();
            log.info("Claude APIクライアントを初期化しました（モデル: {}）", modelName);
        } else {
            log.warn("APIキーが未設定です（CLAUDE_API_KEY / ANTHROPIC_API_KEY）。" +
                    "改善提案はルールベースのみで生成されます。");
        }
    }

    /**
     * FAIR/POOR評価の項目に対してClaude APIで改善提案を強化する。
     * 提案は CriterionResult#setSuggestion() で直接上書きする。
     *
     * @param criteria 6項目の評価結果（全件）
     * @param form     解析済みフォーム情報（コンテキスト提供用）
     */
    public void enhanceSuggestions(List<CriterionResult> criteria, ParsedForm form) {
        if (client == null) {
            log.debug("APIクライアント未初期化のためスキップ");
            return;
        }

        // 強化対象: FAIR/POOR かつ「対象外」でない項目
        List<CriterionResult> targets = criteria.stream()
                .filter(c -> c.getLevel() != CriterionResult.Level.GOOD)
                .filter(c -> !"対象外です。".equals(c.getSuggestion()))
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            log.debug("改善が必要な項目なし。提案強化をスキップ");
            return;
        }

        log.info("改善提案を強化します（対象: {}項目）", targets.size());

        String prompt = buildPrompt(targets, form);

        try {
            Message response = client.messages().create(
                    MessageCreateParams.builder()
                            .model(Model.CLAUDE_OPUS_4_6)
                            .maxTokens(2048L)
                            .addUserMessage(prompt)
                            .build()
            );

            String content = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining());

            log.debug("Claude APIレスポンス: {}", content);

            applyEnhancedSuggestions(content, criteria);

        } catch (Exception e) {
            log.error("Claude API呼び出しに失敗しました。ルールベースの提案を使用します: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------

    /**
     * Claude へ送るプロンプトを構築する
     */
    private String buildPrompt(List<CriterionResult> targets, ParsedForm form) {
        // フォームから得られるコンテキスト情報
        String errorMessageContext = form.getErrorMessages().isEmpty()
                ? "（エラーメッセージ要素なし）"
                : form.getErrorMessages().stream()
                        .limit(5)
                        .map(m -> "・" + m)
                        .collect(Collectors.joining("\n"));

        // 改善対象項目をJSON風に列挙
        String criteriaContext = targets.stream()
                .map(c -> String.format(
                        "  - id: \"%s\"\n    name: \"%s\"\n    level: %s（%d/%d点）\n    現在の判定理由: %s",
                        c.getId(), c.getName(),
                        c.getLevel().getLabel(), c.getScore(), c.getMaxScore(),
                        c.getDetail()
                ))
                .collect(Collectors.joining("\n\n"));

        return """
                あなたはWebフォームのユーザー体験（UX）改善の専門家です。
                以下のフォーム診断結果をもとに、高齢者（60〜80代）やITリテラシーが低い方にも
                わかりやすい「具体的な改善提案」を各項目について生成してください。

                ===フォームで検出されたエラーメッセージ===
                %s

                ===改善が必要な診断項目===
                %s

                ===生成ルール===
                - 専門用語（バリデーション・フォーマット・セッションなど）を使わない
                - 日常的な言葉とやさしい日本語で書く
                - 「何が問題か」「どう直すか」の両方を含める
                - 具体的な実装例（コード断片や表示文言の例）を含める
                - 1項目あたり200文字以内
                - 高齢者が開発者に「これをやってほしい」と伝えられるレベルの具体性

                ===出力形式===
                以下のJSONのみを出力してください（説明文不要）:
                [
                  {"id": "項目のid", "suggestion": "改善提案文"},
                  ...
                ]

                対象項目のidは次の通りです: %s
                """.formatted(
                errorMessageContext,
                criteriaContext,
                targets.stream().map(c -> "\"" + c.getId() + "\"").collect(Collectors.joining(", "))
        );
    }

    /**
     * Claudeのレスポンスを解析して各CriterionResultの提案を更新する
     */
    private void applyEnhancedSuggestions(String content, List<CriterionResult> allCriteria) {
        try {
            String json = extractJson(content);
            List<Map<String, String>> suggestions =
                    objectMapper.readValue(json, new TypeReference<>() {});

            // id → suggestion のマップを作成
            Map<String, String> suggestionMap = suggestions.stream()
                    .filter(s -> s.containsKey("id") && s.containsKey("suggestion"))
                    .collect(Collectors.toMap(
                            s -> s.get("id"),
                            s -> s.get("suggestion"),
                            (a, b) -> a  // 重複時は最初を使用
                    ));

            // 対応する CriterionResult を更新
            int updated = 0;
            for (CriterionResult criterion : allCriteria) {
                String enhanced = suggestionMap.get(criterion.getId());
                if (enhanced != null && !enhanced.isBlank()) {
                    criterion.setSuggestion(enhanced);
                    updated++;
                }
            }

            log.info("改善提案を強化しました（{}項目更新）", updated);

        } catch (Exception e) {
            log.error("改善提案のパースに失敗しました。ルールベースの提案を維持します: {}", e.getMessage());
        }
    }

    /**
     * Claude のレスポンスから JSON 配列部分を抽出する。
     * Markdown コードブロック（```json ... ```）が付いている場合も対応。
     */
    private String extractJson(String content) {
        // ```json ... ``` ブロックを探す
        Pattern codeBlock = Pattern.compile("```(?:json)?\\s*(\\[.*?])\\s*```", Pattern.DOTALL);
        Matcher m = codeBlock.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }

        // コードブロックなしで直接 [ で始まる JSON を探す
        int start = content.indexOf('[');
        int end   = content.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1).trim();
        }

        return content.trim();
    }
}
