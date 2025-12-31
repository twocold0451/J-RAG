package com.twocold.jrag.api.dto;

import com.twocold.jrag.domain.Chunk;

import java.util.List;

public record QaResponse(String answer, List<Chunk> sources) {
}
