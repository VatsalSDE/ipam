package com.motadata.ipam.scanner;


import com.motadata.ipam.core.config.AppConfig;

import io.vertx.core.Future;

import io.vertx.core.Vertx;

import io.vertx.core.WorkerExecutor;

import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import java.io.BufferedReader;

import java.io.File;

import java.io.InputStreamReader;

import java.util.ArrayList;

import java.util.List;

import java.util.concurrent.TimeUnit;

/**
 * GoPluginBridge executes Go plugins asynchronously on a dedicated WorkerExecutor pool.
 */
public class GoPluginBridge {

    private static final Logger logger = LoggerFactory.getLogger(GoPluginBridge.class);

    private final WorkerExecutor workerExecutor;

    private final String binaryPath;

    private static final java.util.concurrent.atomic.AtomicBoolean INITIALIZED_LOGGED = new java.util.concurrent.atomic.AtomicBoolean(false);

    public GoPluginBridge(Vertx vertx) {

        AppConfig config = AppConfig.getInstance();

        this.workerExecutor = vertx.createSharedWorkerExecutor("go-plugin-worker-pool", config.getPluginWorkerPoolSize());

        File bin = new File("../go-plugins/bin/ipam-engine");

        if (!bin.exists()) {

            bin = new File("go-plugins/bin/ipam-engine");

        }

        this.binaryPath = bin.getAbsolutePath();

        if (INITIALIZED_LOGGED.compareAndSet(false, true)) {

            logger.info("Initialized GoPluginBridge pointing to: {}", this.binaryPath);

        }

    }

    /**
     * Executes an ipam-engine subcommand asynchronously with JSON payload.
     *
     * @param command Subcommand: ping, snmp-scan, snmp-arp, windows-dhcp
     * @param payload Arguments or JSON string
     * @return Future containing the parsed JsonObject response from STDOUT
     */
    public Future<JsonObject> execute(String command, String... payload) {

        return workerExecutor.executeBlocking(() -> {

            List<String> commandList = new ArrayList<>();

            commandList.add(binaryPath);

            commandList.add(command);

            if (payload != null) {

                for (String p : payload) {

                    if (p != null && !p.isEmpty()) {

                        commandList.add(p);

                    }

                }

            }

            ProcessBuilder pb = new ProcessBuilder(commandList);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    output.append(line);

                }

            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished) {

                process.destroyForcibly();

                throw new RuntimeException("Go plugin command timed out: " + command);

            }

            String outStr = output.toString().trim();

            if (outStr.isEmpty()) {

                return new JsonObject();

            }

            return new JsonObject(outStr);

        });

    }

    public void close() {

        if (workerExecutor != null) {

            workerExecutor.close();

        }

    }

}
