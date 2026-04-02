package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReserveBatchRequest {

    @JsonProperty("authority_id")
    private String authorityId;

    private String naan;

    private List<ReserveBatchItem> items;

    public static ReserveBatchRequest fromClientItemIds(String authorityId, String naan, List<String> clientItemIds) {
        List<ReserveBatchItem> items = clientItemIds.stream()
                .map(ReserveBatchItem::new)
                .toList();
        return new ReserveBatchRequest(authorityId, naan, items);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReserveBatchItem {

        @JsonProperty("client_item_id")
        private String clientItemId;
    }
}
