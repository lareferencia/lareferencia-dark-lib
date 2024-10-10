package org.lareferencia.contrib.dark.contract;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.vo.DarkId;
import org.lareferencia.core.worker.WorkerRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DarkBlockChainService {

    private static final Logger LOG = LogManager.getLogger(DarkBlockChainService.class);
    public static final int DARKPID_POSITION = 1;
    public static final int EXPECTED_SIZE_OF_TOPICS = 3;
    public static final int FIRST_BYTE_OF_ARK = 256;
    public static final int LAST_BYTE_OF_ARK = 275;
    public static final String SUCCESS_STATUS = "0x1";


    @Getter
    @Setter
    @Value("${blockchain.url}")
    private String blockChainUrl;

    @Setter
    @Getter
    @Value("${blockchain.chain-id}")
    private Long chainId;

    @Value("${blockchain.generate-pid.sleeptime-milliseconds}")
    private Long sleepTime;

    @Setter
    @Getter
    @Value("${blockchain.contract-address.generate-pid}")
    private String addressOfNewPidContract;

    @Setter
    @Getter
    @Value("${blockchain.contract-address.format-pid}")
    private String addressOfFormatPid;


    Web3j blockChainProxy;

    public void onStart() {
        blockChainProxy = Web3j.build(new HttpService(blockChainUrl));
    }

    public void onStop() {
        blockChainProxy.shutdown();
    }


    public List<DarkId> getPidsInBulkMode(String privateKey) {

        try {
            Thread.sleep(sleepTime);

            Credentials credentials = Credentials.create(privateKey);
            String assignId = econdeFunction("bulk_assingID", Arrays.asList(new org.web3j.abi.datatypes.Address(160, credentials.getAddress())), Collections.emptyList());

            RawTransaction transaction = createRawTransaction(credentials, assignId);
            String signedMessage = signTransaction(transaction, credentials);

            EthSendTransaction bulkAssignTransaction = blockChainProxy.ethSendRawTransaction(signedMessage).send();

            TransactionReceipt receipt = waitAndGetReceipt(bulkAssignTransaction);
            return receipt.getLogs().stream()
                    .filter(log -> log.getTopics() != null && log.getTopics().size() == EXPECTED_SIZE_OF_TOPICS)
                    .map(log -> log.getTopics().get(DARKPID_POSITION))
                    .map(darkPid ->
                            new DarkId(
                                    darkPid,
                                    Numeric.hexStringToByteArray(darkPid),
                                    formatPid(Numeric.hexStringToByteArray(darkPid), privateKey)))
                    .collect(Collectors.toList());

        } catch (ExecutionException | InterruptedException | IOException | TransactionException e) {
            LOG.error(e);
            // TODO: CHECK WITH LAUTARO WHICH EXCEPTION TO THROW
            throw new RuntimeException(e);
        }
    }


    public String formatPid(byte[] pidHash, String privateKey) {

        try {

            Credentials credentials = Credentials.create(privateKey);
            PidDB pidDB = new PidDB(addressOfFormatPid, blockChainProxy,
                    new RawTransactionManager(blockChainProxy, credentials), new DefaultGasProvider());

            String formattedPidResponse = pidDB.get(pidHash).send();
            byte[] responseConvertedToBytes = Numeric.hexStringToByteArray(formattedPidResponse);

            return new StringBuilder("ark:/")
                    .append(new String(Arrays.copyOfRange(responseConvertedToBytes, FIRST_BYTE_OF_ARK, LAST_BYTE_OF_ARK), StandardCharsets.UTF_8)).toString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void associateDarkPidWithUrl(byte[] pid_hash, String url, String privateKey) throws WorkerRuntimeException {
        try {
            Credentials credentials = Credentials.create(privateKey);
            Bytes32 bytes32 = new Bytes32(pid_hash);
            String assignId = econdeFunction("set_url",
                    Arrays.<Type>asList(bytes32,
                            new org.web3j.abi.datatypes.Utf8String(url)),
                    Collections.<TypeReference<?>>emptyList());

            RawTransaction transaction = createRawTransaction(credentials, assignId);
            String signedMessage = signTransaction(transaction, credentials);

            EthSendTransaction sentTransaction = blockChainProxy.ethSendRawTransaction(signedMessage).send();
            TransactionReceipt receipt = waitAndGetReceipt(sentTransaction);
            LOG.debug("The set_url method for the dARK Hash [{}] returned the following: [{}]", Numeric.toHexString(pid_hash), receipt.toString());

            if(SUCCESS_STATUS.equals(receipt.getStatus())) {
                LOG.debug("The set_url method for the dARK Hash [{}] was successful", Numeric.toHexString(pid_hash));

            } else {
                LOG.error("The set_url method for the dARK Hash [{}] ended with error, receipt: [{}]", Numeric.toHexString(pid_hash), receipt.toString());
                // TODO: THROW ERROR, CANT KILL THE THREAD
            }

        } catch (ExecutionException | InterruptedException | IOException | TransactionException e) {
            throw new WorkerRuntimeException(e.getMessage());
        }

    }


    private TransactionReceipt waitAndGetReceipt(EthSendTransaction transaction) throws IOException, TransactionException {
        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
                blockChainProxy,
                5 * 1000,
                TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);

        TransactionReceipt receipt = receiptProcessor
                .waitForTransactionReceipt(transaction.getTransactionHash());
        return receipt;
    }

    private static String econdeFunction(String functionName, List<Type> inputs, List<TypeReference<?>> outputs) {
        Function function = new Function(functionName, inputs, outputs);
        return FunctionEncoder.encode(function);
    }

    private RawTransaction createRawTransaction(Credentials credentials, String contractFuncion) throws InterruptedException, ExecutionException, IOException {
        BigInteger nounce = getNounce(credentials);
        BigInteger gasLimit = getCurrentBlock().getGasLimit();
        RawTransaction transaction = RawTransaction.createTransaction(
                nounce,
                BigInteger.valueOf(100),
                gasLimit,
                addressOfNewPidContract,
                BigInteger.ZERO,
                contractFuncion
        );
        return transaction;
    }

    private String signTransaction(RawTransaction transaction, Credentials credentials) {
        return Numeric.toHexString(TransactionEncoder.signMessage(transaction, chainId, credentials));
    }

    private BigInteger getNounce(Credentials credentials) throws InterruptedException, ExecutionException {
        return blockChainProxy.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .sendAsync().get().getTransactionCount();
    }

    private EthBlock.Block getCurrentBlock() throws IOException {
        return blockChainProxy.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
    }


}
