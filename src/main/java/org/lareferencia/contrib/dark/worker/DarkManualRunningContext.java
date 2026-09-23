package org.lareferencia.contrib.dark.worker;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.worker.NetworkRunningContext;

import java.util.List;
import java.util.UUID;

/** Context for a non-durable, explicitly selected dARK legacy command. */
public class DarkManualRunningContext extends NetworkRunningContext {

    public enum Action { STAGE, RECONCILE }

    private final String commandId;
    private final String requestedBy;
    private final Action action;
    /** Snapshot that produced the selected payload. Null is valid for reconciliation. */
    private final Long sourceSnapshotId;
    private final List<String> oaiIds;

    public DarkManualRunningContext(Network network, String requestedBy, Action action, List<String> oaiIds) {
        this(UUID.randomUUID().toString(), network, null, requestedBy, action, oaiIds);
    }

    public DarkManualRunningContext(String commandId, Network network, String requestedBy, Action action, List<String> oaiIds) {
        this(commandId, network, null, requestedBy, action, oaiIds);
    }

    public DarkManualRunningContext(String commandId, Network network, Long sourceSnapshotId,
            String requestedBy, Action action, List<String> oaiIds) {
        super(network, "DARK_MANUAL_" + action.name(), JsonNodeFactory.instance.objectNode());
        this.commandId = commandId;
        this.sourceSnapshotId = sourceSnapshotId;
        this.requestedBy = requestedBy;
        this.action = action;
        this.oaiIds = List.copyOf(oaiIds);
    }

    public String getCommandId() { return commandId; }
    public String getRequestedBy() { return requestedBy; }
    public Action getAction() { return action; }
    public Long getSourceSnapshotId() { return sourceSnapshotId; }
    public List<String> getOaiIds() { return oaiIds; }

    @Override
    public String getId() {
        return "DARK_MANUAL::" + commandId;
    }
}
