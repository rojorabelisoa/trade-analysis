package com.srliq.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Fenêtre d'exclusion autour des annonces à fort impact ({@code srliq.news.*}), en minutes. */
@Validated
@ConfigurationProperties(prefix = "srliq.news")
public class NewsProperties {

    @Min(0)
    @Max(1440)
    private int beforeMinutes = 30;

    @Min(0)
    @Max(1440)
    private int afterMinutes = 30;

    public int getBeforeMinutes() {
        return beforeMinutes;
    }

    public void setBeforeMinutes(int beforeMinutes) {
        this.beforeMinutes = beforeMinutes;
    }

    public int getAfterMinutes() {
        return afterMinutes;
    }

    public void setAfterMinutes(int afterMinutes) {
        this.afterMinutes = afterMinutes;
    }
}
