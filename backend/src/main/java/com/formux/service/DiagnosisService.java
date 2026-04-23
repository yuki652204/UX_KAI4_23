package com.formux.service;

import com.formux.evaluator.*;
import com.formux.model.CriterionResult;
import com.formux.model.DiagnosisRequest;
import com.formux.model.DiagnosisResult;
import com.formux.model.ParsedForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 診断のメインサービス
 * FormParserService → 各Evaluator → スコア集計 → Claude改善提案 の流れを制御する
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final FormParserService formParserService;
    private final SuggestionService suggestionService;

    // 6つのEvaluator（コンストラクタインジェクション）
    private final ValidationMessageEvaluator      validationMessageEvaluator;
    private final LanguageSimplicityEvaluator      languageSimplicityEvaluator;
    private final ErrorTimingEvaluator             errorTimingEvaluator;
    private final MultipleErrorEvaluator           multipleErrorEvaluator;
    private final PasswordRequirementsEvaluator    passwordRequirementsEvaluator;
    private final AutoFocusEvaluator               autoFocusEvaluator;

    /** Step 5: PDF取得のため診断結果をメモリキャッシュ（プロトタイプ用） */
    private final Map<String, DiagnosisResult> resultCache = new ConcurrentHashMap<>();

    /**
     * 診断を実行して結果を返す
     */
    public DiagnosisResult diagnose(DiagnosisRequest request) throws IOException {
        log.info("診断開始: urlMode={}, htmlMode={}", request.isUrlMode(), request.isHtmlMode());

        // Step 2: HTML解析
        ParsedForm parsedForm = request.isUrlMode()
                ? formParserService.parseFromUrl(request.getUrl())
                : formParserService.parseFromHtml(request.getHtml());

        log.debug("フォーム解析完了: formFound={}, errorMessages={}件",
                parsedForm.isFormFound(), parsedForm.getErrorMessages().size());

        // Step 3: 6項目を評価
        List<CriterionResult> criteria = List.of(
            validationMessageEvaluator   .evaluate(parsedForm),  // ① 25点
            languageSimplicityEvaluator  .evaluate(parsedForm),  // ② 20点
            errorTimingEvaluator         .evaluate(parsedForm),  // ③ 15点
            multipleErrorEvaluator       .evaluate(parsedForm),  // ④ 15点
            passwordRequirementsEvaluator.evaluate(parsedForm),  // ⑤ 15点
            autoFocusEvaluator           .evaluate(parsedForm)   // ⑥ 10点
        );

        // Step 4: Claude APIでFAIR/POOR項目の改善提案を強化
        suggestionService.enhanceSuggestions(criteria, parsedForm);

        // 総合スコア計算
        int overallScore = calculateOverallScore(criteria);
        String grade     = DiagnosisResult.toGrade(overallScore);
        String comment   = DiagnosisResult.toComment(grade);

        // 優先改善事項TOP3（スコア率が低い順でPOOR/FAIR項目を抽出）
        List<String> topSuggestions = criteria.stream()
                .filter(c -> c.getLevel() != CriterionResult.Level.GOOD)
                .filter(c -> !"対象外です。".equals(c.getSuggestion()))
                .sorted((a, b) -> Double.compare(a.getScoreRate(), b.getScoreRate()))
                .limit(3)
                .map(CriterionResult::getSuggestion)
                .collect(Collectors.toList());

        String reportId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        DiagnosisResult result = DiagnosisResult.builder()
                .reportId(reportId)
                .overallScore(overallScore)
                .overallGrade(grade)
                .overallComment(comment)
                .criteria(criteria)
                .topSuggestions(topSuggestions)
                .diagnosedAt(LocalDateTime.now())
                .build();

        // Step 5: PDF取得のためキャッシュに保存
        resultCache.put(reportId, result);

        log.info("診断完了: score={}, grade={}, reportId={}", overallScore, grade, reportId);
        return result;
    }

    /** reportId から診断結果を取得（PDF生成用） */
    public Optional<DiagnosisResult> getResult(String reportId) {
        return Optional.ofNullable(resultCache.get(reportId));
    }

    private int calculateOverallScore(List<CriterionResult> criteria) {
        int totalScore = criteria.stream().mapToInt(CriterionResult::getScore).sum();
        int totalMax   = criteria.stream().mapToInt(CriterionResult::getMaxScore).sum();
        if (totalMax == 0) return 0;
        return (int) Math.round((double) totalScore / totalMax * 100);
    }
}
