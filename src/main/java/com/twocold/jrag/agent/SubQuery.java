package com.twocold.jrag.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 带依赖关系的子查询
 * 用于在查询分解时记录子问题之间的依赖关系
 */
@Data
public class SubQuery {

    /**
     * 子查询唯一标识（如 "q1", "q2"）
     */
    private String id;

    /**
     * 子查询内容
     */
    private String query;

    /**
     * 依赖的前置子问题ID列表
     */
    private List<String> dependsOn;

    /**
     * 子查询类型
     * - independent: 独立型，不依赖其他子问题
     * - dependent: 依赖型，需要前置子问题的结果
     */
    private String type;

    /**
     * 依赖原因说明
     */
    private String reason;

    public SubQuery() {
        this.dependsOn = new ArrayList<>();
    }

    /**
     * 是否为独立子查询
     */
    public boolean isIndependent() {
        return "independent".equals(type) || dependsOn.isEmpty();
    }

    /**
     * 是否为依赖型子查询
     */
    public boolean isDependent() {
        return !isIndependent();
    }

    @Override
    public String toString() {
        return String.format("SubQuery{id='%s', query='%s', type='%s', dependsOn=%s}",
                id, query, type, dependsOn);
    }
}
