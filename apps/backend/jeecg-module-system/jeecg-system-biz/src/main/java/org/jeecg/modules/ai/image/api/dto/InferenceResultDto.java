package org.jeecg.modules.ai.image.api.dto;

import org.jeecg.modules.ai.asset.api.dto.AssetDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import org.jeecg.modules.ai.result.domain.ResultType;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InferenceResultDto {
    private ResultType resultType;
    private Boolean simulated;
    private DetectionDataDto data;
    private List<AssetDto> artifacts;

    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
    }

    public Boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(Boolean simulated) {
        this.simulated = simulated;
    }

    public DetectionDataDto getData() {
        return data;
    }

    public void setData(DetectionDataDto data) {
        this.data = data;
    }

    public List<AssetDto> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<AssetDto> artifacts) {
        this.artifacts = artifacts;
    }
}
