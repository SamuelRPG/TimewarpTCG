package com.saroga.timewarp.infrastructure.web.dto.response;

/** Requisito de objetivo de una carta: "count" objetivos del grupo "group" (ALLY, ENEMY, ANY_UNIT...). */
public class TargetReqDto {
    public int count;
    public String group;

    public TargetReqDto(int count, String group) {
        this.count = count;
        this.group = group;
    }
}
