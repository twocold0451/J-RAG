package com.twocold.jrag.api.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Setter
@Getter
@Data
public class AddDocumentsRequest {
    private List<UUID> documentIds;

}