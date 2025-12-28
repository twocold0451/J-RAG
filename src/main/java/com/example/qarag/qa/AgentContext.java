package com.example.qarag.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentContext {
    private static final ThreadLocal<List<UUID>> DOCUMENT_IDS = new ThreadLocal<>();

    public static void setDocumentIds(List<UUID> documentIds) {
        DOCUMENT_IDS.set(documentIds != null ? documentIds : new ArrayList<>());
    }

    public static List<UUID> getDocumentIds() {
        List<UUID> ids = DOCUMENT_IDS.get();
        return ids != null ? ids : new ArrayList<>();
    }

    public static void clear() {
        DOCUMENT_IDS.remove();
    }
}
