package com.example.qarag.api.dto;

import com.example.qarag.domain.Chunk;

import java.util.List;

public record QaResponse(String answer, List<Chunk> sources) {
}
