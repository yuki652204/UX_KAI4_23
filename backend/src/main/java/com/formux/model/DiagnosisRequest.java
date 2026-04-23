package com.formux.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 診断リクエスト
 * url か html のどちらか一方を指定する
 */
@Getter
@Setter
public class DiagnosisRequest {

    /** 診断対象のURL（URLモード） */
    private String url;

    /** HTMLテキスト（ペーストモード） */
    private String html;

    public boolean isUrlMode() {
        return url != null && !url.isBlank();
    }

    public boolean isHtmlMode() {
        return html != null && !html.isBlank();
    }

    public boolean isValid() {
        return isUrlMode() || isHtmlMode();
    }
}
