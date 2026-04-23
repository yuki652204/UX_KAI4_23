package com.formux.evaluator;

import com.formux.model.CriterionResult;
import com.formux.model.ParsedForm;

/**
 * 診断項目の評価を行うインターフェース
 */
public interface CriterionEvaluator {

    /**
     * ParsedFormを受け取り、この項目の評価結果を返す
     */
    CriterionResult evaluate(ParsedForm form);
}
