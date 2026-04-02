package org.lareferencia.contrib.dark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode
public class DarkTrackingRecordId implements Serializable {

    @Column(name = "ark_naan", nullable = false, updatable = false, length = 64)
    private String arkNaan;

    @Column(name = "oai_id", nullable = false, updatable = false, length = 512)
    private String oaiId;
}
