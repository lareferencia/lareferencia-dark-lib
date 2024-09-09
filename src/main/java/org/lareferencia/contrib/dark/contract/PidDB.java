package org.lareferencia.contrib.dark.contract;

import io.reactivex.Flowable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.reflection.Parameterized;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.5.3.
 */
@SuppressWarnings("rawtypes")
public class PidDB extends Contract {
    public static final String BINARY = "Bin file was not provided";

    public static final String FUNC_ADD_ATTRIBUTE_TO_SCHEMA = "add_attribute_to_schema";

    public static final String FUNC_ADD_EXTERNALPID = "add_externalPid";

    public static final String FUNC_ADD_URL = "add_url";

    public static final String FUNC_ASSING_ID = "assing_id";

    public static final String FUNC_COUNT = "count";

    public static final String FUNC_FIND_ATTRIBUTE_POSITION = "find_attribute_position";

    public static final String FUNC_GET = "get";

    public static final String FUNC_GET_BY_INDEX = "get_by_index";

    public static final String FUNC_GET_BY_NOID = "get_by_noid";

    public static final String FUNC_GET_PAYLOAD = "get_payload";

    public static final String FUNC_get_payload_schema = "get_payload_schema";

    public static final String FUNC_MARK_SCHEMA_AS_CONFIGURED = "mark_schema_as_configured";

    public static final String FUNC_SAVE_PAYLOAD_SCHEMA = "save_payload_schema";

    public static final String FUNC_SET_PAYLOAD_IN_PID = "set_payload_in_pid";

    public static final String FUNC_STORE_PAYLOAD = "store_payload";

    public static final Event ID_EVENT = new Event("ID", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event STORE_PAYLOAD_EVENT = new Event("STORE_PAYLOAD", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Int256>() {}));
    ;

    @Deprecated
    protected PidDB(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected PidDB(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected PidDB(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected PidDB(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<IDEventResponse> getIDEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ID_EVENT, transactionReceipt);
        ArrayList<IDEventResponse> responses = new ArrayList<IDEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            IDEventResponse typedResponse = new IDEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.uuid = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static IDEventResponse getIDEventFromLog(Log log) {
        EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ID_EVENT, log);
        IDEventResponse typedResponse = new IDEventResponse();
        typedResponse.log = log;
        typedResponse.uuid = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.owner = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<IDEventResponse> iDEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getIDEventFromLog(log));
    }

    public Flowable<IDEventResponse> iDEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ID_EVENT));
        return iDEventFlowable(filter);
    }

    public static List<STORE_PAYLOADEventResponse> getSTORE_PAYLOADEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = staticExtractEventParametersWithLog(STORE_PAYLOAD_EVENT, transactionReceipt);
        ArrayList<STORE_PAYLOADEventResponse> responses = new ArrayList<STORE_PAYLOADEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            STORE_PAYLOADEventResponse typedResponse = new STORE_PAYLOADEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.id = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.schema = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.attribute = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static STORE_PAYLOADEventResponse getSTORE_PAYLOADEventFromLog(Log log) {
        EventValuesWithLog eventValues = staticExtractEventParametersWithLog(STORE_PAYLOAD_EVENT, log);
        STORE_PAYLOADEventResponse typedResponse = new STORE_PAYLOADEventResponse();
        typedResponse.log = log;
        typedResponse.id = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.schema = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.attribute = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<STORE_PAYLOADEventResponse> sTORE_PAYLOADEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getSTORE_PAYLOADEventFromLog(log));
    }

    public Flowable<STORE_PAYLOADEventResponse> sTORE_PAYLOADEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(STORE_PAYLOAD_EVENT));
        return sTORE_PAYLOADEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> add_attribute_to_schema(String schema_name, String attribute_name) {
        final Function function = new Function(
                FUNC_ADD_ATTRIBUTE_TO_SCHEMA, 
                Arrays.<Type>asList(new Utf8String(schema_name),
                new Utf8String(attribute_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> add_externalPid(byte[] uuid, byte[] searchTerm_id) {
        final Function function = new Function(
                FUNC_ADD_EXTERNALPID, 
                Arrays.<Type>asList(new Bytes32(uuid),
                new Bytes32(searchTerm_id)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> add_url(byte[] uuid, byte[] url_id) {
        final Function function = new Function(
                FUNC_ADD_URL, 
                Arrays.<Type>asList(new Bytes32(uuid),
                new Bytes32(url_id)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> assing_id(String proveider_addr) {
        final Function function = new Function(
                FUNC_ASSING_ID, 
                Arrays.<Type>asList(new Address(160, proveider_addr)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> count() {
        final Function function = new Function(FUNC_COUNT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> find_attribute_position(PayloadSchema schema, String attribute) {
        final Function function = new Function(FUNC_FIND_ATTRIBUTE_POSITION, 
                Arrays.<Type>asList(schema, 
                new Utf8String(attribute)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Int256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> get(byte[] pid_hash) {
        final Function function = new Function(FUNC_GET, 
                Arrays.<Type>asList(new Bytes32(pid_hash)),
                Arrays.<TypeReference<?>>asList(new TypeReference<PID>() {}));
        return executeRemoteCallSingleValueReturn2(function, Object.class);
    }


    protected <T> RemoteFunctionCall<String> executeRemoteCallSingleValueReturn2(
            Function function, Class<T> returnType) {
        return new RemoteFunctionCall<>(
                function, () -> executeCallSingleValueReturn2(function));
    }



    protected String executeCallSingleValueReturn2(Function function)
            throws IOException {
        return executeCall2(function);
    }

    private String executeCall2(Function function) throws IOException {
        String encodedFunction = FunctionEncoder.encode(function);

        return call(contractAddress, encodedFunction, defaultBlockParameter);

    }


    public RemoteFunctionCall<byte[]> get_by_index(BigInteger index) {
        final Function function = new Function(FUNC_GET_BY_INDEX, 
                Arrays.<Type>asList(new Uint256(index)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<PID> get_by_noid(String noid) {
        final Function function = new Function(FUNC_GET_BY_NOID, 
                Arrays.<Type>asList(new Utf8String(noid)),
                Arrays.<TypeReference<?>>asList(new TypeReference<PID>() {}));
        return executeRemoteCallSingleValueReturn(function, PID.class);
    }

    public RemoteFunctionCall<Payload> get_payload(byte[] id) {
        final Function function = new Function(FUNC_GET_PAYLOAD, 
                Arrays.<Type>asList(new Bytes32(id)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Payload>() {}));
        return executeRemoteCallSingleValueReturn(function, Payload.class);
    }

    public RemoteFunctionCall<PayloadSchema> get_payload_schema(String schema_name) {
        final Function function = new Function(FUNC_get_payload_schema, 
                Arrays.<Type>asList(new Utf8String(schema_name)),
                Arrays.<TypeReference<?>>asList(new TypeReference<PayloadSchema>() {}));
        return executeRemoteCallSingleValueReturn(function, PayloadSchema.class);
    }

    public RemoteFunctionCall<PayloadSchema> get_payload_schema(byte[] schema_hash) {
        final Function function = new Function(FUNC_get_payload_schema, 
                Arrays.<Type>asList(new Bytes32(schema_hash)),
                Arrays.<TypeReference<?>>asList(new TypeReference<PayloadSchema>() {}));
        return executeRemoteCallSingleValueReturn(function, PayloadSchema.class);
    }

    public RemoteFunctionCall<TransactionReceipt> mark_schema_as_configured(String schema_name) {
        final Function function = new Function(
                FUNC_MARK_SCHEMA_AS_CONFIGURED, 
                Arrays.<Type>asList(new Utf8String(schema_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> save_payload_schema(String schema_name) {
        final Function function = new Function(
                FUNC_SAVE_PAYLOAD_SCHEMA, 
                Arrays.<Type>asList(new Utf8String(schema_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_payload_in_pid(byte[] pid_hash_id, byte[] payload_hash_id) {
        final Function function = new Function(
                FUNC_SET_PAYLOAD_IN_PID, 
                Arrays.<Type>asList(new Bytes32(pid_hash_id),
                new Bytes32(payload_hash_id)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> store_payload(byte[] payload_noid, String payload_schema, String payload_attribute, String payload_value) {
        final Function function = new Function(
                FUNC_STORE_PAYLOAD, 
                Arrays.<Type>asList(new Bytes32(payload_noid),
                new Utf8String(payload_schema),
                new Utf8String(payload_attribute),
                new Utf8String(payload_value)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static PidDB load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new PidDB(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static PidDB load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new PidDB(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static PidDB load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new PidDB(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static PidDB load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new PidDB(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static class PayloadSchema extends DynamicStruct {
        public String schema_name;

        public List<String> attribute_list;

        public Boolean configured;

        public PayloadSchema(String schema_name, List<String> attribute_list, Boolean configured) {
            super(new Utf8String(schema_name),
                    new DynamicArray<Utf8String>(
                            Utf8String.class,
                            org.web3j.abi.Utils.typeMap(attribute_list, Utf8String.class)),
                    new Bool(configured));
            this.schema_name = schema_name;
            this.attribute_list = attribute_list;
            this.configured = configured;
        }

        public PayloadSchema(Utf8String schema_name, @Parameterized(type = Utf8String.class) DynamicArray<Utf8String> attribute_list, Bool configured) {
            super(schema_name, attribute_list, configured);
            this.schema_name = schema_name.getValue();
            this.attribute_list = attribute_list.getValue().stream().map(v -> v.getValue()).collect(Collectors.toList());
            this.configured = configured.getValue();
        }
    }

    public static class PID extends DynamicStruct {
        public byte[] pid_hash;

        public String noid;

        public List<byte[]> extarnalPIDs;

        public byte[] url;

        public byte[] payload;

        public String owner;

        public PID(byte[] pid_hash, String noid, List<byte[]> extarnalPIDs, byte[] url, byte[] payload, String owner) {
            super(new Bytes32(pid_hash),
                    new Utf8String(noid),
                    new DynamicArray<Bytes32>(
                            Bytes32.class,
                            org.web3j.abi.Utils.typeMap(extarnalPIDs, Bytes32.class)),
                    new Bytes32(url),
                    new Bytes32(payload),
                    new Address(160, owner));
            this.pid_hash = pid_hash;
            this.noid = noid;
            this.extarnalPIDs = extarnalPIDs;
            this.url = url;
            this.payload = payload;
            this.owner = owner;
        }

        public PID(Bytes32 pid_hash, Utf8String noid, @Parameterized(type = Bytes32.class) DynamicArray<Bytes32> extarnalPIDs, Bytes32 url, Bytes32 payload, Address owner) {
            super(pid_hash, noid, extarnalPIDs, url, payload, owner);
            this.pid_hash = pid_hash.getValue();
            this.noid = noid.getValue();
            this.extarnalPIDs = extarnalPIDs.getValue().stream().map(v -> v.getValue()).collect(Collectors.toList());
            this.url = url.getValue();
            this.payload = payload.getValue();
            this.owner = owner.getValue();
        }
    }

    public static class Payload extends DynamicStruct {
        public byte[] payload_schema;

        public List<String> attributes_values;

        public Payload(byte[] payload_schema, List<String> attributes_values) {
            super(new Bytes32(payload_schema),
                    new DynamicArray<Utf8String>(
                            Utf8String.class,
                            org.web3j.abi.Utils.typeMap(attributes_values, Utf8String.class)));
            this.payload_schema = payload_schema;
            this.attributes_values = attributes_values;
        }

        public Payload(Bytes32 payload_schema, @Parameterized(type = Utf8String.class) DynamicArray<Utf8String> attributes_values) {
            super(payload_schema, attributes_values);
            this.payload_schema = payload_schema.getValue();
            this.attributes_values = attributes_values.getValue().stream().map(v -> v.getValue()).collect(Collectors.toList());
        }
    }

    public static class IDEventResponse extends BaseEventResponse {
        public byte[] uuid;

        public String owner;

        public BigInteger timestamp;
    }

    public static class STORE_PAYLOADEventResponse extends BaseEventResponse {
        public byte[] id;

        public byte[] schema;

        public BigInteger attribute;
    }
}
