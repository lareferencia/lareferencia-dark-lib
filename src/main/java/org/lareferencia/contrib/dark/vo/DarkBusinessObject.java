package org.lareferencia.contrib.dark.vo;

import lombok.Getter;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.contrib.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;

import java.util.Optional;

@Getter
public class DarkBusinessObject {

    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    private OAIRecord oaiRecord;
    private Optional<OAIIdentifierDark> darkOptional;
    private OAIRecordMetadata oaiRecordMetadata;
    private IMetadataRecordStoreService metadataStoreService;
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;
    private boolean darkTrackingWillBeSaved;


    public DarkBusinessObject(OAIRecord oaiRecord, IMetadataRecordStoreService metadataStoreService, OAIIdentifierDarkRepository oaiIdentifierDarkRepository)
            throws OAIRecordMetadataParseException, MetadataRecordStoreException {
        this.oaiRecord = oaiRecord;
        this.darkOptional = oaiIdentifierDarkRepository.findByOaiIdentifier(oaiRecord.getIdentifier());
        this.metadataStoreService = metadataStoreService;
        this.oaiIdentifierDarkRepository = oaiIdentifierDarkRepository;
        this.oaiRecordMetadata = metadataStoreService.getPublishedMetadata(oaiRecord);
    }

    public String getItemUrlFromCollectedMetadata() {

        return oaiRecordMetadata.getFieldOcurrences("dc.identifier.*").stream()
                .filter(identifier -> identifier.startsWith("http://") || identifier.startsWith("https://"))
                .findFirst().get().trim();
    }


    public boolean isThisATrackedDarkObject() {
        return darkOptional.isPresent() || darkTrackingWillBeSaved;
    }

    private void addDarkIdToInternalTrackingIfNeeded() {
        if (!isThisATrackedDarkObject() && itemMetadataAlreadyHasDarkId()) {
            oaiIdentifierDarkRepository.save(
                    new OAIIdentifierDark(
                        getDarkIdFromMetadata(),
                        getIdentifier(),
                        getItemUrlFromCollectedMetadata()));

            this.darkTrackingWillBeSaved = true;

        }


    }

    private String getDarkIdFromMetadata() {
        String darkIdFromMetadata = oaiRecordMetadata.getFieldValue(DC_IDENTIFIER_DARK);
        if(darkIdFromMetadata != null && !"".equals(darkIdFromMetadata.trim())) {
            return darkIdFromMetadata.trim();
        }
        return null;
    }

    public String getDarkIdFromTracking() {
        if(darkOptional.isPresent()) {
            return darkOptional.get().getDarkIdentifier();
        }
        return null;
    }

    public String getDarkIdFromAnySourceIfExists() {
        if(getDarkIdFromMetadata() != null) {
            return getDarkIdFromMetadata();
        }
        else {
            return getDarkIdFromTracking();
        }
    }

    private boolean itemMetadataAlreadyHasDarkId() {
        String darkId = getDarkIdFromMetadata();
        return darkId != null && !"".equals(darkId);
    }

    public void setDarkId(String darkId) {
        oaiRecordMetadata.addFieldOcurrence(DarkBusinessObject.DC_IDENTIFIER_DARK, darkId);
    }

    public String getIdentifier() {
        return oaiRecordMetadata.getIdentifier();
    }

    private void addDarkIdToItemMetadataIfNeeded() {

        boolean doesNotHasDarkIdInMetadata = !itemMetadataAlreadyHasDarkId();
        if (darkOptional.isPresent() && doesNotHasDarkIdInMetadata) {
            oaiRecordMetadata.addFieldOcurrence(DarkBusinessObject.DC_IDENTIFIER_DARK, darkOptional.get().getDarkIdentifier());
            metadataStoreService.updatePublishedMetadata(oaiRecord, oaiRecordMetadata);
        }

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

    public void normalizeMetadataAndTrackingIfNeeded() {
        addDarkIdToItemMetadataIfNeeded();
        addDarkIdToInternalTrackingIfNeeded();
    }

    public enum Situation {
        NEED_TO_REGISTER,
        NEED_TO_UPDATE_URL,
        NO_ACTION_NEEDED
    }
}
