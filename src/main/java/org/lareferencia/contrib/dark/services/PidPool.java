package org.lareferencia.contrib.dark.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.contract.DarkBlockChainService;
import org.lareferencia.contrib.dark.vo.DarkId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.annotation.ApplicationScope;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

@ApplicationScope
public class PidPool {

    private static final Logger LOG = LogManager.getLogger(PidPool.class);

    @Value("${pool.pid.min-number}")
    Integer minNumberOfPidsInPoool;

    @Value("${pool.milliseconds-to-wait-pids}")
    Integer milisecondsToWaidPids;

    ExecutorService executorService = Executors.newFixedThreadPool(1);

    @Autowired
    DarkBlockChainService dark;

    Map<String, Queue<DarkId>> pidsPerPrivateKey = new ConcurrentHashMap<>();


    public DarkId unstackDarkPid(String privateKey) {
        if(!pidsPerPrivateKey.containsKey(privateKey)) {
            pidsPerPrivateKey.put(privateKey, new ArrayBlockingQueue<>(10000, true));
        }
        executorService.execute(() -> {
            if(pidsPerPrivateKey.get(privateKey).size() <= minNumberOfPidsInPoool) {
                List<DarkId> pidsInBulkMode = dark.getPidsInBulkMode(privateKey);

                LOG.debug("Got new list of dArk Pids [{}]", pidsInBulkMode);
                pidsPerPrivateKey.get(privateKey).addAll(pidsInBulkMode);
            }
        });

        if(!pidsPerPrivateKey.containsKey(privateKey) || pidsPerPrivateKey.get(privateKey).isEmpty()) {
            LOG.warn("The pool of PIDs is empty, waiting for more PIDs");
            try {
                executorService.awaitTermination(milisecondsToWaidPids, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        DarkId darkPid = pidsPerPrivateKey.get(privateKey).poll();

        LOG.trace("Delivering the dark pid [{}]", darkPid.getPidHashAsString());
        return darkPid;
    }



}
