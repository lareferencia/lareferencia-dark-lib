package org.lareferencia.core.dark.contract;

import io.reactivex.Flowable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
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
public class PidService extends Contract {
    public static final String BINARY = "Bin file was not provided";

    public static final String FUNC_ADDEXTERNALPID = "addExternalPid";

    public static final String FUNC_ADD_ATTRIBUTE_PAYLOAD_SCHEMA = "add_attribute_payload_schema";

    public static final String FUNC_ASSINGID = "assingID";

    public static final String FUNC_BULK_ASSINGID = "bulk_assingID";

    public static final String FUNC_CREATE_PAYLOAD_SCHEMA = "create_payload_schema";

    public static final String FUNC_IS_A_DRAFT = "is_a_draft";

    public static final String FUNC_IS_A_VALID_PID = "is_a_valid_pid";

    public static final String FUNC_MARK_PAYLOAD_SCHEMA_READY = "mark_payload_schema_ready";

    public static final String FUNC_SET_AUTH_SERVICE = "set_auth_service";

    public static final String FUNC_SET_DB = "set_db";

    public static final String FUNC_SET_EXTERNALPID_SERVICE = "set_externalpid_service";

    public static final String FUNC_SET_PAYLOAD = "set_payload";

    public static final String FUNC_SET_PAYLOAD_TMP = "set_payload_tmp";

    public static final String FUNC_SET_URL = "set_url";

    public static final String FUNC_SET_URL_SERVICE = "set_url_service";

    public static final Event LOG_ID_EVENT = new Event("log_id", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    ;

    @Deprecated
    protected PidService(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected PidService(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected PidService(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected PidService(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<Log_idEventResponse> getLog_idEvents(TransactionReceipt transactionReceipt) {
        List<EventValuesWithLog> valueList = staticExtractEventParametersWithLog(LOG_ID_EVENT, transactionReceipt);
        ArrayList<Log_idEventResponse> responses = new ArrayList<Log_idEventResponse>(valueList.size());
        for (EventValuesWithLog eventValues : valueList) {
            Log_idEventResponse typedResponse = new Log_idEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.id = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static Log_idEventResponse getLog_idEventFromLog(Log log) {
        EventValuesWithLog eventValues = staticExtractEventParametersWithLog(LOG_ID_EVENT, log);
        Log_idEventResponse typedResponse = new Log_idEventResponse();
        typedResponse.log = log;
        typedResponse.id = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<Log_idEventResponse> log_idEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getLog_idEventFromLog(log));
    }

    public Flowable<Log_idEventResponse> log_idEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(LOG_ID_EVENT));
        return log_idEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> addExternalPid(byte[] pid_hash, BigInteger schema, String external_pid) {
        final Function function = new Function(
                FUNC_ADDEXTERNALPID, 
                Arrays.<Type>asList(new Bytes32(pid_hash),
                new org.web3j.abi.datatypes.generated.Uint8(schema), 
                new Utf8String(external_pid)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> add_attribute_payload_schema(String schema_name, String att_name) {
        final Function function = new Function(
                FUNC_ADD_ATTRIBUTE_PAYLOAD_SCHEMA, 
                Arrays.<Type>asList(new Utf8String(schema_name),
                new Utf8String(att_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> assingID(String sender) {
        final Function function = new Function(
                FUNC_ASSINGID, 
                Arrays.<Type>asList(new Address(160, sender)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> bulk_assingID(String sender) {
        final Function function = new Function(
                FUNC_BULK_ASSINGID, 
                Arrays.<Type>asList(new Address(160, sender)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> create_payload_schema(String schema_name) {
        final Function function = new Function(
                FUNC_CREATE_PAYLOAD_SCHEMA, 
                Arrays.<Type>asList(new Utf8String(schema_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> is_a_draft(PID p) {
        final Function function = new Function(FUNC_IS_A_DRAFT, 
                Arrays.<Type>asList(p), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<TransactionReceipt> mark_payload_schema_ready(String schema_name) {
        final Function function = new Function(
                FUNC_MARK_PAYLOAD_SCHEMA_READY, 
                Arrays.<Type>asList(new Utf8String(schema_name)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_auth_service(String addr) {
        final Function function = new Function(
                FUNC_SET_AUTH_SERVICE, 
                Arrays.<Type>asList(new Address(160, addr)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_db(String addr) {
        final Function function = new Function(
                FUNC_SET_DB, 
                Arrays.<Type>asList(new Address(160, addr)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_externalpid_service(String addr) {
        final Function function = new Function(
                FUNC_SET_EXTERNALPID_SERVICE, 
                Arrays.<Type>asList(new Address(160, addr)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_payload(byte[] pid_hash, String pid_payload_name, String pid_payload_value) {
        final Function function = new Function(
                FUNC_SET_PAYLOAD, 
                Arrays.<Type>asList(new Bytes32(pid_hash),
                new Utf8String(pid_payload_name),
                new Utf8String(pid_payload_value)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_payload_tmp(byte[] pid_hash, String schema_name, String pid_payload_name, String pid_payload_value) {
        final Function function = new Function(
                FUNC_SET_PAYLOAD_TMP, 
                Arrays.<Type>asList(new Bytes32(pid_hash),
                new Utf8String(schema_name),
                new Utf8String(pid_payload_name),
                new Utf8String(pid_payload_value)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_url(byte[] pid_hash, String url) {
        final Function function = new Function(
                FUNC_SET_URL, 
                Arrays.<Type>asList(new Bytes32(pid_hash),
                new Utf8String(url)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> set_url_service(String addr) {
        final Function function = new Function(
                FUNC_SET_URL_SERVICE, 
                Arrays.<Type>asList(new Address(160, addr)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static PidService load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new PidService(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static PidService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new PidService(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static PidService load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new PidService(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static PidService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new PidService(contractAddress, web3j, transactionManager, contractGasProvider);
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

    public static class Log_idEventResponse extends BaseEventResponse {
        public byte[] id;
    }
}
