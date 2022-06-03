package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.UnidbgProperties;
import com.anjia.unidbgserver.web.MeiTuanForm;
import com.github.unidbg.worker.Worker;
import com.github.unidbg.worker.WorkerPool;
import com.github.unidbg.worker.WorkerPoolFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("meiTuanServiceWorker")
public class MeiTuanServiceWorker implements Worker {

    private UnidbgProperties unidbgProperties;
    private WorkerPool pool;
    private MeiTuanService meiTuanService;


    public MeiTuanServiceWorker() {

    }

    @Autowired
    public MeiTuanServiceWorker(UnidbgProperties unidbgProperties,
                           @Value("${spring.task.execution.pool.core-size:4}") int poolSize) {
        this.unidbgProperties = unidbgProperties;
        this.meiTuanService = new MeiTuanService(unidbgProperties);
        pool = WorkerPoolFactory.create(() ->
                        new MeiTuanServiceWorker(unidbgProperties.isDynarmic(), unidbgProperties.isVerbose()),
                Math.max(poolSize, 4));
        log.info("线程池为:{}", Math.max(poolSize, 4));
    }

    public MeiTuanServiceWorker(boolean dynarmic, boolean verbose) {
        this.unidbgProperties = new UnidbgProperties();
        unidbgProperties.setDynarmic(dynarmic);
        unidbgProperties.setVerbose(verbose);
        log.info("是否启用动态引擎:{},是否打印详细信息:{}", dynarmic, verbose);
        this.meiTuanService = new MeiTuanService(unidbgProperties);
    }

    @Async
    public CompletableFuture<Object> doWork(MeiTuanForm param) {
        MeiTuanServiceWorker worker;
        Object data;
        if (this.unidbgProperties.isAsync()) {
            while (true) {
                if ((worker = pool.borrow(2, TimeUnit.SECONDS)) == null) {
                    continue;
                }
                data = worker.exec(param);
                pool.release(worker);
                break;
            }
        } else {
            synchronized (this) {
                data = this.exec(param);
            }
        }
        return CompletableFuture.completedFuture(data);
    }

    @Override
    public void close() throws IOException {
        meiTuanService.destroy();
        log.debug("Destroy: {}", meiTuanService);
    }

    private String exec(MeiTuanForm param) {
        return meiTuanService.doWork(param);
    }

}
