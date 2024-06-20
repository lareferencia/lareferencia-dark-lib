package org.lareferencia.core.dark.contract;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DarkService {

    private static final Logger LOG = LogManager.getLogger(DarkService.class);
    public static final int DARKPID_POSITION = 1;
    public static final int EXPECTED_SIZE_OF_TOPICS = 3;


    @Getter
    @Setter
    @Value("${blockchain.url}")
    private String blockChainUrl;

    @Setter
    @Getter
    @Value("${blockchain.account-private-key}")
    private String privateKey;

    @Setter
    @Getter
    @Value("${blockchain.chain-id}")
    private Long chainId;

    @Setter
    @Getter
    @Value("${blockchain.contract-address.generate-pid}")
    private String addressOfNewPidContract;

    Web3j blockChainProxy;

    public void onStart() {
        blockChainProxy = Web3j.build(new HttpService(blockChainUrl));
    }

    public void onStop() {
        blockChainProxy.shutdown();
    }


    public List<DarkPidVo> getPidsInBulkMode() {

        try {

            Credentials credentials = Credentials.create(privateKey);
            String assignId = econdeFunction("bulk_assingID", Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, credentials.getAddress())), Collections.emptyList());

            RawTransaction transaction = createRawTransaction(credentials, assignId);
            String signedMessage = signTransaction(transaction, credentials);

            EthSendTransaction bulkAssignTransaction = blockChainProxy.ethSendRawTransaction(signedMessage).send();

            TransactionReceipt receipt = sendTransactionAndWaitForReceipt(bulkAssignTransaction);

            return receipt.getLogs().stream()
                    .filter(log -> log.getTopics() != null && log.getTopics().size() == EXPECTED_SIZE_OF_TOPICS)
                    .map(log -> log.getTopics().get(DARKPID_POSITION))
                    .map(darkPid -> new DarkPidVo(darkPid)).collect(Collectors.toList());

        } catch (ExecutionException | InterruptedException | IOException | TransactionException e) {
            LOG.error(e);
            // TODO: CHECK WITH LAUTARO WHICH EXCEPTION TO THROW
            throw new RuntimeException(e);
        }
    }

    public void associateDarkPidWithUrl(String darkId, String url) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            String assignId = econdeFunction("set_url",
                    Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(darkId.getBytes()),
                            new org.web3j.abi.datatypes.Utf8String(url)),
                    Collections.<TypeReference<?>>emptyList());

            RawTransaction transaction = createRawTransaction(credentials, assignId);
            String signedMessage = signTransaction(transaction, credentials);

            EthSendTransaction bulkAssignTransaction = blockChainProxy.ethSendRawTransaction(signedMessage).send();

            TransactionReceipt receipt = sendTransactionAndWaitForReceipt(bulkAssignTransaction);

            LOG.info("Receipt recieved [{}]", receipt.getTransactionHash());

        } catch (ExecutionException | InterruptedException | IOException | TransactionException e) {
            throw new RuntimeException(e);
        }

    }


    private TransactionReceipt sendTransactionAndWaitForReceipt(EthSendTransaction bulkAssignTransaction) throws IOException, TransactionException {
        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
                blockChainProxy,
                3 * 1000,
                TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);

        TransactionReceipt receipt = receiptProcessor
                .waitForTransactionReceipt(bulkAssignTransaction.getTransactionHash());
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
