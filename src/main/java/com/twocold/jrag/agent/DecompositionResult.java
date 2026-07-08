package com.twocold.jrag.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询分解结果
 * 包含子查询列表和依赖关系图
 */
@Data
public class DecompositionResult {

    /**
     * 子查询列表
     */
    private List<SubQuery> subQueries;

    public DecompositionResult() {
        this.subQueries = new ArrayList<>();
    }

    public DecompositionResult(List<SubQuery> subQueries) {
        this.subQueries = subQueries != null ? subQueries : new ArrayList<>();
    }

    /**
     * 构建依赖图
     * key: 子查询ID
     * value: 依赖的子查询ID列表
     */
    public Map<String, List<String>> getDependencyGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        for (SubQuery sq : subQueries) {
            graph.put(sq.getId(), sq.getDependsOn());
        }
        return graph;
    }

    /**
     * 根据ID获取子查询
     */
    public SubQuery getById(String id) {
        return subQueries.stream()
                .filter(sq -> sq.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return subQueries == null || subQueries.isEmpty();
    }

    /**
     * 获取子查询数量
     */
    public int size() {
        return subQueries != null ? subQueries.size() : 0;
    }

    @Override
    public String toString() {
        return String.format("DecompositionResult{subQueries=%s}", subQueries);
    }
}
