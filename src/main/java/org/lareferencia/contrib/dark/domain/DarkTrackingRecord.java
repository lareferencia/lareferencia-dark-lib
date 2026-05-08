package org.lareferencia.contrib.dark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "dark_tracking_record",
        indexes = {
                @Index(name = "idx_dark_tracking_ark", columnList = "ark"),
                @Index(name = "idx_dark_tracking_state", columnList = "state"),
                @Index(name = "idx_dark_tracking_reconcile", columnList = "ark_naan, state, oai_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class DarkTrackingRecord {

    @EmbeddedId
    private DarkTrackingRecordId id;

    @Column(unique = true, length = 255)
    private String ark;

    @Column(name = "source_metadata_hash", length = 128)
    private String sourceMetadataHash;

    @Column(name = "target_url", length = 2000)
    private String targetUrl;

    @Convert(converter = DarkTrackingStateConverter.class)
    @Column(name = "state", nullable = false, length = 1)
    private DarkTrackingState state = DarkTrackingState.ERROR;

    @Column(name = "last_error", length = 4000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_staged_at")
    private LocalDateTime lastStagedAt;

    @Column(name = "last_reconciled_at")
    private LocalDateTime lastReconciledAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean hasArk() {
        return ark != null && !ark.isBlank();
    }

    public String getArkNaan() {
        return id != null ? id.getArkNaan() : null;
    }

    public void setArkNaan(String arkNaan) {
        if (id == null) {
            id = new DarkTrackingRecordId();
        }
        id.setArkNaan(arkNaan);
    }

    public String getOaiId() {
        return id != null ? id.getOaiId() : null;
    }

    public void setOaiId(String oaiId) {
        if (id == null) {
            id = new DarkTrackingRecordId();
        }
        id.setOaiId(oaiId);
    }

    public boolean samePayload(String metadataHash, String target) {
        return Objects.equals(sourceMetadataHash, metadataHash) && Objects.equals(targetUrl, target);
    }
}
