package corecord.dev.domain.analysis.converter;

import corecord.dev.domain.analysis.constant.Keyword;
import corecord.dev.domain.analysis.dto.response.AnalysisResponse;
import corecord.dev.domain.analysis.entity.Ability;
import corecord.dev.domain.analysis.entity.Analysis;
import corecord.dev.domain.record.entity.Record;
import corecord.dev.domain.user.entity.User;

import java.util.List;

public class AnalysisConverter {
    public static Analysis toAnalysis(String comment, Record record) {
        return Analysis.builder()
                .comment(comment)
                .record(record)
                .build();
    }

    public static Ability toAbility(Keyword keyword, String content, Analysis analysis, User user) {
        return Ability.builder()
                .keyword(keyword)
                .content(content)
                .analysis(analysis)
                .user(user)
                .build();
    }

    public static AnalysisResponse.AbilityDto toAbilityDto(Ability ability) {
        return AnalysisResponse.AbilityDto.builder()
                .keyword(ability.getKeyword().getValue())
                .content(ability.getContent())
                .build();
    }

    public static AnalysisResponse.AnalysisDto toAnalysisDto(Analysis analysis) {
        Record record = analysis.getRecord();

        // TODO: keyword 정렬 순서 고려 필요
        List<AnalysisResponse.AbilityDto> abilityDtoList = analysis.getAbilityList().stream()
                .map(AnalysisConverter::toAbilityDto)
                .toList();

        return AnalysisResponse.AnalysisDto.builder()
                .analysisId(analysis.getAnalysisId())
                .recordId(record.getRecordId())
                .recordTitle(record.getTitle())
                .recordContent(record.getContent())
                .abilityDtoList(abilityDtoList)
                .comment(analysis.getComment())
                .createdAt(analysis.getCreatedAtFormatted())
                .build();
    }
}
