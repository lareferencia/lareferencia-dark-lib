package org.lareferencia.core.dark.vo;

import org.lareferencia.backend.domain.OAIRecord;

import java.util.List;

public class DarkRecord {

    String url;
    List<String> authors;

    String title;
    String darkId;
    String oaiId;
    String year;

    public DarkRecord(OAIRecord oaiRecord) {

    }

}
