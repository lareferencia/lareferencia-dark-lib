package org.lareferencia.contrib.dark.vo;

import lombok.Getter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.IOAIRecord;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Getter
public class DarkBusinessObject {

    private static Logger logger = LogManager.getLogger(DarkBusinessObject.class);

    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    private IOAIRecord oaiRecord;
    private Optional<OAIIdentifierDark> darkOptional;
    private OAIRecordMetadata oaiRecordMetadata;
    private boolean darkTrackingWillBeSaved;


    public DarkBusinessObject(IOAIRecord oaiRecord, OAIRecordMetadata oaiRecordMetadata, Optional<OAIIdentifierDark> darkOptional)
            throws OAIRecordMetadataParseException, MetadataRecordStoreException {
        this.oaiRecord = oaiRecord;
        this.darkOptional = darkOptional;
        this.oaiRecordMetadata = oaiRecordMetadata;
    }

    public String getItemUrlFromCollectedMetadata() {

        List<String> urls = oaiRecordMetadata.getFieldOcurrences("dc.identifier.*").stream()
                .filter(identifier -> identifier.startsWith("http://") || identifier.startsWith("https://"))
                .collect(Collectors.toList());

        if(!urls.isEmpty()) {
            String choosenUrl = urls.stream()
                    // First option: DOI
                    .filter(url -> url.toLowerCase().matches("https://doi.org/(.*)")).findFirst()
                    // Second option
                    .orElseGet(() -> urls.stream().filter(url -> url.toLowerCase().matches("http(.*)/handle/(.*)")).findFirst()
                            // If there's no DOI or Handle we choose the URL with greatest lenght
                            .orElseGet(() -> {
                                Map.Entry<Integer, String> candidate = urls.stream().collect(
                                        Collectors.toMap(
                                                String::length, String::trim, (o1, o2) -> o1, TreeMap::new)
                                ).descendingMap().firstEntry();
                                return candidate != null ? candidate.getValue() : urls.get(0);
                            }));
            return choosenUrl;
        }

        return "";
    }


    public boolean isThisATrackedDarkObject() {
        return darkOptional.isPresent() || darkTrackingWillBeSaved;
    }

    public Optional<OAIIdentifierDark> getOptionalDarkIdentifier() {
        if (!isThisATrackedDarkObject() && itemMetadataAlreadyHasDarkId()) {
            this.darkTrackingWillBeSaved = true;
            return Optional.of(new OAIIdentifierDark(
                    getDarkIdFromMetadata(),
                    getIdentifier(),
                    getItemUrlFromCollectedMetadata()));
        }
        return Optional.empty();
    }

    public Optional<OAIRecordMetadata> getOptionalUpdatedMetadata() {

        boolean doesNotHasDarkIdInMetadata = !itemMetadataAlreadyHasDarkId();
        if (darkOptional.isPresent() && doesNotHasDarkIdInMetadata) {
            oaiRecordMetadata.addFieldOcurrence(DarkBusinessObject.DC_IDENTIFIER_DARK, darkOptional.get().getDarkIdentifier());
            return Optional.of(oaiRecordMetadata);
        }

        return Optional.empty();
    }
    

    private String getDarkIdFromMetadata() {
        String darkIdFromMetadata = oaiRecordMetadata.getFieldValue(DC_IDENTIFIER_DARK);
        if(darkIdFromMetadata != null && !"".equals(darkIdFromMetadata.trim())) {
            return darkIdFromMetadata.trim();
        }
        return null;
    }

    public String getDarkIdFromTracking() {
        return darkOptional.map(OAIIdentifierDark::getDarkIdentifier).orElse(null);
    }


    private boolean itemMetadataAlreadyHasDarkId() {
        String darkId = getDarkIdFromMetadata();
        return darkId != null && !darkId.isEmpty();
    }

    public void setDarkId(String darkId) {
        oaiRecordMetadata.addFieldOcurrence(DarkBusinessObject.DC_IDENTIFIER_DARK, darkId);
    }

    public String getIdentifier() {
        return oaiRecordMetadata.getIdentifier();
    }

    


    public boolean needToChangeUrl() {

        return
                isThisATrackedDarkObject() &&
                        darkOptional.isPresent() &&
                        !getItemUrlFromCollectedMetadata().equals(darkOptional.get().getItemUrl());

    }

    public boolean needToRegister() {
        return !isThisATrackedDarkObject();
    }


    public Situation getSituation() {
        if(needToRegister()) {
            return Situation.NEED_TO_REGISTER;
        }
        else if(needToChangeUrl()) {
            return Situation.NEED_TO_UPDATE_URL;
        }

        else return Situation.NO_ACTION_NEEDED;
    }

    public enum Situation {
        NEED_TO_REGISTER,
        NEED_TO_UPDATE_URL,
        NO_ACTION_NEEDED
    }
}
